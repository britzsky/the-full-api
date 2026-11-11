package com.example.demo.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;

import org.jsoup.Jsoup;
import org.jsoup.helper.W3CDom;
import org.springframework.core.io.ClassPathResource;
import org.springframework.web.util.HtmlUtils;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder.FontStyle;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;

// 근로계약서 양식 HTML(templates/contract/labor_contract.html)에 조항 목록(DB)과 계약서 1건의 값을 채워 넣는 클래스
// 앱 전자계약 화면 표시에 쓰고, 서명 PDF 생성에도 같은 양식을 쓴다.
public final class ContractHtmlBuilder {

	private static final String TEMPLATE_PATH = "templates/contract/labor_contract.html";
	private static volatile String template;
	// 앱 서명칸에서 보낸 서명 이미지 형식 (PNG data URL)
	private static final java.util.regex.Pattern SIGN_IMAGE_PATTERN = java.util.regex.Pattern
			.compile("data:image/png;base64,[A-Za-z0-9+/=]+");

	// PDF 생성 라이브러리 자체 로그(폰 전용 CSS 무시·폰트 로딩 안내 등)를 콘솔(stderr)에 찍지 않음
	static {
		com.openhtmltopdf.util.XRLog.setLoggingEnabled(false);
	}

	private ContractHtmlBuilder() {
	}

	// 계약서 1건(ContractDetail·AppContractDetail 조회 결과)과 조항 목록
	// JSON(tb_contract_template.clauses)으로 양식 HTML 완성
	public static String build(Map<String, Object> row, String clausesJson) {
		String html = loadTemplate().replace("{{clauses}}", clauseRows(clausesJson));

		html = html.replace("{{name}}", text(row.get("name")));
		html = html.replace("{{rrn}}", text(row.get("rrn")));
		html = html.replace("{{address}}", text(row.get("address")));
		html = html.replace("{{contract_period}}", contractPeriod(row));

		// 제3조·제4조 시간 값 (소수점 끝 0 제거)
		for (String key : new String[] { "day_work_hours", "month_work_hours", "week_work_days", "break_hours",
				"ot_hours", "night_hours", "holiday_hours", "probation_months" }) {
			html = html.replace("{{" + key + "}}", number(row.get(key)));
		}
		html = html.replace("{{start_time}}", text(row.get("start_time")));
		html = html.replace("{{end_time}}", text(row.get("end_time")));
		html = html.replace("{{break_times}}", breakTimes(row));
		html = html.replace("{{job_name}}", text(row.get("job_name")));
		html = html.replace("{{work_place}}", text(row.get("work_place")));

		// 제7조 임금표 금액 (천 단위 콤마, 0·빈 값은 엑셀 양식처럼 '-')
		for (String key : new String[] { "total_pay", "base_pay", "meal_pay", "car_pay", "child_pay",
				"annual_leave_pay", "ot_pay", "holiday_pay", "night_pay" }) {
			html = html.replace("{{" + key + "}}", money(row.get(key)));
		}

		// 작성일자 (yyyy-MM-dd, 비어 있으면 빈칸, 앱 서명 화면에서 날짜를 바꿔 넣을 수 있게 칸마다 id 부여)
		String[] date = text(row.get("contract_dt")).split("-");
		html = html.replace("{{contract_year}}", "<span id=\"cd_y\">" + (date.length == 3 ? date[0] : "") + "</span>");
		html = html.replace("{{contract_month}}", "<span id=\"cd_m\">" + (date.length == 3 ? String.valueOf(Integer.parseInt(date[1])) : "") + "</span>");
		html = html.replace("{{contract_day}}", "<span id=\"cd_d\">" + (date.length == 3 ? String.valueOf(Integer.parseInt(date[2])) : "") + "</span>");

		// 근로자 서명 이미지 (서명 PDF 생성 때만 들어옴, PNG data URL 형식만 허용)
		String signImage = row.get("sign_image") == null ? "" : String.valueOf(row.get("sign_image"));
		html = html.replace("{{sign_img}}", SIGN_IMAGE_PATTERN.matcher(signImage).matches()
				? "<img src=\"" + signImage + "\" alt=\"서명\">"
				: "");

		// 사용자(甲) 회사 정보와 인감 (직원 구분 cor_type 2 = 개인 더채움, 그 외·파출 = 법인 주식회사 더채움)
		boolean personal = "2".equals(text(row.get("cor_type")));
		html = html.replace("{{corp_name}}", personal ? "더채움" : "주식회사 더채움");
		html = html.replace("{{corp_addr}}", personal
				? "인천광역시 계양구 장제로927번길 4, 3층(방축동)"
				: "경기도 수원시 권선구 금호로 210, 401,501,502호");
		html = html.replace("{{corp_bizno}}", personal ? "175-05-00954" : "875-87-02456");
		html = html.replace("{{seal_img}}", "<img src=\"" + sealImage(personal
				? "templates/contract/seal_personal.png"
				: "templates/contract/seal_corp.png") + "\" alt=\"인감\">");

		// 전자서명 일시 (서명 완료 건만 양식 아래에 표시)
		String signDt = text(row.get("sign_dt"));
		html = html.replace("{{sign_info}}", signDt.isEmpty() ? ""
				: "<div class=\"signinfo\">전자서명 일시 : " + signDt + "</div>");

		return html;
	}

