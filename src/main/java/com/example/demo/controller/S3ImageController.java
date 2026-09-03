package com.example.demo.controller;

import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.demo.service.S3FileStorageService;

import jakarta.servlet.http.HttpServletRequest;

@RestController
public class S3ImageController {

    private final S3FileStorageService fileStorageService;

    public S3ImageController(S3FileStorageService fileStorageService) {
        this.fileStorageService = fileStorageService;
    }

    @GetMapping("/image/**")
    public ResponseEntity<InputStreamResource> viewFromS3(HttpServletRequest request) {
        return streamFromS3(request, false);
    }

    @GetMapping("/download/image/**")
    public ResponseEntity<InputStreamResource> downloadFromS3(HttpServletRequest request) {
        return streamFromS3(request, true);
    }

    // 302 리다이렉트 대신 서버가 S3 오브젝트를 직접 받아 그대로 스트리밍한다.
    // (JS fetch()로 바이트를 읽어야 하는 zip 일괄 다운로드/미리보기 blob 로드가
    //  S3 리다이렉트 이후 CORS에 막히는 문제를 근본적으로 없애기 위함 — S3FileStorageService.streamObject 참고)
    private ResponseEntity<InputStreamResource> streamFromS3(HttpServletRequest request, boolean download) {
        String requestPath = request.getRequestURI();
        int imageIndex = requestPath.indexOf("/image/");
        if (imageIndex < 0) return ResponseEntity.notFound().build();
        String storedPath = requestPath.substring(imageIndex);
        return fileStorageService.streamObject(storedPath, download);
    }
}
