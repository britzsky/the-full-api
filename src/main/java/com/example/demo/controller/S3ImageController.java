package com.example.demo.controller;

import java.net.URI;

import org.springframework.http.HttpHeaders;
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
    public ResponseEntity<Void> redirectToS3(HttpServletRequest request) {
        String requestPath = request.getRequestURI();
        int imageIndex = requestPath.indexOf("/image/");
        if (imageIndex < 0) return ResponseEntity.notFound().build();
        String storedPath = requestPath.substring(imageIndex);
        if (!fileStorageService.exists(storedPath)) return ResponseEntity.notFound().build();
        URI location = URI.create(fileStorageService.createPresignedGetUrl(storedPath).toString());
        return ResponseEntity.status(302)
                .header(HttpHeaders.LOCATION, location.toString())
                .build();
    }
}
