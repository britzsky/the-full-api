package com.example.demo.model;

import com.example.demo.parser.BaseReceiptParser;

public class CardReceiptResponse {
    public CardReceiptType detectedType;
    public double confidence; // 간단 점수(분류 점수 기반)
    public BaseReceiptParser.ReceiptResult result;

    public CardReceiptResponse() {}

    public CardReceiptResponse(CardReceiptType detectedType, double confidence, BaseReceiptParser.ReceiptResult result) {
        this.detectedType = detectedType;
        this.confidence = confidence;
        this.result = result;
    }
}