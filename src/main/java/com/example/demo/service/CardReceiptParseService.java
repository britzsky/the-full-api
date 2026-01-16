package com.example.demo.service;

import java.io.File;

import org.springframework.stereotype.Service;

import com.example.demo.model.CardReceiptType;
import com.example.demo.model.CardReceiptResponse;
import com.example.demo.parser.BaseReceiptParser;
import com.example.demo.parser.CardReceiptParserFactory;
import com.google.cloud.documentai.v1.Document;

@Service
public class CardReceiptParseService {

    private final OcrService ocrService;
    private final CardReceiptParserFactory factory;

    public CardReceiptParseService(OcrService ocrService, CardReceiptParserFactory factory) {
        this.ocrService = ocrService;
        this.factory = factory;
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
            // ✅ 간단 자동감지(운영중 classifier가 있으면 여기만 교체하면 됨)
            Classified c = classifyByHeuristic(doc);
            type = c.type;
            conf = c.confidence;
        }

        BaseReceiptParser parser = factory.get(type);
        BaseReceiptParser.ReceiptResult result = parser.parse(doc);

        return new CardReceiptResponse(type, conf, result);
    }

    // --------------------------------------------
    // type 파라미터 유연 처리 (문자열 들어오면 매핑)
    // --------------------------------------------
    private CardReceiptType toTypeOrNull(String type) {
        if (type == null || type.isBlank()) return null;
        String t = type.trim().toUpperCase();

        return switch (t) {
            case "CONVENIENCE", "CVS", "편의점" -> CardReceiptType.CONVENIENCE;
            case "COUPANG_APP", "쿠팡앱", "쿠페이" -> CardReceiptType.COUPANG_APP;
            case "COUPANG_CARD", "COUPANG", "쿠팡", "쿠팡카드" -> CardReceiptType.COUPANG_CARD;
            case "MART", "MART_ITEMIZED", "마트" -> CardReceiptType.MART_ITEMIZED;
            case "DELIVERY", "배달", "BAEMIN", "YOGIYO", "COUPANG_EATS" -> CardReceiptType.DELIVERY;
            case "SLIP", "CARD_SLIP_GENERIC", "전표", "GENERIC" -> CardReceiptType.CARD_SLIP_GENERIC;
            case "UNKNOWN" -> CardReceiptType.UNKNOWN;
            default -> null;
        };
    }

    // --------------------------------------------
    // 간단 자동 분류(필요하면 너 기존 classifier로 대체)
    // --------------------------------------------
    private Classified classifyByHeuristic(Document doc) {
        String text = (doc == null) ? "" : doc.getText();
        String t = text == null ? "" : text.toUpperCase();

        // 편의점
        if (t.contains("GS25") || t.contains("세븐일레븐") || t.contains("7-ELEVEN") || t.contains(" CU ")
                || t.contains("\nCU") || t.contains("CU\n")) {
            return new Classified(CardReceiptType.CONVENIENCE, 0.85);
        }

        // 쿠팡
        if (t.contains("쿠팡") || t.contains("COUPANG") || t.contains("쿠페이") || t.contains("COUPAY")) {
            // 앱 결제내역 힌트
            if (t.contains("거래메모") && t.contains("쿠팡(쿠페이)") && !t.contains("카드영수증")) {
                return new Classified(CardReceiptType.COUPANG_APP, 0.85);
            }
            return new Classified(CardReceiptType.COUPANG_CARD, 0.75);
        }

        // 배달
        if (t.contains("배달의민족") || t.contains("배민") || t.contains("요기요") || t.contains("쿠팡이츠")
                || t.contains("주문메뉴") || t.contains("라이더님께")) {
            return new Classified(CardReceiptType.DELIVERY, 0.8);
        }

        // 마트
        if (t.contains("NO.") && t.contains("상품명") && (t.contains("단가") || t.contains("수량") || t.contains("금액"))) {
            return new Classified(CardReceiptType.MART_ITEMIZED, 0.75);
        }

        // 기본 전표
        if (t.contains("승인번호") || t.contains("가맹점") || t.contains("TID") || t.contains("VAN")) {
            return new Classified(CardReceiptType.CARD_SLIP_GENERIC, 0.6);
        }

        return new Classified(CardReceiptType.UNKNOWN, 0.3);
    }

    private static class Classified {
        final CardReceiptType type;
        final double confidence;
        Classified(CardReceiptType type, double confidence) {
            this.type = type;
            this.confidence = confidence;
        }
    }
}
