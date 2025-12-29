package com.example.demo.parser;

import com.google.cloud.documentai.v1.Document;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GenericCardSlipParser extends BaseReceiptParser {

    @Override
    public ReceiptResult parse(Document doc) {
        ReceiptResult r = new ReceiptResult();

        String raw = text(doc);
        String text = normalize(raw);

        // 1) 일시(날짜/시간)
        extractDateTime(text, r);

        // 2) 승인번호
        r.approval.approvalNo = firstNonNull(
                extract(text, "(승인번호)\\s*[:：-]?\\s*([0-9]{5,12})", 2),
                extract(text, "(승\\s*인\\s*번\\s*호)\\s*[:：-]?\\s*([0-9]{5,12})", 2),
                extract(text, "(승인)\\s*[:：-]?\\s*([0-9]{5,12})", 2)
        );

        // 3) 카드번호(마스킹)
        r.payment.cardNo = firstNonNull(
                extract(text, "(카드번호)\\s*[:：-]?\\s*([0-9\\-*xX]{8,})", 2),
                extract(text, "(CARD\\s*NO)\\s*[:：-]?\\s*([0-9\\-*xX]{8,})", 2),
                extractMaskedPanLoose(text)
        );
        r.payment.cardMasked = r.payment.cardNo;

        // 4) 할부
        r.payment.installment = firstNonNull(
                extract(text, "(일시불)", 1),
                extract(text, "(할부)\\s*([0-9]{1,2})\\s*개월", 2),
                extract(text, "(할부)\\s*[:：-]?\\s*([0-9]{1,2})", 2)
        );

        // 5) 결제수단/카드사(대충)
        r.payment.type = firstNonNull(
                extract(text, "(신용카드|체크카드|간편결제|삼성페이|네이버페이|카카오페이|토스페이|애플페이|구글페이)", 1),
                "신용카드"
        );
        r.payment.cardBrand = firstNonNull(
                extract(text, "(국민|KB|신한|삼성|현대|롯데|하나|NH|농협|BC|우리)\\s*(카드)?", 1),
                extract(text, "(VISA|MASTER|MASTERCARD|AMEX|JCB)", 1)
        );

        // 6) 총액/부가세/공급가액/할인 후보 랭킹
        extractTotalsByRanking(text, r);

        // 7) 상호/사업자/전화/주소
        extractMerchantInfo(text, r);

        // 8) VAN/TID/가맹점번호 등
        r.approval.merchantNo = firstNonNull(
                extract(text, "(가맹점번호)\\s*[:：-]?\\s*([0-9A-Za-z\\-]{4,})", 2),
                extract(text, "(가맹점\\s*번호)\\s*[:：-]?\\s*([0-9A-Za-z\\-]{4,})", 2)
        );
        r.approval.tid = firstNonNull(
                extract(text, "(TID)\\s*[:：-]?\\s*([0-9A-Za-z\\-]{4,})", 2),
                extract(text, "(단말기번호|단말기\\s*번호)\\s*[:：-]?\\s*([0-9A-Za-z\\-]{4,})", 2)
        );
        r.approval.van = firstNonNull(
                extract(text, "(VAN)\\s*[:：-]?\\s*([0-9A-Za-z\\-]{2,})", 2),
                extract(text, "(밴사|밴)\\s*[:：-]?\\s*([0-9A-Za-z\\-]{2,})", 2),
                extract(text, "(KICC|KSNET|NICE|SMARTRO|KIS|KOVAN)", 1)
        );

        if (r.totals.total != null) r.payment.approvalAmt = String.valueOf(r.totals.total);

        return r;
    }

    private String normalize(String raw) {
        if (raw == null) return "";
        return raw
                .replaceAll("[\\t\\x0B\\f\\r]+", " ")
                .replaceAll("(?<=\\d)\\.(?=\\d{3}\\b)", ",")
                .replaceAll(" +", " ")
                .trim();
    }

    private void extractDateTime(String text, ReceiptResult r) {
        r.meta.saleDate = firstNonNull(
                extract(text, "(20\\d{2}[./-]\\d{1,2}[./-]\\d{1,2})", 1),
                extract(text, "(20\\d{2}년\\s*\\d{1,2}월\\s*\\d{1,2}일)", 1)
        );

        r.meta.saleTime = firstNonNull(
                extract(text, "([0-2]?\\d:[0-5]\\d(?::[0-5]\\d)?)", 1)
        );

        r.approval.authDateTime = firstNonNull(
                extract(text, "(승인일시)\\s*[:：-]?\\s*(20\\d{2}[./-]\\d{1,2}[./-]\\d{1,2}\\s*[0-2]?\\d:[0-5]\\d(?::[0-5]\\d)?)", 2),
                (r.meta.saleDate != null && r.meta.saleTime != null) ? (r.meta.saleDate + " " + r.meta.saleTime) : null
        );
    }

    private void extractTotalsByRanking(String text, ReceiptResult r) {
        String t = text
                .replaceAll("(?=(합계|총액|결제금액|승인금액|공급가액|부가세|할인|면세|과세))", "\n")
                .replaceAll("\\n+", "\n");

        String[] lines = t.split("\\n");

        List<Cand> totalCands = new ArrayList<>();
        List<Cand> vatCands = new ArrayList<>();
        List<Cand> supplyCands = new ArrayList<>();
        List<Cand> discCands = new ArrayList<>();

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) continue;

            List<Integer> monies = extractAllMoney(line);
            if (monies.isEmpty()) continue;

            for (Integer money : monies) {
                int base = 0;

                if (containsAny(line, "합계", "총액", "결제금액", "승인금액", "총 결제", "승인 금액")) base += 10;
                if (containsAny(line, "부가세", "VAT")) base += 7;
                if (containsAny(line, "공급가액")) base += 7;
                if (containsAny(line, "할인", "DC", "쿠폰")) base += 6;
                if (containsAny(line, "면세", "과세")) base += 2;

                if (i > lines.length * 0.75) base -= 2;

                if (containsAny(line, "부가세", "VAT")) vatCands.add(new Cand(money, base + 2, line));
                else if (containsAny(line, "공급가액")) supplyCands.add(new Cand(money, base + 2, line));
                else if (containsAny(line, "할인", "DC", "쿠폰")) discCands.add(new Cand(money, base + 1, line));
                else totalCands.add(new Cand(money, base, line));
            }
        }

        r.totals.total = pickBest(totalCands);
        r.totals.vat = pickBest(vatCands);
        r.totals.taxable = pickBest(supplyCands);
        r.totals.discount = pickBest(discCands);

        if (r.totals.total != null && r.totals.taxable != null && r.totals.vat != null) {
            int sum = r.totals.taxable + r.totals.vat;
            int tol = Math.max(10, (int) (r.totals.total * 0.02));
            if (Math.abs(sum - r.totals.total) > tol) {
                Integer better = pickBest(filterByKeyword(totalCands, "결제금액", "승인금액"));
                if (better != null) r.totals.total = better;
            }
        }

        r.totals.taxFree = firstInt(text, "(면세)[^0-9]*([0-9,]+)");
    }

    private List<Cand> filterByKeyword(List<Cand> cands, String... keys) {
        List<Cand> out = new ArrayList<>();
        for (Cand c : cands) {
            for (String k : keys) {
                if (c.context != null && c.context.contains(k)) {
                    out.add(new Cand(c.amount, c.score + 3, c.context));
                    break;
                }
            }
        }
        return out;
    }

    private void extractMerchantInfo(String text, ReceiptResult r) {
        r.merchant.bizNo = firstNonNull(
                extract(text, "\\b(\\d{3}[- ]?\\d{2}[- ]?\\d{5})\\b", 1)
        );

        r.merchant.tel = firstNonNull(
                extract(text, "(0\\d{1,2}[- ]?\\d{3,4}[- ]?\\d{4})", 1)
        );

        r.merchant.address = firstNonNull(
                extract(text, "([가-힣]+시\\s*[가-힣]+(구|군)\\s*[가-힣0-9\\s\\-]+\\d+번?[^\\n]*)", 1),
                extract(text, "([가-힣]+도\\s*[가-힣]+시\\s*[가-힣]+(구|군)\\s*[가-힣0-9\\s\\-]+)", 1)
        );

        String name1 = firstNonNull(
                extract(text, "(상호|가맹점명|가맹점)\\s*[:：-]?\\s*([가-힣A-Za-z0-9()\\-\\s]{2,30})", 2)
        );

        String name2 = null;
        if (r.merchant.bizNo != null) name2 = merchantNearBizNo(text, r.merchant.bizNo);

        String name3 = guessTopName(text);

        r.merchant.name = firstNonNull(cleanMerchantName(name1), cleanMerchantName(name2), cleanMerchantName(name3));
    }

    private String merchantNearBizNo(String text, String bizNoRaw) {
        String bizNo = bizNoRaw.replaceAll("[^0-9]", "");
        String[] lines = text.split("\\r?\\n");
        for (int i = 0; i < lines.length; i++) {
            String onlyNum = lines[i].replaceAll("[^0-9]", "");
            if (onlyNum.contains(bizNo)) {
                int from = Math.max(0, i - 2);
                int to = Math.min(lines.length - 1, i + 2);
                for (int j = from; j <= to; j++) {
                    String cand = lines[j].trim();
                    if (looksLikeMerchantName(cand)) return cand;
                }
            }
        }
        return null;
    }

    private String guessTopName(String text) {
        String[] lines = text.split("\\r?\\n");
        String best = null;
        int bestScore = -999;

        for (int i = 0; i < Math.min(5, lines.length); i++) {
            String cand = lines[i].trim();
            int s = 0;
            if (looksLikeMerchantName(cand)) s += 3;
            if (cand.length() >= 6) s += 1;
            if (cand.contains("㈜") || cand.contains("(주)") || cand.contains("주식회사")) s += 1;

            if (s > bestScore) { bestScore = s; best = cand; }
        }
        return bestScore >= 3 ? best : null;
    }

    private boolean looksLikeMerchantName(String s) {
        if (s == null) return false;
        s = s.trim();
        if (s.length() < 2) return false;

        if (containsAny(s, "승인", "카드", "일시불", "할부", "매입", "단말기", "고객용", "가맹점번호", "부가세", "합계", "결제금액"))
            return false;

        int digit = 0, letter = 0;
        for (char c : s.toCharArray()) {
            if (Character.isDigit(c)) digit++;
            if (Character.isLetter(c) || (c >= '가' && c <= '힣')) letter++;
        }
        if (letter < 2) return false;
        return digit <= s.length() / 2;
    }

    private String cleanMerchantName(String s) {
        if (s == null) return null;
        s = s.replaceAll("\\s{2,}", " ").trim();
        s = s.replaceAll("(고객용|승인|카드|영수증|매출전표)$", "").trim();
        if (s.length() < 2) return null;
        return s;
    }

    private static class Cand {
        final Integer amount;
        final int score;
        final String context;
        Cand(Integer amount, int score, String context) {
            this.amount = amount; this.score = score; this.context = context;
        }
    }

    private Integer pickBest(List<Cand> cands) {
        if (cands == null || cands.isEmpty()) return null;
        cands.sort((a, b) -> Integer.compare(b.score, a.score));
        return cands.get(0).amount;
    }

    private List<Integer> extractAllMoney(String line) {
        List<Integer> out = new ArrayList<>();
        if (line == null) return out;

        Matcher m = Pattern.compile("([0-9]{1,3}(?:,[0-9]{3})+|[0-9]{4,})").matcher(line);
        while (m.find()) {
            Integer v = toInt(m.group(1));
            if (v == null || v <= 0) continue;
            out.add(v);
        }
        return out;
    }

    private String extractMaskedPanLoose(String text) {
        if (text == null) return null;
        Pattern p = Pattern.compile("\\b(\\d{4})[\\s\\-]*([*Xx]{2,}|\\d{0,2})[\\s\\-*Xx0-9]{2,12}(\\d{4})\\b");
        Matcher m = p.matcher(text);
        if (m.find()) return m.group(1) + "********" + m.group(3);
        return null;
    }
}
