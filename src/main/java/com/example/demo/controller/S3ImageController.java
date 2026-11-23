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

    // 미리보기와 ZIP 다운로드에서 API 응답으로 원본 파일을 읽을 수 있게 한다.
    private ResponseEntity<InputStreamResource> streamFromS3(HttpServletRequest request, boolean download) {
        // 인코딩된 요청 경로를 서비스에 전달해 파일명 속 % 문자를 보존한다.
        String requestPath = request.getRequestURI();
        int imageIndex = requestPath.indexOf("/image/");
        if (imageIndex < 0) return ResponseEntity.notFound().build();
        String storedPath = requestPath.substring(imageIndex);
        return fileStorageService.streamRequestObject(storedPath, download);
    }
}
