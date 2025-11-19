package com.example.demo.service;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

import javax.annotation.PreDestroy;

import org.opencv.core.Mat;
import org.opencv.core.MatOfInt;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.google.cloud.documentai.v1.Document;
import com.google.cloud.documentai.v1.DocumentProcessorServiceClient;
import com.google.cloud.documentai.v1.ProcessRequest;
import com.google.cloud.documentai.v1.ProcessResponse;
import com.google.cloud.documentai.v1.RawDocument;
import com.google.protobuf.ByteString;

@Service
public class OcrService {

    static {
        try {
            nu.pattern.OpenCV.loadLocally();
            System.out.println("✅ OpenCV 로드 완료 (OcrService)");
        } catch (Exception e) {
            System.err.println("⚠️ OpenCV 로드 실패: " + e.getMessage());
        }
    }

    @Value("${documentai.project.id:the-full-ocr-project}")
    private String projectId;

    @Value("${documentai.location:us}")
    private String location;

    // 💰 [변경] Expense Parser 대신 일반 OCR 프로세서 ID를 사용합니다.
    // 이는 페이지당 요금($1.50/1000페이지)으로 청구되어 비용 절감이 가능합니다.
    @Value("${documentai.processor.id:88284f2a23409f90}")
    private String processorId;

    private final DocumentProcessorServiceClient docAiClient;

    @Autowired
    public OcrService(DocumentProcessorServiceClient docAiClient) {
        this.docAiClient = docAiClient;
        System.out.println("✅ Google Document AI Client 주입 성공!");
    }

    // ===============================
    // 메인 OCR 처리 (Expense Parser 기능 제거)
    // ===============================
    /**
     * Expense Parser (고가) 대신 일반 OCR 프로세서를 사용하여 문서를 처리합니다.
     * 추출된 데이터는 후처리 로직(별도 구현 필요)으로 처리해야 합니다.
     */
    public Document processDocument(MultipartFile file) throws IOException {
        File tempFile = File.createTempFile("docai_input_", ".jpg");
        file.transferTo(tempFile);
        return processDocumentFile(tempFile);
    }

    public Document processDocumentFile(File file) throws IOException {
        // 1️⃣ 이미지 크기/용량 최적화 (유지)
        // OCR 처리량을 늘리고 전송 시간을 줄이는 데 도움이 됩니다.
        File optimized = autoOptimizeImage(file);

        // 2️⃣ Google Document AI 요청 생성
        // 이 요청은 이제 구조화된 데이터 추출(Expense Parser)이 아닌
        // 문서 내 모든 텍스트를 인식하는 (일반 OCR) 기능을 수행합니다.
        String name = String.format("projects/%s/locations/%s/processors/%s",
                projectId, location, processorId);

        ByteString content = ByteString.readFrom(new FileInputStream(optimized));
        RawDocument rawDocument = RawDocument.newBuilder()
                .setContent(content)
                // 이미지 파일을 보내므로 image/jpeg MimeType을 사용합니다.
                .setMimeType("image/jpeg") 
                .build();

        ProcessRequest request = ProcessRequest.newBuilder()
                .setName(name)
                .setRawDocument(rawDocument)
                .build();

        // 3️⃣ Document AI 호출
        // 반환되는 Document 객체는 텍스트(document.getText())와 레이아웃 정보만 포함합니다.
        ProcessResponse response = docAiClient.processDocument(request);
        return response.getDocument();
        
        // Expense Parser를 사용했다면 여기서 
        // document.getEntitiesList() 등을 사용하여 구조화된 영수증 항목을 추출합니다.
        // 현재는 이 엔티티 추출 로직이 비활성화되었으므로, 별도의 후처리 로직을 추가해야 합니다.
    }

    // ===============================
    // 이미지 자동 최적화 (유지)
    // ===============================
    private File autoOptimizeImage(File file) throws IOException {
        // ... (내용 변화 없음 - OpenCV 로직)
        Mat src = Imgcodecs.imread(file.getAbsolutePath(), Imgcodecs.IMREAD_UNCHANGED);
        if (src.empty()) {
            System.err.println("⚠️ 이미지 로드 실패: " + file.getName());
            return file;
        }

        // PNG → BGR 변환 (투명 채널 제거)
        if (src.channels() == 4) {
            Imgproc.cvtColor(src, src, Imgproc.COLOR_BGRA2BGR);
        }

        int width = src.width();
        int height = src.height();
        long beforeSize = file.length();

        System.out.printf("🖼️ 원본 이미지: %dx%d (%.2f MB)%n", width, height, beforeSize / 1024.0 / 1024.0);

        // 📏 1단계: 해상도 1800px 이하로 강제 축소
        if (width > 1800 || height > 1800) {
            file = resizeImage(file, 1800, 1800);
        }

        // 📦 2단계: 5MB 이하로 자동 축소 루프
        File optimized = file;
        int pass = 1;
        while (optimized.length() > 5_000_000) {
            System.out.printf("⚠️ [%d차] 용량 초과 (%.2f MB) → 추가 축소 중...%n",
                    pass++, optimized.length() / 1024.0 / 1024.0);
            optimized = resizeImage(optimized, 1600, 1600);
        }

        System.out.printf("✅ 최종 이미지: %.2f MB%n", optimized.length() / 1024.0 / 1024.0);
        return optimized;
    }

    // ===============================
    // 이미지 리사이즈 + 압축 (유지)
    // ===============================
    public File resizeImage(File inputFile, int maxWidth, int maxHeight) throws IOException {
        // ... (내용 변화 없음 - OpenCV 로직)
        Mat src = Imgcodecs.imread(inputFile.getAbsolutePath(), Imgcodecs.IMREAD_COLOR);
        if (src.empty()) return inputFile;

        int width = src.width();
        int height = src.height();

        double scale = Math.min((double) maxWidth / width, (double) maxHeight / height);
        if (scale >= 1.0) return inputFile; // 줄일 필요 없음

        int newW = (int) (width * scale);
        int newH = (int) (height * scale);

        Mat resized = new Mat();
        Imgproc.resize(src, resized, new org.opencv.core.Size(newW, newH));

        File resizedFile = File.createTempFile("resized_", ".jpg");
        Imgcodecs.imwrite(resizedFile.getAbsolutePath(), resized,
                new MatOfInt(Imgcodecs.IMWRITE_JPEG_QUALITY, 70)); // ✅ 압축률 70 적용

        System.out.printf("📏 리사이즈 완료 → %dx%d (%.2f MB)%n",
                newW, newH, resizedFile.length() / 1024.0 / 1024.0);
        return resizedFile;
    }

    // ===============================
    // 클라이언트 및 임시파일 정리 (유지)
    // ===============================
    @PreDestroy
    public void closeClients() {
        if (docAiClient != null) {
            docAiClient.close();
            System.out.println("🧹 Document AI 클라이언트 종료 완료");
        }

        // 임시 파일 정리
        File tmpDir = new File(System.getProperty("java.io.tmpdir"));
        File[] files = tmpDir.listFiles((dir, name) ->
                name.startsWith("resized_") || name.startsWith("docai_input_"));
        if (files != null) {
            for (File f : files) f.delete();
            System.out.println("🧽 임시 파일 정리 완료");
        }
    }
}