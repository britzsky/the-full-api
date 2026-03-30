package com.example.demo.parser;

import com.google.cloud.documentai.v1.Document;

import java.util.regex.Pattern;

public class HeadOfficeReceiptParserFactory {

    public static BaseReceiptParser.ReceiptResult parse(Document doc, String type) {
        String normalizedType = normalizeType(type);
        BaseReceiptParser parser;

        switch (normalizedType) {
            case "11post":
                parser = new HeadOffice11PostReceiptParser();
                break;
            case "coupang":
                parser = new HeadOfficeCoupangReceiptParser();
                break;
            case "auction":
                parser = new HeadOfficeAuctionReceiptParser();
                break;
            case "gmarket":
                parser = new HeadOfficeGMarketReceiptParser();
                break;
            case "homeplus":
                parser = new HeadOfficeHomePlusReceiptParser();
                break;
            case "naver":
                parser = new HeadOfficeNaverReceiptParser();
                break;
            case "daiso":
                parser = new HeadOfficeDaisoReceiptParser();
                break;
            case "mart_itemized":
                parser = new MartReceiptParser();
                break;
            case "convenience":
                parser = new ConvenienceReceiptParser();
                break;
            default:
                if (isAuctionSlip(doc)) {
                    parser = new HeadOfficeAuctionReceiptParser();
                    break;
                }
                throw new IllegalArgumentException("지원하지 않는 영수증 타입: " + type);
        }

        return parser.parse(doc);
    }

    private static String normalizeType(String type) {
        if (type == null)
            return "";
        String t = type.trim().toLowerCase();
        if (t.isEmpty())
            return "";
        if (t.contains("옥션") || t.equals("auction"))
            return "auction";
        if (t.contains("g마켓") || t.equals("gmarket"))
            return "gmarket";
        if (t.contains("11번가") || t.contains("11st") || t.equals("11post"))
            return "11post";
        if (t.contains("네이버") || t.equals("naver"))
            return "naver";
        if (t.contains("홈플러스") || t.equals("homeplus"))
            return "homeplus";
        if (t.contains("쿠팡") || t.equals("coupang"))
            return "coupang";
        if (t.contains("다이소") || t.equals("daiso"))
            return "daiso";
        if (t.contains("마트") || t.equals("mart") || t.equals("mart_itemized"))
            return "mart_itemized";
        if (t.contains("편의점") || t.equals("convenience"))
            return "convenience";
        return t;
    }

    private static boolean isAuctionSlip(Document doc) {
        if (doc == null || doc.getText() == null) {
            return false;
        }

        String text = doc.getText();
        // OCR 공백/줄바꿈 흔들림 대응: Auction 전자 지불 / Auction전자지불 모두 허용
        Pattern auctionMerchantNo = Pattern.compile("(?i)auction\\s*전자\\s*지불");
        return auctionMerchantNo.matcher(text).find();
    }
}
