package com.example.demo.service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import com.google.cloud.documentai.v1.Document;

@Service
public class AiReceiptAnalyzer {

    private final Map<String, List<String>> keywordMap = new HashMap<>();

    public AiReceiptAnalyzer() {
        loadKeywords();
    }

    /**
     * 영수증 텍스트에서 타입 자동 감지
     */
    public String detectType(Document doc) {
        if (doc == null || doc.getText() == null) return "unknown";
        String text = doc.getText().toLowerCase();

        // 🛒 마트
        if (containsAny(text, keywordMap.get("mart"))) {
            if (!containsAny(text, keywordMap.get("convenience")) &&
                !containsAny(text, keywordMap.get("delivery"))) {
                return "mart";
            }
        }

        // 🏪 편의점
        if (containsAny(text, keywordMap.get("convenience"))) {
            return "convenience";
        }

        // 📦 쿠팡
        if (containsAny(text, keywordMap.get("coupang"))) {
            return "coupang";
        }

        // 🍱 배달앱
        if (containsAny(text, keywordMap.get("delivery"))) {
            return "delivery";
        }

        return "unknown";
    }

    /**
     * 키워드 파일들을 로드
     */
    private void loadKeywords() {
//        keywordMap.put("mart", loadFile("keywords/mart_keywords.txt"));
//        keywordMap.put("convenience", loadFile("keywords/convenience_keywords.txt"));
//        keywordMap.put("coupang", loadFile("keywords/coupang_keywords.txt"));
//        keywordMap.put("delivery", loadFile("keywords/delivery_keywords.txt"));

        System.out.println("✅ AIReceiptAnalyzer 키워드 로드 완료");
    }

    /**
     * 파일에서 키워드 목록 로드
     */
    private List<String> loadFile(String path) {
        try {
            ClassPathResource resource = new ClassPathResource(path);
            return new BufferedReader(new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))
                    .lines()
                    .map(String::trim)
                    .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                    .collect(Collectors.toList());
        } catch (Exception e) {
            System.err.println("⚠️ 키워드 파일 로드 실패: " + path + " (" + e.getMessage() + ")");
            return Collections.emptyList();
        }
    }

    /**
     * 텍스트에 키워드 포함 여부 검사
     */
    private boolean containsAny(String text, List<String> keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword.toLowerCase())) return true;
        }
        return false;
    }
}
