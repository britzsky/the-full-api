package com.example.demo.parser;

import java.util.EnumMap;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.example.demo.model.CardReceiptType;

@Component
public class CardReceiptParserFactory {

    private final Map<CardReceiptType, BaseReceiptParser> map = new EnumMap<>(CardReceiptType.class);

    public CardReceiptParserFactory() {
        // 기존 파서 재사용
        BaseReceiptParser convenience = new ConvenienceReceiptParser();
        BaseReceiptParser coupang = new CoupangReceiptParser();

        // 새 범용 파서(폴백)
        BaseReceiptParser generic = new GenericCardSlipParser();

        map.put(CardReceiptType.CONVENIENCE, convenience);
        map.put(CardReceiptType.COUPANG_APP, coupang);
        map.put(CardReceiptType.COUPANG_CARD, coupang);
        map.put(CardReceiptType.MART_ITEMIZED, generic);      // 아직 마트 전용 없으면 generic로 우선
        map.put(CardReceiptType.CARD_SLIP_GENERIC, generic);
        map.put(CardReceiptType.UNKNOWN, generic);
    }

    public BaseReceiptParser get(CardReceiptType type) {
        return map.getOrDefault(type, map.get(CardReceiptType.UNKNOWN));
    }
}