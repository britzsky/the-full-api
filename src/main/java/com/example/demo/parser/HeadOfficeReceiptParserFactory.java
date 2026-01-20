package com.example.demo.parser;

import com.google.cloud.documentai.v1.Document;

public class HeadOfficeReceiptParserFactory {

    public static BaseReceiptParser.ReceiptResult parse(Document doc, String type) {
        BaseReceiptParser parser;

        switch (type.toLowerCase()) {
            case "11post":
                parser = new HeadOffice11PostReceiptParser();
                break;
            case "coupang":
                parser = new HeadOfficeCoupangReceiptParser();
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
            default:
                throw new IllegalArgumentException("지원하지 않는 영수증 타입: " + type);
        }

        return parser.parse(doc);
    }
}
