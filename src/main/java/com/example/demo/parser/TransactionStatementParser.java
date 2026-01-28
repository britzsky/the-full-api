package com.example.demo.parser;

import com.google.cloud.documentai.v1.Document;
import com.google.cloud.documentai.v1.NormalizedVertex;
import com.google.cloud.documentai.v1.Vertex;
import com.google.cloud.documentai.v1.BoundingPoly;
import com.google.type.*;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TransactionStatementParser extends BaseReceiptParser {

    private static final boolean DEBUG = Boolean.parseBoolean(System.getProperty("ts.debug", "false"));
    private static final boolean DUMP  = Boolean.parseBoolean(System.getProperty("ts.dump", "false"));

    // -----------------------------
    // 결과 모델
    // -----------------------------
    public static class TransactionStatementResult {
        public Party supplier = new Party();
        public Party buyer = new Party();
        public String issueDate;
        public String docNo;
        public List<StatementItem> items = new ArrayList<>();
        public Totals totals = new Totals();
        public String rawText;
        public String normalizedText;
    }

    public static class Party {
        public String bizNo;
        public String name;
        public String ceo;
        public String address;
        public String bizType;
        public String bizItem;
    }

    public static class StatementItem {
        public String name;
        public String unit;
        public Double qty;
        public Integer unitPrice;
        public Integer supplyAmt;
        public Integer taxAmt;

        @Override
        public String toString() {
            return "StatementItem{" +
                    "name='" + name + '\'' +
                    ", unit='" + unit + '\'' +
                    ", qty=" + qty +
                    ", unitPrice=" + unitPrice +
                    ", supplyAmt=" + supplyAmt +
                    ", taxAmt=" + taxAmt +
                    '}';
        }
    }

    public static class Totals {
        public Integer supplyTotal;
        public Integer taxTotal;
        public Integer grandTotal;
        public Integer prevBalance;
        public Integer balance;
    }

    // -----------------------------
    // 패턴/상수
    // -----------------------------
    private static final Set<String> UNIT_SET = new LinkedHashSet<>(Arrays.asList(
            "박스", "봉", "kg", "KG", "팩", "EA", "개", "통", "캔", "병", "줄", "포", "롤", "세트", "SET", "묶음", "판"
    ));

    private static final Pattern P_BIZNO = Pattern.compile("\\b\\d{3}-\\d{2}-\\d{5}\\b");
    private static final Pattern P_NUM = Pattern.compile("\\d{1,3}(?:,\\d{3})+|\\d+(?:\\.\\d+)?");

    private static final Pattern P_UNIT_FOLLOWED_BY_QTY = Pattern.compile(
            "(박스|봉|kg|KG|팩|EA|개|통|캔|병|줄|포|롤|세트|SET|묶음|판)\\s*(\\d+(?:\\.\\d+)?)"
    );

    // ✅ totals 라벨 (OCR 깨짐 포함) - 더 강하게
    // "공가 |급액" 같은 찢어진 형태 대응
    private static final Pattern P_TOTAL_LABEL_SUPPLY = Pattern.compile(
            "(공급가액|공\\s*급\\s*가\\s*액|공\\s*가\\s*액|공기\\s*급액|공\\s*기\\s*급\\s*액|공\\s*가\\s*\\|\\s*급\\s*액|공\\s*가\\s*급\\s*액)"
    );
    private static final Pattern P_TOTAL_LABEL_TAX    = Pattern.compile("(세\\s*액|세역)");
    private static final Pattern P_TOTAL_LABEL_GRAND  = Pattern.compile("(합\\s*계|총\\s*계|총\\s*액|합\\s*계\\s*금\\s*액)");
    private static final Pattern P_TOTAL_LABEL_PREV   = Pattern.compile("(전\\s*미\\s*수)");
    private static final Pattern P_TOTAL_LABEL_BAL    = Pattern.compile("(미\\s*수\\s*금)");

    // ✅ 라벨(찢어진 케이스까지) - \b 제거 (한글에서 \b가 잘 안 먹는 게 핵심 버그)
    private static final Pattern P_LABEL_SANGHO = Pattern.compile("^\\s*상\\s*호(?:\\s|:|$).*");
    private static final Pattern P_LABEL_SEONG  = Pattern.compile("^\\s*성(?:\\s|:|$).*");
    private static final Pattern P_LABEL_SEONGMYEONG = Pattern.compile("^\\s*성\\s*명(?:\\s|:|$).*");
    private static final Pattern P_LABEL_MYEONG = Pattern.compile("^\\s*명(?:\\s|:|$).*");
    private static final Pattern P_LABEL_JUSO   = Pattern.compile("^\\s*(주\\s*소|주소)(?:\\s|:|$).*");
    private static final Pattern P_LABEL_UPTAE  = Pattern.compile("^\\s*업\\s*태(?:\\s|:|$).*");
    private static final Pattern P_LABEL_JONGMOK = Pattern.compile("^\\s*(종\\s*목|종목)(?:\\s|:|$).*");

    // ✅ 한글 \b 제거
    private static final Pattern P_PARTY_NOISE_LINE = Pattern.compile(
            "^(등록|공번호|공급받는\\s*자|공급받는자|공급\\s*받는\\s*자|인수자|거래명세표|\\(1/1\\)|\\(공급받는자\\s*보관용\\)|보관용).*$"
    );

    private static final Set<String> PARTY_NOISE_TOKENS = new HashSet<>(Arrays.asList(
            "급", "자", "는", "|", "공급", "받는", "공급받는", "공급자"
    ));

    @Override
    public ReceiptResult parse(Document doc) {
        TransactionStatementResult st = parseStatement(doc);

        ReceiptResult r = new ReceiptResult();

        // ✅ merchant/meta 기본 매핑
        r.merchant.name = st.supplier.name;
        r.merchant.bizNo = st.supplier.bizNo;
        r.merchant.address = st.supplier.address;

        r.meta.saleDate = st.issueDate;
        r.meta.receiptNo = st.docNo;

        // ✅ items 매핑
        List<Item> items = new ArrayList<>();
        for (StatementItem si : st.items) {
            Item it = new Item();
            it.name = si.name;

            // 거래명세표는 qty가 Double이라 반올림해서 Integer로
            it.qty = (si.qty == null) ? 1 : (int) Math.round(si.qty);

            it.unitPrice = si.unitPrice;
            it.amount = si.supplyAmt;     // 공급가액을 amount로 사용

            items.add(it);
        }
        r.items = items;

        // ✅ totals 매핑
        r.totals.subtotal = st.totals.supplyTotal;
        r.totals.vat      = st.totals.taxTotal;
        r.totals.total    = st.totals.grandTotal;

        r.extra.put("prevBalance", st.totals.prevBalance);
        r.extra.put("balance", st.totals.balance);

        r.extra.put("ts", st);
        r.extra.put("parserType", "TRANSACTION");

        return r;
    }

    public TransactionStatementResult parseStatement(Document doc) {
        String raw = text(doc);

        TransactionStatementResult res = new TransactionStatementResult();
        res.rawText = raw;

        String text = normalize(raw);
        res.normalizedText = text;
        dumpResult("AFTER_NORMALIZE", res);

        parseHeader(text, res);
        dumpResult("AFTER_HEADER", res);

        // ✅ parties: FormFields(레이아웃) 우선 → 실패 시 기존 텍스트 파싱
        if (!tryParsePartiesFromFormFields(doc, res)) {
            parseParties(text, res);
        }
        dumpResult("AFTER_PARTIES", res);
        
     // parties 파싱 이후 (AFTER_PARTIES 전에/후에)
        salvagePartiesByLabelOrder(text, res);

        // ✅ items: Tables 우선 → 실패 시 기존 텍스트 파싱
        List<StatementItem> tableItems = parseItemsFromTables(doc);
        if (tableItems != null && !tableItems.isEmpty()) {
            res.items = tableItems;
        } else {
            res.items = parseItems(text);
        }
        dumpResult("AFTER_ITEMS", res);

        parseTotals(text, res);
        dumpResult("AFTER_TOTALS", res);

        System.out.println("📄 [거래명세표] date=" + res.issueDate + ", docNo=" + res.docNo);
        System.out.println("🏢 공급자: " + res.supplier.bizNo + " / " + res.supplier.name + " / " + res.supplier.ceo);
        System.out.println("🏬 공급받는자: " + res.buyer.bizNo + " / " + res.buyer.name + " / " + res.buyer.ceo);
        System.out.println("📦 품목수: " + (res.items == null ? 0 : res.items.size()));
        System.out.println("💰 공급가액=" + res.totals.supplyTotal + ", 세액=" + res.totals.taxTotal + ", 합계=" + res.totals.grandTotal);
        System.out.println("💳 전미수=" + res.totals.prevBalance + ", 미수금=" + res.totals.balance);

        return res;
    }
    
    private void salvagePartiesByLabelOrder(String text, TransactionStatementResult res) {
        if (res == null) return;
        if (text == null) text = "";

        String[] lines = text.split("\\n");

        // 1) "상호" 라벨 값들을 순서대로 수집
        List<String> names = new ArrayList<>();
        List<Integer> nameLineIdx = new ArrayList<>();

        Pattern pSanghoLine = Pattern.compile("^\\s*상\\s*호\\s*(.+)?$"); // "상호 엔푸드" 케이스
        for (int i = 0; i < lines.length; i++) {
            String line = safeTrim(lines[i]);
            if (line.isEmpty()) continue;

            Matcher m = pSanghoLine.matcher(line);
            if (m.find()) {
                String tail = safeTrim(m.group(1));
                if (tail.isEmpty()) {
                    // "상호"만 있고 다음줄에 값이 오는 케이스
                    if (i + 1 < lines.length) tail = safeTrim(lines[i + 1]);
                }
                tail = cleanPartyText(tail);
                if (tail != null && isGoodNameCandidate(tail)) {
                    names.add(tail);
                    nameLineIdx.add(i);
                }
            }
        }

        if (res.supplier == null) res.supplier = new Party();
        if (res.buyer == null) res.buyer = new Party();

        // 첫 번째 상호 = 공급자, 두 번째 상호 = 공급받는자
        if (res.supplier.name == null && names.size() >= 1) res.supplier.name = names.get(0);
        if (res.buyer.name == null && names.size() >= 2) res.buyer.name = names.get(1);

        // 2) 대표자(성/성명/대표) 순서대로 수집 후 매핑
        List<String> ceos = new ArrayList<>();
        Pattern pCeo = Pattern.compile("(?:성\\s*명|대표\\s*자|대표|성)\\s*[:：]?\\s*([가-힣]{2,5})");
        for (int i = 0; i < lines.length; i++) {
            String line = safeTrim(lines[i]);
            if (line.isEmpty()) continue;

            Matcher m = pCeo.matcher(line);
            if (m.find()) {
                String name = safeTrim(m.group(1));
                name = cleanPartyText(name);
                if (name != null && name.matches("^[가-힣]{2,5}$")) ceos.add(name);
            }
        }
        if (res.supplier.ceo == null && ceos.size() >= 1) res.supplier.ceo = ceos.get(0);
        if (res.buyer.ceo == null && ceos.size() >= 2) res.buyer.ceo = ceos.get(1);

        // 3) 공급자 상호 prefix(법인명) 라인 결합
        // - "상호 엔푸드" 다음줄에 "농업회사법인주식회사 씨 ..." 같은 라인이 붙는 템플릿 대응
        if (res.supplier.name != null && nameLineIdx.size() >= 1) {
            int idx = nameLineIdx.get(0);

            // 다음 1~2줄에서 prefix 후보 찾기
            for (int k = 1; k <= 2; k++) {
                if (idx + k >= lines.length) break;
                String cand = safeTrim(lines[idx + k]);
                if (cand.isEmpty()) continue;

                // 대표자/주소/업태/종목/품목 라벨은 제외
                if (cand.matches(".*(주\\s*소|주소|업\\s*태|종\\s*목|품\\s*목|규\\s*격|단\\s*위|수\\s*량|단\\s*가|세\\s*액|합\\s*계|전\\s*미\\s*수|미\\s*수\\s*금).*")) continue;

                boolean corpHint = cand.contains("주식회사") || cand.contains("유한회사")
                        || cand.contains("농업회사법인") || cand.contains("회사법인") || cand.contains("(주)") || cand.contains("㈜");

                if (!corpHint) continue;

                // cand 안에 "성 유인식" 같은 대표자 부분이 같이 있으면 그 앞까지만 prefix로 자르기
                int cut = cand.indexOf("성");
                if (cut > 0) cand = cand.substring(0, cut).trim();

                cand = cleanPartyText(cand);
                if (cand == null) continue;

                // 이미 포함되어 있으면 스킵
                String tail = res.supplier.name.trim();
                String pre  = cand.trim();
                if (pre.contains(tail) || tail.contains(pre)) break;

                // "씨"+"엔푸드" => "씨엔푸드" 붙임
                if (pre.endsWith("씨") && tail.startsWith("엔")) res.supplier.name = pre + tail;
                else res.supplier.name = pre + " " + tail;

                break;
            }
        }

        // 최종 정리 (특히 "받" 같은 꼬리 제거가 필요)
        res.supplier.name = cleanPartyText(res.supplier.name);
        res.supplier.ceo  = cleanPartyText(res.supplier.ceo);
        res.buyer.name    = cleanPartyText(res.buyer.name);
        res.buyer.ceo     = cleanPartyText(res.buyer.ceo);
    }
    
    private void salvageSupplierFromText(String text, TransactionStatementResult res) {
        if (res == null) return;
        if (text == null) text = "";

        // 이미 supplier가 충분히 채워졌으면 스킵
        boolean supplierOk = (res.supplier != null) && (res.supplier.name != null || res.supplier.ceo != null);
        if (supplierOk) return;

        // 사업자번호 기반으로 supplier 구간을 잡는다 (첫 bizno 주변)
        String[] lines = text.split("\\n");
        if (lines.length == 0) return;

        String supplierBiz = (res.supplier == null) ? null : res.supplier.bizNo;
        String buyerBiz = (res.buyer == null) ? null : res.buyer.bizNo;

        int idxSupBiz = -1;
        if (supplierBiz != null) {
            Pattern p = Pattern.compile(".*" + Pattern.quote(supplierBiz) + ".*");
            idxSupBiz = indexOfFirst(lines, 0, p);
        }
        if (idxSupBiz < 0) {
            // supplierBiz가 없으면 첫 bizno 등장 라인
            for (int i = 0; i < lines.length; i++) {
                if (P_BIZNO.matcher(lines[i]).find()) { idxSupBiz = i; break; }
            }
        }
        if (idxSupBiz < 0) return;

        int idxBuyerBiz = -1;
        if (buyerBiz != null) {
            Pattern p = Pattern.compile(".*" + Pattern.quote(buyerBiz) + ".*");
            idxBuyerBiz = indexOfFirst(lines, 0, p);
        }

        // items 헤더 라인(있으면 거기 전까지만)
        int idxItemsHdr = indexOfFirst(lines, 0,
                Pattern.compile(".*품\\s*목.*\\(\\s*규\\s*격\\s*\\).*|.*품\\s*목.*규\\s*격.*"));

        int end = lines.length;
        if (idxBuyerBiz >= 0) end = Math.min(end, idxBuyerBiz);
        if (idxItemsHdr >= 0) end = Math.min(end, idxItemsHdr);

        // supplier 윈도우: supplierBiz 라인 기준 앞/뒤로 적당히
        int from = Math.max(0, idxSupBiz - 10);
        int to   = Math.min(end, idxSupBiz + 18);

        List<String> win = new ArrayList<>();
        for (int i = from; i < to; i++) {
            String s = safeTrim(lines[i]);
            if (!s.isEmpty()) win.add(s);
        }

        if (res.supplier == null) res.supplier = new Party();

        // 1) 라벨 기반 추출(같은 줄 또는 다음 줄)
        if (res.supplier.name == null) {
            String name = pickValueByLabel(win, Pattern.compile("상\\s*호\\s*명?|상\\s*호"), 2);
            if (name != null) res.supplier.name = cleanPartyText(name);
        }
        if (res.supplier.ceo == null) {
            String ceo = pickValueByLabel(win, Pattern.compile("대\\s*표\\s*자|대\\s*표|성\\s*명|성"), 2);
            if (ceo != null) res.supplier.ceo = cleanPartyText(ceo);
        }

        // 2) 라벨이 안 찍힌 경우: “회사명 후보 라인” 휴리스틱
        if (res.supplier.name == null) {
            String guess = guessCompanyName(win, res);
            if (guess != null) res.supplier.name = cleanPartyText(guess);
        }

        // 3) 대표자 휴리스틱: "대표 홍길동" / "성명 홍길동"
        if (res.supplier.ceo == null) {
            String joined = String.join(" ", win).replaceAll("\\s{2,}", " ").trim();
            Matcher m = Pattern.compile("(대표자|대표|성명|성)\\s*[:：]?\\s*([가-힣]{2,5})").matcher(joined);
            if (m.find()) res.supplier.ceo = m.group(2);
        }

        // 최종 정리
        res.supplier.name = cleanPartyText(res.supplier.name);
        res.supplier.ceo  = cleanPartyText(res.supplier.ceo);
    }

    private String pickValueByLabel(List<String> win, Pattern label, int forward) {
        for (int i = 0; i < win.size(); i++) {
            String line = win.get(i);
            if (!label.matcher(line).find()) continue;

            // 같은 줄에서 ":" 이후나 라벨 제거한 꼬리
            String tail = line;
            tail = tail.replaceAll("[:：]", " ");
            tail = tail.replaceAll("상\\s*호\\s*명?|상\\s*호|대\\s*표\\s*자|대\\s*표|성\\s*명|성", " ");
            tail = tail.replaceAll("\\s{2,}", " ").trim();
            tail = tail.replaceAll(".*\\b\\d{3}-\\d{2}-\\d{5}\\b", " ").trim(); // bizno 붙은 케이스 제거

            if (isGoodNameCandidate(tail)) return tail;

            // 다음 줄에서 값 찾기
            for (int j = i + 1; j < win.size() && j <= i + forward; j++) {
                String n = safeTrim(win.get(j));
                if (isGoodNameCandidate(n)) return n;
            }
        }
        return null;
    }

    private String guessCompanyName(List<String> win, TransactionStatementResult res) {
        // “회사명”로 보이는 라인 우선순위:
        // - (주), ㈜, 주식회사, 유한회사, 농업회사법인, 법인 등 포함
        // - 숫자/금액/주소 키워드 적고 한글 비중 높은 라인
        for (String s : win) {
            String line = safeTrim(s);
            if (line.isEmpty()) continue;
            if (line.contains("￦")) continue;
            if (line.matches(".*\\d{1,3}(?:,\\d{3})+.*")) continue; // 금액
            if (line.matches(".*(주\\s*소|주소|업\\s*태|종\\s*목|품\\s*목|규\\s*격|단\\s*위|수\\s*량|단\\s*가|세\\s*액|합\\s*계|전\\s*미\\s*수|미\\s*수\\s*금).*"))
                continue;

            String compact = line.replaceAll("\\s+", "");
            // buyer 이름이랑 동일하면 스킵
            if (res != null && res.buyer != null && res.buyer.name != null && compact.equals(res.buyer.name.replaceAll("\\s+","")))
                continue;

            boolean corpHint =
                    line.contains("주식회사") || line.contains("유한회사") || line.contains("농업회사법인") ||
                    line.contains("회사법인") || line.contains("(주)") || line.contains("㈜") || line.contains("법인");

            if (corpHint && compact.length() >= 2 && compact.length() <= 40) return line;

            // 힌트 없어도, 한글/영문 조합으로 “상호”처럼 보이면
            if (compact.length() >= 2 && compact.length() <= 30
                    && !compact.matches(".*\\d.*")
                    && compact.matches(".*[가-힣A-Za-z].*")) {
                return line;
            }
        }
        return null;
    }

    private boolean isGoodNameCandidate(String s) {
        if (s == null) return false;
        String v = s.replaceAll("\\s{2,}", " ").trim();
        if (v.length() < 2) return false;
        if (v.matches("^[0-9,]+$")) return false;
        if (v.contains("￦")) return false;
        // 너무 주소처럼 보이면 제외
        if (v.matches(".*(로|길|동|번지|층|호|시|군|구|읍|면).*") && v.length() > 12) return false;
        // 라벨/문서타이틀 제외
        if (v.matches(".*(거래명세표|공급가액|세액|합계|전미수|미수금).*")) return false;
        return true;
    }
    
    /**
     * ✅ FIX 핵심:
     * - Document AI FormField boundingPoly가 normalizedVertices가 없고 vertices(px)만 있는 경우가 많음
     * - 기존 layoutCenterX()가 normalizedVertices가 없으면 0.5로 고정 -> 모든 필드가 right로 몰림
     * - 그래서 buyer는 채워지고 supplier(left)는 비는 현상이 발생
     *
     * 해결:
     * - page.getDimension().getWidth()를 사용해서 vertices(px)도 0~1로 normalize 해서 centerX 계산
     * - 추가로, 좌/우가 뒤집힌 템플릿/몰림 케이스를 보정(스왑/폴백)
     */
    private boolean tryParsePartiesFromFormFields(Document doc, TransactionStatementResult res) {
        if (doc == null || doc.getPagesCount() == 0) return false;

        Map<String, String> left = new HashMap<>();
        Map<String, String> right = new HashMap<>();
        boolean foundAny = false;

        for (Document.Page page : doc.getPagesList()) {
            float pageWidth = 0f;
            if (page.hasDimension()) pageWidth = page.getDimension().getWidth();

            for (Document.Page.FormField ff : page.getFormFieldsList()) {
                String key = layoutText(doc, ff.getFieldName());
                String val = layoutText(doc, ff.getFieldValue());

                key = safeTrim(key);
                val = safeTrim(val);
                if (key.isEmpty() || val.isEmpty()) continue;

                String normKey = key.replaceAll("\\s+", "");
                String mapped = mapPartyKey(normKey);
                if (mapped == null) continue;

                float cx = layoutCenterX(ff.getFieldName(), pageWidth);
                Map<String, String> target = (cx < 0.5f) ? left : right;

                // 여러 줄/중복 키는 이어붙임
                target.put(mapped, mergeKeepSpace(target.get(mapped), val));
                foundAny = true;

                if (DEBUG) {
                    System.out.println("[TS-FF] cx=" + cx + " side=" + (cx < 0.5f ? "L" : "R")
                            + " key=" + normKey + " -> " + mapped + " val=" + val);
                }
            }
        }

        if (!foundAny) return false;

        // bizNo는 기존 regex가 더 안정적이라 유지
        List<String> bizNos = findAll(res.normalizedText, P_BIZNO);
        if (bizNos.size() >= 1) res.supplier.bizNo = bizNos.get(0);
        if (bizNos.size() >= 2) res.buyer.bizNo = bizNos.get(1);

        applyPartyMap(res.supplier, left);
        applyPartyMap(res.buyer, right);

        // ✅ 보정 1) supplier가 비고 buyer만 찼는데, 사실 right로 몰린 케이스 (centerX 실패/양식 한쪽 몰림)
        boolean supplierEmpty = (res.supplier.name == null && res.supplier.ceo == null && res.supplier.address == null
                && res.supplier.bizType == null && res.supplier.bizItem == null);
        boolean buyerFilled = (res.buyer.name != null || res.buyer.ceo != null || res.buyer.address != null
                || res.buyer.bizType != null || res.buyer.bizItem != null);

        if (supplierEmpty && buyerFilled && left.isEmpty() && !right.isEmpty()) {
            // right에만 쌓였으면 supplier도 right로 채워보기
            applyPartyMap(res.supplier, right);
            // buyer는 그대로 두되, 최소 supplier라도 살리기
        }

        // ✅ 보정 2) 좌/우가 반대로 나온 템플릿 대응 (left/right 둘다 있는데 supplier만 비고 buyer만 찬 경우)
        supplierEmpty = (res.supplier.name == null && res.supplier.ceo == null && res.supplier.address == null
                && res.supplier.bizType == null && res.supplier.bizItem == null);
        buyerFilled = (res.buyer.name != null || res.buyer.ceo != null || res.buyer.address != null
                || res.buyer.bizType != null || res.buyer.bizItem != null);

        if (supplierEmpty && buyerFilled && !left.isEmpty() && !right.isEmpty()) {
            Party tmp = res.supplier;
            res.supplier = res.buyer;
            res.buyer = tmp;
        }

        // 최소한 상호/성명 둘 중 하나라도 잡혀야 “성공”으로 간주
        return (res.supplier.name != null || res.supplier.ceo != null
                || res.buyer.name != null || res.buyer.ceo != null);
    }

    private String mapPartyKey(String k) {
        if (k == null) return null;

        // 공백 제거 + 흔한 OCR 기호 제거
        k = k.replaceAll("\\s+", "")
             .replace("：", ":")
             .replace("|", "");

        // ✅ 상호: "상호", "상호명", "상" (한 글자만 찍히는 케이스)
        if (k.contains("상호") || k.contains("상호명") || k.equals("상")) return "name";

        // ✅ 대표/성명: "성명", "성", "대표", "대표자"
        if (k.contains("대표자") || k.equals("대표") || k.contains("대표") || k.contains("성명") || k.equals("성")) return "ceo";

        // ✅ 주소/업태/종목
        if (k.contains("주소") || k.contains("주 소")) return "address";
        if (k.contains("업태")) return "bizType";
        if (k.contains("종목")) return "bizItem";

        return null;
    }

    private void applyPartyMap(Party p, Map<String, String> m) {
        if (p == null || m == null) return;
        if (p.name == null)    p.name = cleanPartyText(m.get("name"));
        if (p.ceo == null)     p.ceo = cleanPartyText(m.get("ceo"));
        if (p.address == null) p.address = cleanPartyText(m.get("address"));
        if (p.bizType == null) p.bizType = cleanPartyText(m.get("bizType"));
        if (p.bizItem == null) p.bizItem = cleanPartyText(m.get("bizItem"));
    }

    private String mergeKeepSpace(String a, String b) {
        a = safeTrim(a); b = safeTrim(b);
        if (a.isEmpty()) return b;
        if (b.isEmpty()) return a;
        if (a.contains(b)) return a;
        return (a + " " + b).replaceAll("\\s{2,}", " ").trim();
    }

    private String layoutText(Document doc, Document.Page.Layout layout) {
        if (layout == null || !layout.hasTextAnchor()) return "";
        return textByAnchor(doc, layout.getTextAnchor());
    }

    private String textByAnchor(Document doc, Document.TextAnchor anchor) {
        if (doc == null || anchor == null) return "";
        String full = doc.getText();
        StringBuilder sb = new StringBuilder();
        for (Document.TextAnchor.TextSegment seg : anchor.getTextSegmentsList()) {
            int s = (int) seg.getStartIndex();
            int e = (int) seg.getEndIndex();
            if (s < 0) s = 0;
            if (e > full.length()) e = full.length();
            if (s < e) sb.append(full, s, e);
        }
        return sb.toString();
    }

    // ✅ FIX: normalizedVertices 없을 때 vertices(px) + pageWidth로 centerX 계산
    private float layoutCenterX(Document.Page.Layout layout, float pageWidth) {
        if (layout == null || !layout.hasBoundingPoly()) return 0.5f;

        BoundingPoly bp = layout.getBoundingPoly();

        // 1) normalizedVertices (0~1)
        if (bp.getNormalizedVerticesCount() > 0) {
            float min = 1f, max = 0f;
            for (NormalizedVertex v : bp.getNormalizedVerticesList()) {
                float x = v.getX();
                min = Math.min(min, x);
                max = Math.max(max, x);
            }
            return (min + max) / 2f;
        }

        // 2) vertices (px) -> normalize with pageWidth
        if (bp.getVerticesCount() > 0 && pageWidth > 0) {
            float min = Float.MAX_VALUE, max = 0f;
            for (Vertex v : bp.getVerticesList()) {
                float x = v.getX();
                min = Math.min(min, x);
                max = Math.max(max, x);
            }
            float cxPx = (min + max) / 2f;
            float cx = cxPx / pageWidth;

            if (cx < 0f) cx = 0f;
            if (cx > 1f) cx = 1f;
            return cx;
        }

        return 0.5f;
    }

    private List<StatementItem> parseItemsFromTables(Document doc) {
        if (doc == null || doc.getPagesCount() == 0) return null;

        List<StatementItem> out = new ArrayList<>();

        for (Document.Page page : doc.getPagesList()) {
            for (Document.Page.Table table : page.getTablesList()) {

                // 1) 이 테이블이 품목 테이블인지 확인(헤더에 품목/단위/수량/단가/금액/세액 중 2개 이상)
                List<String> headers = new ArrayList<>();
                if (table.getHeaderRowsCount() > 0) {
                    Document.Page.Table.TableRow hr = table.getHeaderRows(0);
                    for (Document.Page.Table.TableCell c : hr.getCellsList()) {
                        headers.add(safeTrim(layoutText(doc, c.getLayout())).replaceAll("\\s+", ""));
                    }
                }
                int hit = 0;
                for (String h : headers) {
                    if (h.contains("품목") || h.contains("규격")) hit++;
                    if (h.contains("단위")) hit++;
                    if (h.contains("수량")) hit++;
                    if (h.contains("단가")) hit++;
                    if (h.contains("금액")) hit++;
                    if (h.contains("세액")) hit++;
                }
                if (hit < 2) continue; // 품목 테이블 아니면 스킵

                // 2) 바디 로우 파싱
                for (Document.Page.Table.TableRow row : table.getBodyRowsList()) {
                    List<String> cells = new ArrayList<>();
                    for (Document.Page.Table.TableCell cell : row.getCellsList()) {
                        cells.add(safeTrim(layoutText(doc, cell.getLayout())));
                    }

                    String joined = String.join(" ", cells).replaceAll("\\s{2,}", " ").trim();
                    if (joined.isEmpty()) continue;
                    if (joined.contains("이하여백")) break;

                    // 거래명세표 컬럼 순서가 거의 고정: [품목, 단위, 수량, 단가, 금액, 세액]
                    // 셀 개수가 다르면 최대한 방어적으로 매핑
                    StatementItem it = new StatementItem();

                    it.name = (cells.size() > 0) ? cleanItemName(cells.get(0)) : null;
                    it.unit = (cells.size() > 1) ? canonicalUnit(cells.get(1)) : null;
                    it.qty  = (cells.size() > 2) ? toDoubleSafe(cells.get(2)) : null;
                    it.unitPrice = (cells.size() > 3) ? toIntSafe(cells.get(3)) : null;
                    it.supplyAmt = (cells.size() > 4) ? toIntSafe(cells.get(4)) : null;
                    it.taxAmt    = (cells.size() > 5) ? toIntSafe(cells.get(5)) : null;

                    // 단위/수량이 비는 케이스 보정
                    if (it.unit == null) it.unit = inferUnitFromName(it.name);
                    if (it.qty == null) it.qty = 1.0;

                    // ✅ 검증 통과한 행만 추가
                    if (isValidItemName(it.name) && validateItemRow(it)) {
                        out.add(it);
                    }
                }

                if (!out.isEmpty()) return out; // 첫 매칭 테이블에서 성공하면 끝
            }
        }
        return out;
    }

    private boolean validateItemRow(StatementItem it) {
        if (it == null) return false;
        if (it.name == null || it.name.trim().length() < 2) return false;
        if (it.unitPrice == null || it.supplyAmt == null) return false;

        double qty = (it.qty == null || it.qty <= 0) ? 1.0 : it.qty;
        long expected = Math.round(qty * it.unitPrice);

        // 금액 범위 방어(너무 작은 건 잡음일 확률 큼)
        if (it.supplyAmt < 100 && expected > 1000) return false;

        long tol = Math.max(2000L, Math.round(expected * 0.02)); // 2% or 2000원

        if (it.taxAmt != null) {
            long sum = (long) it.supplyAmt + (long) it.taxAmt;
            // (공급+세액) ≈ (수량*단가)
            if (Math.abs(sum - expected) <= tol) return true;

            // 혹시 단가가 공급가 단가인 케이스면 supply ≈ expected 도 허용
            if (Math.abs((long) it.supplyAmt - expected) <= tol) return true;

            return false;
        } else {
            // 세액이 없으면 supply ≈ expected
            if (Math.abs((long) it.supplyAmt - expected) <= tol) return true;

            // 단가가 VAT 포함이면 supply ≈ expected/1.1
            long net = Math.round(expected / 1.1);
            if (Math.abs((long) it.supplyAmt - net) <= tol) return true;

            return false;
        }
    }

    // =========================================================
    // normalize
    // =========================================================
    private String normalize(String raw) {
        if (raw == null) return "";

        String t = raw
                .replaceAll("[\\t\\x0B\\f\\r]+", " ")
                .replace("₩", "￦")
                // ✅ W가 숫자 앞에 붙는 경우만 원화로 보정 (W185,184 / W1,607,100)
                .replaceAll("(?i)(?<=^|\\s)W(?=\\d)", "￦")
                .replaceAll("(?<=\\d)\\.(?=\\d{3}\\b)", ",")
                .replaceAll("[ ]{2,}", " ")
                .trim();

        // 단위 OCR 깨짐 보정
        t = t.replaceAll("박\\s*스", "박스");
        t = t.replaceAll("k\\s*g", "kg");
        t = t.replaceAll("K\\s*G", "KG");
        t = t.replaceAll("E\\s*A", "EA");
        t = t.replaceAll("S\\s*E\\s*T", "SET");
        t = t.replaceAll("븡", "봉");

        // 핵심 구간 앞 줄바꿈 유도
        t = t.replaceAll("(?=거\\s*래\\s*명\\s*세\\s*표)", "\n");
        t = t.replaceAll("(?=전\\s*미\\s*수|미\\s*수\\s*금|합\\s*계|세\\s*액)", "\n");
        t = t.replaceAll("(?=공\\s*급\\s*가\\s*액|공기\\s*급액|공\\s*기\\s*급\\s*액|공\\s*가\\s*\\|\\s*급\\s*액)", "\n");

        // 라벨 앞 줄바꿈
        t = t.replaceAll("(?=상\\s*호)", "\n");
        t = t.replaceAll("(?=주\\s*소|주소)", "\n");
        t = t.replaceAll("(?=업\\s*태)", "\n");
        t = t.replaceAll("(?=종\\s*목|종목)", "\n");

        // 단위가 품목명 뒤에 붙는 케이스 분리
        t = t.replaceAll("(?<=[가-힣A-Za-z0-9\\)])(kg|KG|박스|봉|팩|EA|개|통|캔|병|줄|포|롤|세트|SET|묶음|판)\\b", " $1");

        return t.replaceAll("[ ]{2,}", " ").trim();
    }

    // =========================================================
    // header
    // =========================================================
    private void parseHeader(String text, TransactionStatementResult res) {
        Pattern p = Pattern.compile("일\\s*자\\s*(20\\d{2})\\s*년\\s*(\\d{1,2})\\s*월\\s*(\\d{1,2})\\s*일\\s*([0-9\\-]{3,})?");
        Matcher m = p.matcher(text);
        if (m.find()) {
            res.issueDate = m.group(1) + "-" + pad2(m.group(2)) + "-" + pad2(m.group(3));
            String docNo = m.group(4);
            if (docNo != null && !docNo.trim().isEmpty()) res.docNo = docNo.trim();
            return;
        }

        Pattern p2 = Pattern.compile("(20\\d{2})\\s*년\\s*(\\d{1,2})\\s*월\\s*(\\d{1,2})\\s*일");
        Matcher m2 = p2.matcher(text);
        if (m2.find()) {
            res.issueDate = m2.group(1) + "-" + pad2(m2.group(2)) + "-" + pad2(m2.group(3));
        }
    }

    private String pad2(String s) {
        if (s == null) return "00";
        s = s.replaceAll("[^0-9]", "");
        if (s.length() == 1) return "0" + s;
        return s;
    }

    private Integer moneyAfterLabelInSameLine(String line, Pattern label) {
        if (line == null) return null;
        Matcher lm = label.matcher(line);
        if (!lm.find()) return null;

        String after = line.substring(lm.end());
        Matcher m = Pattern.compile("(?:￦\\s*)?(\\d{1,3}(?:,\\d{3})+)").matcher(after);
        if (m.find()) return toIntSafe(m.group(1));
        return null;
    }

    // =========================================================
    // parties
    // =========================================================
    private void parseParties(String text, TransactionStatementResult res) {
        List<String> bizNos = findAll(text, P_BIZNO);
        if (bizNos.size() >= 1) res.supplier.bizNo = bizNos.get(0);
        if (bizNos.size() >= 2) res.buyer.bizNo = bizNos.get(1);

        String[] lines = text.split("\\n");

        int idxItemsHdr = indexOfFirst(lines, 0,
                Pattern.compile(".*품\\s*목.*\\(\\s*규\\s*격\\s*\\).*|.*품\\s*목.*규\\s*격.*"));

        // ✅ “두 번째 사업자번호가 등장한 줄”로 buyer 시작점을 잡는다 (마커 OCR 실패 대비)
        int idxBuyerBizLine = -1;
        if (res.buyer.bizNo != null) {
            Pattern pBiz2 = Pattern.compile(".*" + Pattern.quote(res.buyer.bizNo) + ".*");
            idxBuyerBizLine = indexOfFirst(lines, 0, pBiz2);
        }

        List<String> supplierLines;
        List<String> buyerLines;

        if (idxBuyerBizLine >= 0) {
            supplierLines = slice(lines, 0, idxBuyerBizLine);
            int end = (idxItemsHdr > idxBuyerBizLine) ? idxItemsHdr : lines.length;
            buyerLines = slice(lines, idxBuyerBizLine, end);
        } else {
            int end = (idxItemsHdr > 0) ? idxItemsHdr : lines.length;
            supplierLines = slice(lines, 0, end);
            buyerLines = slice(lines, 0, end);
        }

        fillPartyFromLines(res.supplier, supplierLines);
        fillPartyFromLines(res.buyer, buyerLines);

        refinePartyHeuristics(res.supplier, supplierLines, true);
        refinePartyHeuristics(res.buyer, buyerLines, false);

        res.supplier.name    = cleanPartyText(res.supplier.name);
        res.supplier.ceo     = cleanPartyText(res.supplier.ceo);
        res.supplier.address = cleanPartyText(res.supplier.address);
        res.supplier.bizType = cleanPartyText(res.supplier.bizType);
        res.supplier.bizItem = cleanPartyText(res.supplier.bizItem);

        res.buyer.name    = cleanPartyText(res.buyer.name);
        res.buyer.ceo     = cleanPartyText(res.buyer.ceo);
        res.buyer.address = cleanPartyText(res.buyer.address);
        res.buyer.bizType = cleanPartyText(res.buyer.bizType);
        res.buyer.bizItem = cleanPartyText(res.buyer.bizItem);

        if (res.buyer.bizItem == null && res.buyer.bizType != null) {
            String hint = findFirstLineContainsAny(buyerLines, "위탁", "급식");
            if (hint != null) res.buyer.bizItem = hint.replaceAll("^\\s*[가-힣]?\\s*", "").trim();
        }
    }

    private void fillPartyFromLines(Party p, List<String> lines) {
        if (p == null || lines == null) return;

        String current = null;
        StringBuilder buf = new StringBuilder();
        Map<String, String> fields = new HashMap<>();

        for (String raw : lines) {
            String line = safeTrim(raw);
            if (line.isEmpty()) continue;

            if (P_PARTY_NOISE_LINE.matcher(line).find()) continue;
            if (isNoiseTokenLine(line)) continue;

            String nextField = null;
            if (P_LABEL_SANGHO.matcher(line).find()) nextField = "name";
            else if (P_LABEL_SEONGMYEONG.matcher(line).find()) nextField = "ceo";
            else if (P_LABEL_SEONG.matcher(line).find()) nextField = "ceo";
            else if (P_LABEL_JUSO.matcher(line).find()) nextField = "address";
            else if (P_LABEL_UPTAE.matcher(line).find()) nextField = "bizType";
            else if (P_LABEL_JONGMOK.matcher(line).find()) nextField = "bizItem";

            if (nextField != null) {
                flushField(fields, current, buf);
                current = nextField;
                buf.setLength(0);

                String tail = stripLabel(line);
                if (!tail.isEmpty()) buf.append(tail);
                continue;
            }

            if ("ceo".equals(current) && P_LABEL_MYEONG.matcher(line).find()) continue;

            if (current != null) {
                if (buf.length() > 0) buf.append(" ");
                buf.append(line);
            }
        }

        flushField(fields, current, buf);

        p.name = fields.get("name");
        p.ceo = fields.get("ceo");
        p.address = fields.get("address");
        p.bizType = fields.get("bizType");
        p.bizItem = fields.get("bizItem");
    }

    private void refinePartyHeuristics(Party p, List<String> lines, boolean isSupplier) {
        if (p == null || lines == null || lines.isEmpty()) return;

        String joined = String.join(" ", lines).replaceAll("\\s{2,}", " ").trim();

        // 1) CEO fallback: "... 성 유인식" / "... 성명 최희영"
        if (p.ceo == null) {
            Matcher m = Pattern.compile("(성\\s*명|성)\\s*([가-힣]{2,5})").matcher(joined);
            if (m.find()) p.ceo = m.group(2);
        }

        // 2) 회사명 prefix 후보: "농업회사법인주식회사 씨" 같은 라벨 없는 본문에서 뽑기
        String prefixName = null;
        for (String raw : lines) {
            String s = safeTrim(raw);
            if (s.isEmpty()) continue;
            if (P_PARTY_NOISE_LINE.matcher(s).find()) continue;
            if (isNoiseTokenLine(s)) continue;

            // 라벨 줄은 제외
            if (P_LABEL_SANGHO.matcher(s).find()
                    || P_LABEL_JUSO.matcher(s).find()
                    || P_LABEL_SEONG.matcher(s).find()
                    || P_LABEL_SEONGMYEONG.matcher(s).find()
                    || P_LABEL_UPTAE.matcher(s).find()
                    || P_LABEL_JONGMOK.matcher(s).find()) {
                continue;
            }

            // "주식회사/회사법인" 포함 + 너무 숫자 투성이 아닌 라인
            if (s.contains("주식회사") || s.contains("회사법인")) {
                // " ... 성 유인식" 앞은 회사명일 가능성 높음
                String cut = s;
                int idx = cut.indexOf("성");
                if (idx > 0) cut = cut.substring(0, idx).trim();
                cut = cut.replaceAll("(공번호|등록|급)\\s*", "").trim();
                if (cut.length() >= 2) {
                    prefixName = cut;
                    break;
                }
            }
        }

        // 3) 상호 라벨로 잡힌 name이 짧으면 prefix + name 결합
        // ex) prefix="농업회사법인주식회사 씨", tail="엔푸드" => "농업회사법인주식회사 씨엔푸드"
        if (p.name != null && prefixName != null) {
            String tail = p.name.trim();
            String pre = prefixName.trim();

            // 이미 prefix가 tail을 포함하면 그대로
            if (!pre.contains(tail) && !tail.contains(pre)) {
                // "씨" + "엔" => "씨엔"로 붙이기
                if (pre.endsWith("씨") && tail.startsWith("엔")) {
                    p.name = pre + tail; // 붙임
                } else {
                    p.name = pre + " " + tail;
                }
            }
        } else if (p.name == null && prefixName != null) {
            // 라벨 상호가 아예 안 잡힌 경우
            p.name = prefixName;
        }

        // 4) 괄호/찌꺼기 정리 (채움) 같은 경우)
        if (p.name != null) {
            p.name = p.name.replaceAll("\\s{2,}", " ").trim();
            // 단독 ')' 같은 것만 정리 (완전 제거는 위험해서 최소만)
            p.name = p.name.replaceAll("^\\)+", "").trim();
        }
    }

    private void flushField(Map<String, String> fields, String current, StringBuilder buf) {
        if (current == null) return;
        String v = safeTrim(buf.toString());
        if (!v.isEmpty()) fields.put(current, v);
    }

    private String stripLabel(String line) {
        String s = line;
        s = s.replaceFirst("^\\s*상\\s*호\\s*[:：]?", "").trim();
        s = s.replaceFirst("^\\s*성\\s*명\\s*[:：]?", "").trim();
        s = s.replaceFirst("^\\s*성\\s*[:：]?", "").trim();
        s = s.replaceFirst("^\\s*(주\\s*소|주소)\\s*[:：]?", "").trim();
        s = s.replaceFirst("^\\s*업\\s*태\\s*[:：]?", "").trim();
        s = s.replaceFirst("^\\s*(종\\s*목|종목)\\s*[:：]?", "").trim();
        return s;
    }

    private boolean isNoiseTokenLine(String line) {
        String s = line.replaceAll("\\s+", "").trim();
        if (s.isEmpty()) return true;
        if (s.length() == 1 && PARTY_NOISE_TOKENS.contains(s)) return true;
        if ("명".equals(s) || "급".equals(s) || "자".equals(s) || "는".equals(s)) return true;
        if (s.startsWith("(") && s.endsWith(")")) return true;
        return false;
    }

    private String cleanPartyText(String s) {
        if (s == null) return null;
        String v = s.replaceAll("[\\n\\r]+", " ").replaceAll("\\s{2,}", " ").trim();
        v = v.replaceAll("\\(\\s*1\\s*/\\s*1\\s*\\)", " ");
        v = v.replaceAll("\\(공급받는자\\s*보관용\\)", " ");
        v = v.replaceAll("\\b거래명세표\\b", " ");
        v = v.replaceAll("\\s{2,}", " ").trim();
        return v.isEmpty() ? null : v;
    }

    // =========================================================
    // items
    // =========================================================
    private List<StatementItem> parseItems(String text) {
        List<StatementItem> items = new ArrayList<>();
        String[] lines = text.split("\\n");

        boolean inTable = false;
        boolean seenItemHeader = false;

        boolean headerUnitSeen = false;
        boolean headerQtySeen = false;
        boolean headerAmtSeen = false;
        int headerStartLine = -1;

        List<String> nameLines = new ArrayList<>();
        String unit = null;
        List<String> nums = new ArrayList<>();

        for (int i = 0; i < lines.length; i++) {
            String line = safeTrim(lines[i]);
            if (line.isEmpty()) continue;

            if (!inTable) {
                if (line.matches(".*품\\s*목.*\\(\\s*규\\s*격\\s*\\).*") || line.matches(".*품\\s*목.*규\\s*격.*")) {
                    seenItemHeader = true;
                    headerUnitSeen = headerQtySeen = headerAmtSeen = false;
                    headerStartLine = i;
                    continue;
                }

                if (seenItemHeader) {
                    if (line.matches(".*단\\s*위.*")) headerUnitSeen = true;
                    if (line.matches(".*수\\s*량.*")) headerQtySeen = true;
                    if (line.matches(".*금\\s*액.*")) headerAmtSeen = true;

                    if (headerUnitSeen && (headerQtySeen || headerAmtSeen)) {
                        inTable = true;
                        seenItemHeader = false;
                        resetRow(nameLines, nums);
                        unit = null;
                        if (DEBUG) System.out.println("=== ITEMS TABLE START @" + i + " ===");
                        continue;
                    }

                    if (headerStartLine >= 0 && (i - headerStartLine) > 25) seenItemHeader = false;
                }
                continue;
            }

            if (isTableHeaderNoise(line)) continue;

            if (line.matches(".*이\\s*하\\s*여\\s*백.*")) {
                if (DEBUG) System.out.println("  -> SKIP FILLER: " + line);
                continue;
            }

            if (isTotalsStartLine(line)) {
                if (DEBUG) System.out.println("=== ITEMS TABLE END @" + i + " (" + line + ") ===");
                flushRowIfPossible(items, nameLines, unit, nums, "END_FLUSH");
                inTable = false;
                break;
            }

            if (DEBUG) System.out.println("[TBL] " + line);

            StatementItem inline = tryParseInlineRow(line);
            if (inline != null) {
                if (DEBUG) System.out.println("  -> INLINE OK: " + inline);
                items.add(inline);
                resetRow(nameLines, nums);
                unit = null;
                continue;
            }

            if (unit == null) {
                if (isUnitOnlyLine(line)) {
                    unit = canonicalUnit(line);
                    continue;
                }

                if (isJunkNameLine(line)) continue;

                nameLines.add(line);

                String inferred = inferUnitFromName(joinName(nameLines));
                if (inferred != null) unit = inferred;

                if (joinName(nameLines).length() > 280) nameLines.clear();
                continue;
            }

            List<String> found = extractNumbers(line);
            if (!found.isEmpty()) nums.addAll(found);

            // ✅ row 확정: 기본 3개, BUT 2개면 qty=1 보정 (수량 OCR 누락 케이스)
            if (nums.size() >= 3 || nums.size() == 2) {
                StatementItem it = buildItem(joinName(nameLines), unit, nums);
                if (it != null && isValidItemName(it.name)) items.add(it);
                resetRow(nameLines, nums);
                unit = null;
            }
        }

        Set<String> seen = new HashSet<>();
        items.removeIf(i -> !seen.add((i.name == null ? "" : i.name) + "|" + i.supplyAmt + "|" + i.taxAmt));

        return items;
    }

    private boolean isTotalsStartLine(String line) {
        boolean hasLabel =
                P_TOTAL_LABEL_SUPPLY.matcher(line).find()
                        || P_TOTAL_LABEL_TAX.matcher(line).find()
                        || P_TOTAL_LABEL_GRAND.matcher(line).find()
                        || P_TOTAL_LABEL_PREV.matcher(line).find()
                        || P_TOTAL_LABEL_BAL.matcher(line).find();

        if (!hasLabel) {
            if (line.contains("￦") && line.matches(".*\\d{1,3}(?:,\\d{3})+.*")) return true;
            return false;
        }

        boolean hasMoney =
                line.contains("￦") ||
                        line.matches(".*\\d{1,3}(?:,\\d{3})+.*");

        return hasMoney;
    }

    private void resetRow(List<String> nameLines, List<String> nums) {
        nameLines.clear();
        nums.clear();
    }

    private void flushRowIfPossible(List<StatementItem> items, List<String> nameLines, String unit, List<String> nums, String reason) {
        if (unit == null) return;
        if (!(nums.size() >= 3 || nums.size() == 2)) return;
        StatementItem it = buildItem(joinName(nameLines), unit, nums);
        if (it != null && isValidItemName(it.name)) items.add(it);
    }

    private StatementItem buildItem(String nameRaw, String unitRaw, List<String> nums) {
        String name = cleanItemName(nameRaw);
        String unit = canonicalUnit(unitRaw);
        if (unit == null) unit = inferUnitFromName(name);
        if (unit == null) unit = "EA";

        if (name == null || name.isEmpty()) return null;

        StatementItem it = new StatementItem();
        it.name = name;
        it.unit = unit;

        // ✅ nums 해석:
        //  - 3개: qty, unitPrice, supplyAmt
        //  - 2개: (qty 누락) => qty=1, unitPrice=nums[0], supplyAmt=nums[1]
        if (nums.size() >= 3) {
            it.qty = toDoubleSafe(nums.get(0));
            it.unitPrice = toIntSafe(nums.get(1));
            it.supplyAmt = toIntSafe(nums.get(2));
            if (nums.size() >= 4) it.taxAmt = toIntSafe(nums.get(3));
        } else if (nums.size() == 2) {
            it.qty = 1.0;
            it.unitPrice = toIntSafe(nums.get(0));
            it.supplyAmt = toIntSafe(nums.get(1));
        } else {
            return null;
        }

        return it;
    }

    private StatementItem tryParseInlineRow(String line) {
        Matcher m = P_UNIT_FOLLOWED_BY_QTY.matcher(line);
        int lastStart = -1;
        String lastUnit = null;

        while (m.find()) {
            lastStart = m.start();
            lastUnit = m.group(1);
        }
        if (lastStart < 0 || lastUnit == null) return null;

        String namePart = line.substring(0, lastStart).trim();
        String restPart = line.substring(lastStart).trim();

        List<String> nums = extractNumbers(restPart);
        if (!(nums.size() >= 3 || nums.size() == 2)) return null;

        StatementItem it = buildItem(namePart, lastUnit, nums);
        if (it == null) return null;
        if (!isValidItemName(it.name)) return null;
        return it;
    }

    private boolean isTableHeaderNoise(String line) {
        if (line.matches(".*(품\\s*목|단\\s*위|수\\s*량|단\\s*가|금\\s*액|세\\s*액).*")) return true;
        if (line.matches("=+.*")) return true;
        if (line.matches("[-_]{3,}.*")) return true;
        return false;
    }

    private boolean isUnitOnlyLine(String line) {
        String s = line.replaceAll("\\s+", "").trim();
        if (s.isEmpty()) return false;
        if (UNIT_SET.contains(s)) return true;
        if (s.contains("|")) return false;
        if (s.startsWith("(") && s.endsWith(")")) return false;
        return false;
    }

    private String canonicalUnit(String unit) {
        if (unit == null) return null;
        String u = unit.replaceAll("\\s+", "").trim();
        if ("kg".equalsIgnoreCase(u)) return "kg";
        if ("ea".equalsIgnoreCase(u)) return "EA";
        if ("set".equalsIgnoreCase(u)) return "SET";
        if (UNIT_SET.contains(u)) return u;
        return null;
    }

    private String inferUnitFromName(String name) {
        if (name == null) return null;
        for (String u : UNIT_SET) {
            if (name.contains("(" + u + ")")) return canonicalUnit(u);
        }
        for (String u : UNIT_SET) {
            if (name.matches(".*\\b" + Pattern.quote(u) + "\\b\\s*$")) return canonicalUnit(u);
        }
        if (name.matches(".*\\bkg\\b.*")) return "kg";
        return null;
    }

    private boolean isJunkNameLine(String line) {
        if (line.contains("|")) return true;
        String s = line.replaceAll("\\s+", "");
        if (s.length() <= 2) return true;
        int hit = 0;
        for (String u : UNIT_SET) if (s.contains(u)) hit++;
        return (hit >= 2 && s.length() < 20);
    }

    private String joinName(List<String> nameLines) {
        if (nameLines == null || nameLines.isEmpty()) return "";
        return String.join(" ", nameLines).replaceAll("\\s{2,}", " ").trim();
    }

    private String cleanItemName(String name) {
        if (name == null) return null;
        String n = name.replaceAll("[\\n\\r]+", " ").replaceAll("\\s{2,}", " ").trim();
        n = n.replaceAll("[|]+", " ").replaceAll("\\s{2,}", " ").trim();
        n = n.replaceAll("(?i)\\b(공급가액|전미수|미수금|합계|세액)\\b.*$", "").trim();
        return n;
    }

    private boolean isValidItemName(String name) {
        if (name == null) return false;
        String n = name.trim();
        if (n.length() < 2) return false;
        if (n.matches("^[0-9,]+$")) return false;
        if (n.matches(".*(공\\s*급\\s*가\\s*액|전\\s*미\\s*수|미\\s*수\\s*금|합\\s*계|세\\s*액|거\\s*래\\s*명\\s*세\\s*표).*")) return false;
        return true;
    }

    private List<String> extractNumbers(String s) {
        List<String> out = new ArrayList<>();
        Matcher m = P_NUM.matcher(s);
        while (m.find()) out.add(m.group());
        return out;
    }

    // =========================================================
    // totals (강화)
    // =========================================================
    private void parseTotals(String text, TransactionStatementResult res) {
        String tail = tailWindow(text, 2600);
        String[] lines = tail.split("\\n");

        // totals 구간을 좀 더 안전하게: '전미수'/'미수금'/'공급가액'/'합계' 또는 '￦' 처음 등장으로 시작
        int idxStart = -1;
        for (int i = 0; i < lines.length; i++) {
            String s = lines[i] == null ? "" : lines[i];
            if (P_TOTAL_LABEL_PREV.matcher(s).find()
                    || P_TOTAL_LABEL_BAL.matcher(s).find()
                    || P_TOTAL_LABEL_SUPPLY.matcher(s).find()
                    || P_TOTAL_LABEL_GRAND.matcher(s).find()
                    || s.contains("￦")) {
                idxStart = i;
                break;
            }
        }
        if (idxStart < 0) return;

        int start = Math.max(0, idxStart - 10);
        String[] L = Arrays.copyOfRange(lines, start, lines.length);

        // 1) 라벨 기반 (같은 줄 우선)
        res.totals.balance     = pickMoneySameOrNearLabel(L, P_TOTAL_LABEL_BAL, 3);   // ✅ 같은 줄 우선
        res.totals.prevBalance = pickMoneyBeforeOrNearLabel(L, P_TOTAL_LABEL_PREV, 4);

        res.totals.supplyTotal = pickMoneySameOrNearLabel(L, P_TOTAL_LABEL_SUPPLY, 4);
        res.totals.taxTotal    = pickMoneySameOrNearLabel(L, P_TOTAL_LABEL_TAX, 2);
        res.totals.grandTotal  = pickMoneySameOrNearLabel(L, P_TOTAL_LABEL_GRAND, 4);

        // 2) ￦ 금액 리스트 수집
        List<Integer> wonList = new ArrayList<>();
        for (String l : L) {
            if (l == null) continue;
            Matcher m = Pattern.compile("￦\\s*(\\d{1,3}(?:,\\d{3})+)").matcher(l);
            while (m.find()) wonList.add(toIntSafe(m.group(1)));
        }

        // 3) 양식별 강력 매핑
        // - 네 첫 번째 케이스(세액 공란)는 4개가 흔함: 공급, 합계, 전미수, 미수금
        if (wonList.size() == 4) {
            if (res.totals.supplyTotal == null) res.totals.supplyTotal = wonList.get(0);
            if (res.totals.grandTotal == null)  res.totals.grandTotal  = wonList.get(1);
            if (res.totals.prevBalance == null) res.totals.prevBalance = wonList.get(2);
            if (res.totals.balance == null)     res.totals.balance     = wonList.get(3);

            // 세액은 (합계-공급)로 유추 (같으면 0)
            if (res.totals.taxTotal == null && res.totals.supplyTotal != null && res.totals.grandTotal != null) {
                int diff = res.totals.grandTotal - res.totals.supplyTotal;
                res.totals.taxTotal = Math.max(0, diff);
            }
        } else if (wonList.size() >= 5) {
            // 일반 케이스: 공급, 세액, 합계, 전미수, 미수금
            if (res.totals.supplyTotal == null) res.totals.supplyTotal = wonList.get(0);
            if (res.totals.taxTotal == null)    res.totals.taxTotal    = wonList.get(1);
            if (res.totals.grandTotal == null)  res.totals.grandTotal  = wonList.get(2);
            if (res.totals.prevBalance == null) res.totals.prevBalance = wonList.get(3);
            if (res.totals.balance == null)     res.totals.balance     = wonList.get(4);
        }

        // 4) 마지막 보정: 합계 없으면 공급+세액
        if (res.totals.grandTotal == null && res.totals.supplyTotal != null) {
            if (res.totals.taxTotal != null) res.totals.grandTotal = res.totals.supplyTotal + res.totals.taxTotal;
        }
    }

    private Integer pickMoneySameOrNearLabel(String[] lines, Pattern label, int forward) {
        for (int i = 0; i < lines.length; i++) {
            String line = safeTrim(lines[i]);
            if (line.isEmpty()) continue;
            if (!label.matcher(line).find()) continue;

            // ✅ 같은 줄: 라벨 뒤 금액
            Integer after = moneyAfterLabelInSameLine(line, label);
            if (after != null) return after;

            // ✅ 같은 줄: 그냥 첫 금액 (라벨과 같이 찍힌 케이스)
            Integer same = firstMoneyInLine(line);
            if (same != null) return same;

            // 아래 몇 줄에서 찾기
            for (int j = i + 1; j < lines.length && j <= i + forward; j++) {
                String n = safeTrim(lines[j]);
                if (n.isEmpty()) continue;
                Integer v = firstMoneyInLine(n);
                if (v != null) return v;
            }
        }
        return null;
    }

    // 전미수는 라벨이 따로 찍히고 금액은 위줄에 있는 경우가 많아서 "위/아래" 같이 본다
    private Integer pickMoneyBeforeOrNearLabel(String[] lines, Pattern label, int span) {
        for (int i = 0; i < lines.length; i++) {
            String line = safeTrim(lines[i]);
            if (line.isEmpty()) continue;
            if (!label.matcher(line).find()) continue;

            // ✅ 같은 줄 우선
            Integer after = moneyAfterLabelInSameLine(line, label);
            if (after != null) return after;
            Integer same = firstMoneyInLine(line);
            if (same != null) return same;

            // 위로 span 줄
            for (int j = i - 1; j >= 0 && j >= i - span; j--) {
                String p = safeTrim(lines[j]);
                if (p.isEmpty()) continue;
                Integer v = firstMoneyInLine(p);
                if (v != null) return v;
            }

            // 아래로 span 줄
            for (int j = i + 1; j < lines.length && j <= i + span; j++) {
                String n = safeTrim(lines[j]);
                if (n.isEmpty()) continue;
                Integer v = firstMoneyInLine(n);
                if (v != null) return v;
            }
        }
        return null;
    }

    private Integer firstMoneyInLine(String line) {
        if (line == null) return null;
        Matcher m = Pattern.compile("(?:￦\\s*)?(\\d{1,3}(?:,\\d{3})+)").matcher(line);
        if (m.find()) return toIntSafe(m.group(1));
        return null;
    }

    private String tailWindow(String text, int maxChars) {
        if (text == null) return "";
        if (text.length() <= maxChars) return text;
        return text.substring(text.length() - maxChars);
    }

    // =========================================================
    // utils
    // =========================================================
    private Integer toIntSafe(String s) {
        if (s == null) return null;
        try {
            String v = s.replaceAll("[^0-9]", "");
            if (v.isEmpty()) return null;
            return Integer.parseInt(v);
        } catch (Exception e) {
            return null;
        }
    }

    private Double toDoubleSafe(String s) {
        if (s == null) return null;
        try {
            String v = s.replaceAll("[^0-9.]", "");
            if (v.isEmpty()) return null;
            return Double.parseDouble(v);
        } catch (Exception e) {
            return null;
        }
    }

    private List<String> findAll(String text, Pattern p) {
        List<String> out = new ArrayList<>();
        Matcher m = p.matcher(text);
        while (m.find()) out.add(m.group());
        return out;
    }

    private int indexOfFirst(String[] lines, int from, Pattern p) {
        for (int i = Math.max(0, from); i < lines.length; i++) {
            String s = lines[i] == null ? "" : lines[i];
            if (p.matcher(s).find()) return i;
        }
        return -1;
    }

    private List<String> slice(String[] lines, int from, int to) {
        int a = Math.max(0, from);
        int b = Math.min(lines.length, to);
        List<String> out = new ArrayList<>();
        for (int i = a; i < b; i++) out.add(lines[i]);
        return out;
    }

    private String safeTrim(String s) {
        return s == null ? "" : s.trim();
    }

    private String findFirstLineContainsAny(List<String> lines, String... keys) {
        if (lines == null) return null;
        for (String l : lines) {
            String s = safeTrim(l);
            if (s.isEmpty()) continue;
            for (String k : keys) {
                if (s.contains(k)) return s;
            }
        }
        return null;
    }

    // =========================================================
    // DEBUG / DUMP
    // =========================================================
    private void dumpResult(String stage, TransactionStatementResult res) {
        if (!(DEBUG || DUMP)) return;

        System.out.println("\n==============================");
        System.out.println("🧾 TS DUMP STAGE = " + stage);
        System.out.println("==============================");

        System.out.println("issueDate=" + res.issueDate + ", docNo=" + res.docNo);

        dumpParty("SUPPLIER", res.supplier);
        dumpParty("BUYER   ", res.buyer);

        System.out.println("TOTALS: supplyTotal=" + res.totals.supplyTotal
                + ", taxTotal=" + res.totals.taxTotal
                + ", grandTotal=" + res.totals.grandTotal
                + ", prevBalance=" + res.totals.prevBalance
                + ", balance=" + res.totals.balance);

        System.out.println("ITEMS COUNT=" + (res.items == null ? 0 : res.items.size()));
        if (res.items != null) {
            for (int i = 0; i < res.items.size(); i++) {
                System.out.println("  [" + (i + 1) + "] " + res.items.get(i));
            }
        }

        if (DEBUG) {
            System.out.println("\n--- RAW (HEAD 800) ---");
            System.out.println(headWindow(res.rawText, 800));
            System.out.println("\n--- NORMALIZED (HEAD 1200) ---");
            System.out.println(headWindow(res.normalizedText, 1200));
            System.out.println("\n--- NORMALIZED (TAIL 1200) ---");
            System.out.println(tailWindow(res.normalizedText, 1200));
        }

        System.out.println("==============================\n");
    }

    private String headWindow(String text, int maxChars) {
        if (text == null) return "";
        if (text.length() <= maxChars) return text;
        return text.substring(0, maxChars);
    }

    private void dumpParty(String tag, Party p) {
        if (p == null) return;
        System.out.println(tag + " bizNo=" + p.bizNo);
        System.out.println(tag + " name =" + p.name);
        System.out.println(tag + " ceo  =" + p.ceo);
        System.out.println(tag + " addr =" + p.address);
        System.out.println(tag + " type =" + p.bizType);
        System.out.println(tag + " item =" + p.bizItem);
    }
}
