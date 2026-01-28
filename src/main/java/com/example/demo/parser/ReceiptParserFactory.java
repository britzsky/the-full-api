package com.example.demo.parser;

import com.google.cloud.documentai.v1.Document;

public class ReceiptParserFactory {

    public static BaseReceiptParser.ReceiptResult parse(Document doc, String type) {
        BaseReceiptParser parser;
        //System.out.println("type.toLowerCase() == " + type.toLowerCase());
        switch (type) {
            case "MART_ITEMIZED":
                parser = new MartReceiptParser();
                break;
            case "CONVENIENCE":
                parser = new ConvenienceReceiptParser();
                break;
            case "COUPANG_CARD":
                parser = new CoupangReceiptParser();
                break;
            case "COUPANG_APP":
                parser = new DeliveryReceiptParser();
                break;
            case "TRANSACTION":
                parser = new TransactionStatementParser();
                break;
            default:
                throw new IllegalArgumentException("지원하지 않는 영수증 타입: " + type);
        }

        return parser.parse(doc);
    }
}
