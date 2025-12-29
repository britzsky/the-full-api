package com.example.demo.service;

import java.io.File;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.example.demo.classify.CardReceiptClassifier;
import com.example.demo.model.CardReceiptResponse;
import com.example.demo.model.CardReceiptType;
import com.example.demo.parser.BaseReceiptParser;
import com.example.demo.parser.CardReceiptParserFactory;
import com.google.cloud.documentai.v1.Document;

@Service
public class CardReceiptParseService {

    private final OcrService ocrService;
    private final CardReceiptParserFactory factory;
    private final CardReceiptClassifier classifier; // 네가 만든 분류기(패키지 위치는 프로젝트에 맞게)

    public CardReceiptParseService(OcrService ocrService, CardReceiptParserFactory factory) {
        this.ocrService = ocrService;
        this.factory = factory;
        this.classifier = new CardReceiptClassifier();
    }

    // ✅ 기존: 자동 분류
    public CardReceiptResponse parse(MultipartFile file) throws Exception {
        return parse(file, null);
    }
    
    public CardReceiptResponse parseFile(File file, String typeOverride) throws Exception {
        Document doc = ocrService.processDocumentFile(file);

        CardReceiptType type;
        double conf;

        CardReceiptType forced = toTypeOrNull(typeOverride);
        if (forced != null) {
            type = forced;
            conf = 1.0;
        } else {
            CardReceiptClassifier.Classified c = classifier.classify(doc);
            type = c.type;
            conf = c.confidence;
        }

        BaseReceiptParser parser = factory.get(type);
        BaseReceiptParser.ReceiptResult result = parser.parse(doc);

        return new CardReceiptResponse(type, conf, result);
    }
    
    // ✅ 신규: type이 오면 강제(없으면 자동 분류)
    public CardReceiptResponse parse(MultipartFile file, String typeOverride) throws Exception {
        Document doc = ocrService.processDocument(file);

        CardReceiptType type;
        double conf;

        CardReceiptType forced = toTypeOrNull(typeOverride);
        if (forced != null) {
            type = forced;
            conf = 1.0;
        } else {
            CardReceiptClassifier.Classified c = classifier.classify(doc);
            type = c.type;
            conf = c.confidence;
        }

        BaseReceiptParser parser = factory.get(type);
        BaseReceiptParser.ReceiptResult result = parser.parse(doc);

        return new CardReceiptResponse(type, conf, result);
    }

    // type 파라미터 유연 처리 (원하는 값에 맞게 케이스 추가 가능)
    private CardReceiptType toTypeOrNull(String type) {
        if (type == null || type.isBlank()) return null;
        String t = type.trim().toUpperCase();

        return switch (t) {
            case "CONVENIENCE", "CVS", "편의점" -> CardReceiptType.CONVENIENCE;
            case "COUPANG_APP" -> CardReceiptType.COUPANG_APP;
            case "COUPANG_CARD", "COUPANG" -> CardReceiptType.COUPANG_CARD;
            case "MART", "MART_ITEMIZED", "마트" -> CardReceiptType.MART_ITEMIZED;
            case "SLIP", "CARD_SLIP_GENERIC", "전표" -> CardReceiptType.CARD_SLIP_GENERIC;
            case "UNKNOWN" -> CardReceiptType.UNKNOWN;
            default -> null;
        };
    }
}