	// 내용 아래 끝 목표 위치 (A4 297mm 중 아래 여백 7mm를 뺀 위치에서 약간 여유, 페이지 높이 대비 비율)
	private static final double FILL_TARGET = (297.0 - 7.0) / 297.0 - 0.006;

	// 완성된 양식 HTML을 A4 한 장에 꽉 차게 PDF로 변환
	// 조항 길이가 양식 탭에서 바뀔 수 있어 매번 그려 보고 맞춤: 넘치면 줄 간격 → 글자 크기 순으로 줄이고,
	// 남으면 줄 간격을 늘려 아래 빈 공간을 채움 (줄 수는 그대로라 내용 높이가 줄 간격에 비례해 늘어남)
	public static byte[] toPdf(String html) {
		double font = 10.5;
		double lineHeight = 1.5;
		byte[] pdf = render(html, font, lineHeight);
		double[] fit = measure(pdf);

		// 한 장을 넘으면 줄 간격(최소 1.2) → 글자 크기(최소 8.5px) 순으로 줄임
		while (fit[0] > 1 && (lineHeight > 1.2 + 1e-9 || font > 8.5 + 1e-9)) {
			if (lineHeight > 1.2 + 1e-9) {
				lineHeight -= 0.1;
			} else {
				font -= 0.5;
			}
			pdf = render(html, font, lineHeight);
			fit = measure(pdf);
		}
		if (fit[0] > 1 || fit[1] >= FILL_TARGET - 0.01) {
			return pdf;
		}

		// 줄 간격을 조금 늘려 본 높이 변화로 목표 줄 간격을 계산 (최대 2.6)
		double probe = lineHeight + 0.1;
		byte[] probePdf = render(html, font, probe);
		double[] probeFit = measure(probePdf);
		if (probeFit[0] > 1 || probeFit[1] <= fit[1]) {
			return pdf;
		}
		double slope = (probeFit[1] - fit[1]) / 0.1;
		double target = Math.min(2.6, lineHeight + (FILL_TARGET - fit[1]) / slope);
		// 계산값이 반올림 등으로 살짝 넘치면 조금씩 줄여 다시 그림
		for (int i = 0; i < 8 && target > probe; i++, target -= 0.01) {
			byte[] filled = render(html, font, target);
			if (measure(filled)[0] == 1) {
				return filled;
			}
		}
		return probePdf;
	}

