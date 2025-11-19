package com.example.demo.parser;

import com.google.cloud.documentai.v1.Document;

public class ReceiptParserFactory {

    public static BaseReceiptParser.ReceiptResult parse(Document doc, String type) {
        BaseReceiptParser parser;

        switch (type.toLowerCase()) {
            case "mart":
                parser = new MartReceiptParser();
                break;
            case "convenience":
                parser = new ConvenienceReceiptParser();
                break;
            case "coupang":
                parser = new CoupangReceiptParser();
                break;
            case "delivery":
                parser = new DeliveryReceiptParser();
                break;
            default:
                throw new IllegalArgumentException("지원하지 않는 영수증 타입: " + type);
        }

        return parser.parse(doc);
    }
}
