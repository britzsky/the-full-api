package com.example.demo.service;

import java.io.IOException;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

@Service
public class S3FileStorageService {

    private static final String LEGACY_PREFIX = "/image/";

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final String bucket;
    private final String keyPrefix;
    private final Duration presignedUrlDuration;

    public S3FileStorageService(
            S3Client s3Client,
            S3Presigner s3Presigner,
            @Value("${aws.s3.bucket}") String bucket,
            @Value("${aws.s3.key-prefix:}") String keyPrefix,
            @Value("${aws.s3.presigned-url-duration:10m}") Duration presignedUrlDuration) {
        this.s3Client = s3Client;
        this.s3Presigner = s3Presigner;
        this.bucket = bucket;
        this.keyPrefix = normalizePrefix(keyPrefix);
        this.presignedUrlDuration = presignedUrlDuration;
    }

    public String upload(MultipartFile file, String... pathSegments) throws IOException {
        requireConfiguredBucket();
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("업로드할 파일이 없습니다.");
        }

        String prefix = Arrays.stream(pathSegments)
                .map(this::sanitizeSegment)
                .filter(segment -> !segment.isEmpty())
                .collect(Collectors.joining("/"));
        String originalFilename = sanitizeFilename(file.getOriginalFilename());
        String objectKey = (prefix.isEmpty() ? "" : prefix + "/")
                + UUID.randomUUID() + "_" + originalFilename;
        String physicalKey = physicalKey(objectKey);

        PutObjectRequest.Builder request = PutObjectRequest.builder()
                .bucket(bucket)
                .key(physicalKey);
        if (file.getContentType() != null && !file.getContentType().isBlank()) {
            request.contentType(file.getContentType());
        }

        try (var inputStream = file.getInputStream()) {
            s3Client.putObject(request.build(), RequestBody.fromInputStream(inputStream, file.getSize()));
        }
        return LEGACY_PREFIX + objectKey;
    }

    public URL createPresignedGetUrl(String storedPath) {
        return createPresignedGetUrl(storedPath, false);
    }

    public URL createPresignedDownloadUrl(String storedPath) {
        return createPresignedGetUrl(storedPath, true);
    }

    private URL createPresignedGetUrl(String storedPath, boolean download) {
        requireConfiguredBucket();
        String objectKey = toObjectKey(storedPath);
        GetObjectRequest.Builder getRequest = GetObjectRequest.builder()
                .bucket(bucket)
                .key(physicalKey(objectKey));
        if (download) {
            String filename = objectKey.substring(objectKey.lastIndexOf('/') + 1)
                    .replace("\r", "")
                    .replace("\n", "");
            String encodedFilename = URLEncoder.encode(filename, StandardCharsets.UTF_8)
                    .replace("+", "%20");
            getRequest.responseContentDisposition("attachment; filename*=UTF-8''" + encodedFilename);
        }
        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(presignedUrlDuration)
                .getObjectRequest(getRequest.build())
                .build();
        return s3Presigner.presignGetObject(presignRequest).url();
    }

    public StoredObjectMetadata metadata(String storedPath) {
        requireConfiguredBucket();
        String objectKey = toObjectKey(storedPath);
        HeadObjectResponse response = s3Client.headObject(HeadObjectRequest.builder()
                .bucket(bucket)
                .key(physicalKey(objectKey))
                .build());
        String filename = objectKey.substring(objectKey.lastIndexOf('/') + 1);
        return new StoredObjectMetadata(filename, response.contentType(), response.contentLength());
    }

    public boolean exists(String storedPath) {
        try {
            metadata(storedPath);
            return true;
        } catch (NoSuchKeyException e) {
            return false;
        } catch (S3Exception e) {
            if (e.statusCode() == 404) return false;
            throw e;
        }
    }

    public void delete(String storedPath) {
        if (storedPath == null || storedPath.isBlank()) return;
        requireConfiguredBucket();
        s3Client.deleteObject(DeleteObjectRequest.builder()
                .bucket(bucket)
                .key(physicalKey(toObjectKey(storedPath)))
                .build());
    }

    public String toObjectKey(String storedPath) {
        if (storedPath == null || storedPath.isBlank()) {
            throw new IllegalArgumentException("파일 경로가 비어 있습니다.");
        }
        String normalized = decodeRepeatedly(storedPath.trim()).replace('\\', '/');
        int queryIndex = normalized.indexOf('?');
        if (queryIndex >= 0) normalized = normalized.substring(0, queryIndex);
        int imageIndex = normalized.indexOf(LEGACY_PREFIX);
        if (imageIndex >= 0) normalized = normalized.substring(imageIndex + LEGACY_PREFIX.length());
        else if (normalized.startsWith("image/")) normalized = normalized.substring("image/".length());
        while (normalized.startsWith("/")) normalized = normalized.substring(1);
        if (normalized.isBlank() || normalized.contains("../") || normalized.equals("..")) {
            throw new IllegalArgumentException("허용되지 않는 파일 경로입니다.");
        }
        return normalized;
    }

    private String sanitizeSegment(String value) {
        if (value == null) return "";
        return value.trim().replace("/", "_").replace("\\", "_").replace("..", "_");
    }

    private String physicalKey(String objectKey) {
        return keyPrefix.isEmpty() ? objectKey : keyPrefix + "/" + objectKey;
    }

    private String normalizePrefix(String value) {
        if (value == null) return "";
        String normalized = value.trim().replace('\\', '/');
        while (normalized.startsWith("/")) normalized = normalized.substring(1);
        while (normalized.endsWith("/")) normalized = normalized.substring(0, normalized.length() - 1);
        if (normalized.contains("..")) {
            throw new IllegalArgumentException("aws.s3.key-prefix에 '..'을 사용할 수 없습니다.");
        }
        return normalized;
    }

    private String sanitizeFilename(String value) {
        String filename = value == null ? "file" : value.replace('\\', '/');
        filename = filename.substring(filename.lastIndexOf('/') + 1).trim().replace("..", "_");
        return filename.isEmpty() ? "file" : filename;
    }

    private String decodeRepeatedly(String value) {
        String decoded = value;
        for (int i = 0; i < 3; i++) {
            String next = URLDecoder.decode(decoded, StandardCharsets.UTF_8);
            if (next.equals(decoded)) break;
            decoded = next;
        }
        return decoded;
    }

    private void requireConfiguredBucket() {
        if (bucket == null || bucket.isBlank()) {
            throw new IllegalStateException("AWS_S3_BUCKET 환경변수 또는 aws.s3.bucket 설정이 필요합니다.");
        }
    }

    public record StoredObjectMetadata(String filename, String contentType, long contentLength) {}
}