	// 지정한 글자 크기·줄 간격으로 양식 HTML을 A4 PDF로 그림 (한글·한자는 resources/fonts의 Noto Sans KR)
	private static byte[] render(String html, double font, double lineHeight) {
		// PDF 전용 설정 (A4 용지, 표를 용지 가운데 정렬하고 위 2px 띄워 굵은 바깥 테두리가 여백에 잘리지 않게 함, 본고딕(Noto
		// Sans KR)이 맑은고딕보다 넓어 글자를 한 단계 줄여 양식 폭 안에 맞춤, 임금표 등 작은 표는 본문보다 1px 작게)
		String pdfHtml = html.replace("</head>", "<style>"
				+ "@page { size: A4; margin: 7mm 7mm; } body { padding: 2px 0 0; font-family: \"NotoSansKR\"; }"
				+ " table.ct { margin: 0 auto; font-size: " + font + "px; line-height: " + lineHeight + "; } .signinfo { margin: 6px auto 0; }"
				+ " .ct th, .ct td { padding: 2px 5px; }"
				+ " .ct .ttl { font-size: 20px; padding: 4px; } .ct .sign { padding: 9px 6px; }"
				+ " .sub { margin: 2px 0 2px 18px; } .sub th, .sub td { font-size: " + (font - 1) + "px; padding: 0 4px; } .sub td { min-width: 0; }"
				+ "</style></head>");
		try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			PdfRendererBuilder builder = new PdfRendererBuilder();
			builder.useFastMode();
			builder.useFont(() -> font("fonts/NotoSansKR-Regular.ttf"), "NotoSansKR", 400, FontStyle.NORMAL, true);
			builder.useFont(() -> font("fonts/NotoSansKR-Bold.ttf"), "NotoSansKR", 700, FontStyle.NORMAL, true);
			builder.withW3cDocument(new W3CDom().fromJsoup(Jsoup.parse(pdfHtml)), "/");
			builder.toStream(out);
			builder.run();
			return out.toByteArray();
		} catch (IOException e) {
			throw new UncheckedIOException("근로계약서 PDF를 만들지 못했습니다.", e);
		}
	}

	// 그린 PDF의 쪽수와 첫 쪽 내용 아래 끝 위치(페이지 높이 대비 비율)를 잼 (저해상도로 그려 흰색 아닌 마지막 줄을 찾음)
	private static double[] measure(byte[] pdf) {
		try (PDDocument document = PDDocument.load(pdf)) {
			java.awt.image.BufferedImage image = new PDFRenderer(document).renderImageWithDPI(0, 50);
			int bottom = 0;
			for (int y = image.getHeight() - 1; y >= 0 && bottom == 0; y--) {
				for (int x = 0; x < image.getWidth(); x++) {
					if ((image.getRGB(x, y) & 0xFFFFFF) != 0xFFFFFF) {
						bottom = y + 1;
						break;
					}
				}
			}
			return new double[] { document.getNumberOfPages(), (double) bottom / image.getHeight() };
		} catch (IOException e) {
			throw new UncheckedIOException("근로계약서 PDF 크기를 재지 못했습니다.", e);
		}
	}

	// 서명본 PDF를 페이지별 이미지로 바꿔 앱 WebView에서 보여줄 HTML로 만듦 (서명 완료 계약서 조회용)
	public static String pdfToHtml(byte[] pdf) {
		StringBuilder html = new StringBuilder("<!DOCTYPE html><html><head><meta charset=\"UTF-8\">"
				+ "<meta name=\"viewport\" content=\"width=760\"><title>근로계약서</title>"
				+ "<style>body { margin: 0; background: #fff; } img { display: block; width: 100%; }</style></head><body>");
		try (PDDocument document = PDDocument.load(pdf)) {
			PDFRenderer renderer = new PDFRenderer(document);
			for (int page = 0; page < document.getNumberOfPages(); page++) {
				ByteArrayOutputStream out = new ByteArrayOutputStream();
				ImageIO.write(renderer.renderImageWithDPI(page, 160), "png", out);
				html.append("<img alt=\"근로계약서\" src=\"data:image/png;base64,")
						.append(Base64.getEncoder().encodeToString(out.toByteArray()))
						.append("\">");
			}
		} catch (IOException e) {
			throw new UncheckedIOException("서명본 PDF를 이미지로 바꾸지 못했습니다.", e);
		}
		return html.append("</body></html>").toString();
	}

	// PDF에 넣을 글꼴 파일 열기
	private static InputStream font(String path) {
		try {
			return new ClassPathResource(path).getInputStream();
		} catch (IOException e) {
			throw new UncheckedIOException("글꼴 파일을 읽지 못했습니다: " + path, e);
		}
	}

	// 조항 목록 JSON [{title, body}]을 양식 표의 조항 행 HTML로 변환
	// 제목·본문은 HTML 이스케이프 후 줄마다 나누고, {{ot_table}}·{{pay_table}} 줄은 표로, {{sign}}은 서명
	// 표시로 바꾼다.
	// 본문의 {{name}}·{{work_place}} 같은 값 자리는 build()에서 계약서 값으로 바뀐다.
	static String clauseRows(String clausesJson) {
		List<Map<String, Object>> clauses = new Gson().fromJson(
				clausesJson == null || clausesJson.isBlank() ? "[]" : clausesJson,
				new TypeToken<List<Map<String, Object>>>() {
				}.getType());
		StringBuilder rows = new StringBuilder();
		for (Map<String, Object> clause : clauses) {
			String title = HtmlUtils.htmlEscape(String.valueOf(clause.getOrDefault("title", ""))).replace("\n", "<br>");
			rows.append("  <tr><th>").append(title).append("</th><td colspan=\"4\">");
			String[] lines = String.valueOf(clause.getOrDefault("body", "")).split("\n", -1);
			// 조항 안 가장 긴 항목명 폭(em) ("□ 항목 : 내용" 줄의 콜론 위치를 맞추는 기준)
			double labelWidth = 0;
			for (String line : lines) {
				java.util.regex.Matcher m = LABEL_LINE.matcher(line);
				if (m.matches() && !line.contains("{{sign}}")) labelWidth = Math.max(labelWidth, labelEm(m.group(1)));
			}
			for (String line : lines) {
				java.util.regex.Matcher labelMatch = LABEL_LINE.matcher(line);
				String trimmed = line.trim();
				if ("{{ot_table}}".equals(trimmed)) {
					rows.append(OT_TABLE);
				} else if ("{{pay_table}}".equals(trimmed)) {
					rows.append(PAY_TABLE);
				} else if (line.contains("{{sign}}")) {
					// 서명 자리가 있는 줄: 왼쪽 문구와 오른쪽 성명·서명칸을 테두리 없는 2칸 표로 나눔
					String text = HtmlUtils.htmlEscape(line.replace("{{sign}}", "").stripTrailing());
					rows.append("<table style=\"width:100%; border-collapse:collapse\"><tr>")
							.append("<td style=\"border:none; padding:0\"><div class=\"ln")
							.append(trimmed.startsWith("□") ? " b" : "").append("\">").append(text)
							.append("</div></td>")
							.append("<td style=\"border:none; padding:0 6px 0 0; text-align:right; white-space:nowrap\">")
							.append(SIGN_MARK).append("</td></tr></table>");
				} else if (labelWidth > 0 && labelMatch.matches()) {
					// "□ 항목" | ": 내용" 테두리 없는 2칸 표 (항목 칸 폭을 조항 안에서 같게 해 콜론 위치 정렬, 긴 내용은 콜론 뒤에 맞춰 줄바꿈)
					// 칸 폭 고정 (항목 칸 = 조항 안 가장 긴 항목명 + □, 콜론 칸 = 0.8em, 나머지는 내용)
					rows.append("<table style=\"width:100%; border-collapse:collapse; table-layout:fixed\"><tr>")
							.append("<td style=\"border:none; padding:0; vertical-align:top; white-space:pre-wrap; width:")
							.append(String.format(java.util.Locale.ROOT, "%.2f", labelWidth + 1.15)).append("em\">□ ")
							.append(HtmlUtils.htmlEscape(labelMatch.group(1))).append("</td>")
							.append("<td style=\"border:none; padding:0; vertical-align:top; white-space:nowrap; width:0.8em\">:</td>")
							.append("<td style=\"border:none; padding:0; vertical-align:top\"><div class=\"ln\">")
							.append(HtmlUtils.htmlEscape(labelMatch.group(2))).append("</div></td></tr></table>");
				} else {
					String text = HtmlUtils.htmlEscape(line);
					rows.append("<div class=\"ln").append(trimmed.startsWith("□") ? " b" : "").append("\">")
							.append(text.isEmpty() ? "&nbsp;" : text).append("</div>");
				}
			}
			rows.append("</td></tr>\n");
		}
		return rows.toString();
	}

	// "□ 항목 : 내용" 줄 판별 (항목명에 콜론·값 자리가 없고 12자 이내, 콜론 앞은 띄어쓰기)
	// ERP ContractForm.js LABEL_LINE과 같은 규칙
	private static final java.util.regex.Pattern LABEL_LINE = java.util.regex.Pattern.compile("^\\s*□\\s*([^:{}]{1,12}?)\\s+:\\s*(.*)$");

	// 항목명 표시 폭(em): 한글 1, 띄어쓰기 0.3, 영문·숫자 0.55
	private static double labelEm(String label) {
		double sum = 0;
		for (char ch : label.toCharArray()) {
			sum += ch == ' ' ? 0.3 : (ch >= 0x21 && ch <= 0x7e) ? 0.55 : 1;
		}
		return sum;
	}

	// 제4조 월평균 연장·야간·휴일근로시간 표
	private static final String OT_TABLE = "<table class=\"sub\" style=\"margin-left:90px\">"
			+ "<tr><th>월평균</th><th>연장근로</th><th>야간근로</th><th>휴일근로</th></tr>"
			+ "<tr><th>근로시간</th><td class=\"c\">{{ot_hours}}</td><td class=\"c\">{{night_hours}}</td><td class=\"c\">{{holiday_hours}}</td></tr>"
			+ "</table>";

	// 제7조 월급여액 및 임금 구성항목 표
	private static final String PAY_TABLE = "<table class=\"sub\">"
			+ "<tr><th rowspan=\"2\">월 급여액</th><th colspan=\"4\">통상임금</th><th rowspan=\"2\">연차수당</th><th rowspan=\"2\">연장수당</th><th rowspan=\"2\">휴일수당</th><th rowspan=\"2\">야간수당</th></tr>"
			+ "<tr><th>기본급</th><th>식대보조비</th><th>차량유지비</th><th>육아수당</th></tr>"
			+ "<tr><td>{{total_pay}}</td><td>{{base_pay}}</td><td>{{meal_pay}}</td><td>{{car_pay}}</td><td>{{child_pay}}</td><td>{{annual_leave_pay}}</td><td>{{ot_pay}}</td><td>{{holiday_pay}}</td><td>{{night_pay}}</td></tr>"
			+ "</table>";

	// 제9조 근로자 성명과 서명 자리 (서명 이미지는 build()에서 채움)
	private static final String SIGN_MARK = "{{name}} &nbsp;&nbsp;&nbsp;&nbsp;<span class=\"signbox\">(서명 또는 날인){{sign_img}}</span>";

	// 제2조 계약기간 문구 (종료일 없으면 기간의 정함이 없는 계약, 있으면 기간제 기간 표시)
	private static String contractPeriod(Map<String, Object> row) {
		String start = text(row.get("contract_start_dt")).replace("-", ".");
		String end = text(row.get("contract_end_dt"));
		if (end.isEmpty()) {
			return "계약기간의 정함이 없는 ( &nbsp;&nbsp;" + start + "&nbsp;&nbsp; ) 부터로 정한다.";
		}
		return text(row.get("contract_start_dt")) + " ~ " + end;
	}

	// 일반 문자열 값 (HTML 이스케이프, null은 빈칸)
	// 제3조 휴게 시간대 (", ( 12시 00분 ~ 13시 00분 )" 형식, 2회면 두 구간, 입력이 없으면 빈 문자열)
	// 양식 본문 "총 {{break_hours}}시간{{break_times}}" 뒤에 붙도록 앞에 쉼표를 넣어 돌려준다
	private static String breakTimes(Map<String, Object> row) {
		StringBuilder sb = new StringBuilder();
		for (String n : new String[] { "1", "2" }) {
			String start = text(row.get("break_start" + n));
			String end = text(row.get("break_end" + n));
			if (start.isEmpty() || end.isEmpty()) continue;
			sb.append(", ( ").append(clock(start)).append(" ~ ").append(clock(end)).append(" )");
		}
		return sb.toString();
	}

	// "12:00" → "12시 00분"
	private static String clock(String hhmm) {
		String[] p = hhmm.split(":");
		return p.length == 2 ? p[0] + "시 " + p[1] + "분" : hhmm;
	}

	private static String text(Object value) {
		return value == null ? "" : HtmlUtils.htmlEscape(String.valueOf(value).trim());
	}

	// 시간·일수 값 (12.45333처럼 입력한 자리수 그대로, 8.00은 8로 표시)
	private static String number(Object value) {
		if (value == null || String.valueOf(value).trim().isEmpty()) {
			return "";
		}
		return new BigDecimal(String.valueOf(value).trim()).stripTrailingZeros().toPlainString();
	}

	// 금액 값 (천 단위 콤마, 0·빈 값은 '-')
	private static String money(Object value) {
		if (value == null || String.valueOf(value).trim().isEmpty()) {
			return "-";
		}
		BigDecimal amount = new BigDecimal(String.valueOf(value).trim());
		if (amount.signum() == 0) {
			return "-";
		}
		return new DecimalFormat("#,###").format(amount);
	}

	// 인감 PNG를 data URL로 바꿔 둔 캐시 (경로별 1회만 읽음)
	private static final Map<String, String> SEAL_CACHE = new java.util.concurrent.ConcurrentHashMap<>();

	// 인감 이미지 파일을 HTML·PDF에 바로 넣을 수 있는 data URL로 읽기
	private static String sealImage(String path) {
		return SEAL_CACHE.computeIfAbsent(path, p -> {
			try (InputStream in = new ClassPathResource(p).getInputStream()) {
				return "data:image/png;base64," + Base64.getEncoder().encodeToString(in.readAllBytes());
			} catch (IOException e) {
				throw new UncheckedIOException("인감 이미지를 읽지 못했습니다: " + p, e);
			}
		});
	}

	// 양식 파일은 처음 한 번만 읽어 둔다
	private static String loadTemplate() {
		if (template == null) {
			try (InputStream in = new ClassPathResource(TEMPLATE_PATH).getInputStream()) {
				template = new String(in.readAllBytes(), StandardCharsets.UTF_8);
			} catch (IOException e) {
				throw new UncheckedIOException("근로계약서 양식 파일을 읽지 못했습니다: " + TEMPLATE_PATH, e);
			}
		}
		return template;
	}
}
