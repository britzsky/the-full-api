package com.example.demo.parser;

import java.util.regex.*;
import java.util.*;
import com.google.cloud.documentai.v1.Document;

public abstract class BaseReceiptParser {

    // -------------------- 추상 메서드 --------------------
    public abstract ReceiptResult parse(Document doc);

    // -------------------- 공용 데이터 구조 --------------------
    public static class ReceiptResult {
        public Merchant merchant = new Merchant();
        public Meta meta = new Meta();
        public List<Item> items = new ArrayList<>();
        public Totals totals = new Totals();
        public Payment payment = new Payment();
        public Customer customer = new Customer();
        public Approval approval = new Approval();
        public Map<String, Object> extra = new HashMap<>();
    }

    public static class Merchant {
        public String name, bizNo, tel, address;
    }

    public static class Meta {
        public String saleDate, saleTime, receiptNo, pos, registerNo, cashier;
    }

    public static class Item {
        public String lineNo, name, barcode, taxFlag, option;
        public Integer unitPrice, qty, amount;
    }

    public static class Totals {
        public Integer subtotal, total, discount, vat, taxFree, card, cash, change, taxable;
    }

    public static class Payment {
        public String type, cardBrand, cardMasked, approvalAmt, installment, cardNo, approvalTime, merchant;
    }

    public static class Customer {
        public String nameOrGroup;
        public Integer pointReceived, pointBalance;
    }

    public static class Approval {
        public String approvalNo, merchantNo, acquirer, posNo, van, authDateTime, tid, cashReceiptNo;
    }

    // -------------------- 공용 OCR 텍스트 유틸 --------------------
    /** Document 전체 텍스트 */
    protected String text(Document doc) {
        if (doc == null || doc.getText() == null) return "";
        return doc.getText();
    }

    /** 숫자 문자열을 안전하게 int 로 */
    protected Integer toInt(String s) {
        if (s == null) return null;
        String clean = s.replaceAll("[^0-9]", "");
        if (clean.isEmpty()) return null;
        try {
            return Integer.parseInt(clean);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // -------------------- 안정형 extract() --------------------
    /** 기본 extract — 첫 번째 그룹 또는 전체 매칭 반환 */
    protected String extract(String src, String regex) {
        return extract(src, regex, 1);
    }

    /** 안전한 그룹 인덱스 추출 */
    protected String extract(String src, String regex, int groupIndex) {
        if (src == null || regex == null) return null;
        try {
            Pattern p = Pattern.compile(regex, Pattern.MULTILINE);
            Matcher m = p.matcher(src);
            if (m.find()) {
                int groupCount = m.groupCount();
                // ① 지정 그룹 존재 → 그 그룹
                if (groupIndex <= groupCount && groupIndex > 0) {
                    return Optional.ofNullable(m.group(groupIndex)).orElse("").trim();
                }
                // ② 그룹이 하나라도 있으면 첫 번째 그룹 반환
                else if (groupCount >= 1) {
                    return Optional.ofNullable(m.group(1)).orElse("").trim();
                }
                // ③ 그룹이 없으면 전체 매칭 반환
                else {
                    return Optional.ofNullable(m.group()).orElse("").trim();
                }
            }
        } catch (Exception e) {
            // 실패 시 null
        }
        return null;
    }

    // -------------------- 공용 헬퍼 --------------------
    protected String firstNonNull(String... arr) {
        for (String s : arr) if (s != null && !s.isBlank()) return s.trim();
        return null;
    }

    protected Integer firstInt(String src, String regex) {
        String v = extract(src, regex, 2);
        if (v == null) v = extract(src, regex, 1);
        return toInt(v);
    }

    protected boolean containsAny(String src, String... keys) {
        if (src == null) return false;
        for (String k : keys) if (src.contains(k)) return true;
        return false;
    }
    
    protected String safe(String s) {
        if (s == null) return "";
        s = s.trim();
        if (s.equalsIgnoreCase("null")) return "";
        return s;
    }
}
