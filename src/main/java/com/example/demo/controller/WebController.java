package com.example.demo.controller;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.demo.service.WebService;

@RestController
public class WebController {

	// ===== 공통/기존 웹 컨트롤러 영역 =====
	private static final Logger log = LoggerFactory.getLogger(WebController.class);
	private static final Set<String> ALLOWED_PRIORITY_RULES = Set.of("user_id", "position_type", "department");

	// webcontroller: 웹 도메인 서비스
	private final WebService webService;

	@Value("${erp.webhook.secret:}")
	private String webhookSecret;

	@Value("${erp.contact.routing.priority:user_id,position_type,department}")
	private String defaultPriorityCsv;

	@Value("${erp.contact.routing.user-ids:}")
	private String defaultUserIdsCsv;

	@Value("${erp.contact.routing.position-types:}")
	private String defaultPositionTypesCsv;

	@Value("${erp.contact.routing.departments:}")
	private String defaultDepartmentsCsv;

	public WebController(WebService webService) {
		this.webService = webService;
	}

	// 문의 답변 대기 목록을 조회(ERP/웹 공용)
	/*
	 * webcontroller: 웹 문의 답변 대기 목록 조회(ERP/웹 공용 엔드포인트)
	 */
	@GetMapping({ "/ERP/ContactInquiryPendingList", "/User/ContactInquiryPendingList" })
	public Map<String, Object> ContactInquiryPendingList(@RequestParam(required = false) Map<String, Object> paramMap) {
		Map<String, Object> out = new HashMap<>();
		try {
			List<Map<String, Object>> list = webService.selectContactInquiryPendingList(paramMap);
			out.put("code", "200");
			out.put("list", list);
			return out;
		} catch (Exception e) {
			// webcontroller: 문의 테이블 미구성/쿼리 오류가 있어도 알림 폴링은 빈 목록으로 응답
			log.error("문의 답변 대기 목록 조회 실패", e);
			out.put("code", "200");
			out.put("list", Collections.emptyList());
			return out;
		}
	}

	// ===== ERP 훅(Webhook) 영역 =====
	// ERP 문의 웹훅 수신/라우팅 계산/응답 조립을 처리
	@PostMapping("/ERP/ContactInquiryWebhook")
	public Map<String, Object> ContactInquiryWebhook(
			@RequestBody(required = false) Map<String, Object> body,
			@RequestHeader(value = "X-ERP-WEBHOOK-SECRET", required = false) String inboundSecret) {

		Map<String, Object> out = new HashMap<>();
		Map<String, Object> requestBody = body == null ? new HashMap<>() : body;

		if (isSecretMismatch(inboundSecret)) {
			out.put("code", "401");
			out.put("message", "웹훅 인증에 실패했습니다.");
			return out;
		}

		String eventType = normalizeText(requestBody.get("eventType"));
		if (eventType.isEmpty()) {
			eventType = "CONTACT_INQUIRY_CREATED";
		}

		if (!"CONTACT_INQUIRY_CREATED".equals(eventType)) {
			out.put("code", "400");
			out.put("message", "지원하지 않는 eventType 입니다.");
			out.put("event_type", eventType);
			return out;
		}

		Map<String, Object> payload = toMap(requestBody.get("payload"));
		if (payload.isEmpty()) {
			payload = extractPayloadFallback(requestBody);
		}

		Map<String, Object> routingHints = toMap(requestBody.get("routingHints"));

		List<String> priority = firstNonEmpty(
				parsePriorityRules(routingHints.get("priority")),
				parsePriorityRules(defaultPriorityCsv));
		if (priority.isEmpty()) {
			priority = List.of("user_id", "position_type", "department");
		}

		List<String> preferredUserIds = firstNonEmpty(
				parseStringList(routingHints.get("userIds")),
				parseStringList(defaultUserIdsCsv));

		List<Integer> preferredPositionTypes = firstNonEmpty(
				parseIntegerList(routingHints.get("positionTypes")),
				parseIntegerList(defaultPositionTypesCsv));

		List<Integer> preferredDepartments = firstNonEmpty(
				parseIntegerList(routingHints.get("departments")),
				parseIntegerList(defaultDepartmentsCsv));

		Map<String, Object> routingResult = webService.resolveRecipients(
				priority,
				preferredUserIds,
				preferredPositionTypes,
				preferredDepartments);

		Map<String, Object> criteria = new HashMap<>();
		criteria.put("user_ids", preferredUserIds);
		criteria.put("position_types", preferredPositionTypes);
		criteria.put("departments", preferredDepartments);
		routingResult.put("criteria", criteria);

		log.info(
				"ERP 문의 웹훅 수신: eventType={}, businessName={}, selectedBy={}, primaryUserId={}",
				eventType,
				normalizeText(payload.get("businessName")),
				normalizeText(routingResult.get("selected_by")),
				normalizeText(routingResult.get("primary_user_id")));

		out.put("code", "200");
		out.put("message", "ERP 문의 웹훅 수신 완료");
		out.put("event_type", eventType);
		out.put("routing", routingResult);

		Map<String, Object> inquiry = new HashMap<>();
		inquiry.put("business_name", normalizeText(payload.get("businessName")));
		inquiry.put("manager_name", normalizeText(payload.get("managerName")));
		inquiry.put("phone_number", normalizeText(payload.get("phoneNumber")));
		inquiry.put("email", normalizeText(payload.get("email")));
		inquiry.put("source", normalizeText(payload.get("source")));
		inquiry.put("erp_sync_target", normalizeText(payload.get("erpSyncTarget")));
		out.put("inquiry", inquiry);

		return out;
	}

	// 웹훅 시크릿 일치 여부를 검증
	private boolean isSecretMismatch(String inboundSecret) {
		String configuredSecret = normalizeText(webhookSecret);
		if (configuredSecret.isEmpty()) {
			return false;
		}
		return !configuredSecret.equals(normalizeText(inboundSecret));
	}

	// Object 타입을 Map<String, Object>로 안전 변환
	@SuppressWarnings("unchecked")
	private Map<String, Object> toMap(Object value) {
		if (value instanceof Map<?, ?> mapValue) {
			return (Map<String, Object>) mapValue;
		}
		return new HashMap<>();
	}

	// payload 키가 없을 때 요청 바디를 payload로 간주해 보정
	private Map<String, Object> extractPayloadFallback(Map<String, Object> requestBody) {
		Map<String, Object> payload = new HashMap<>(requestBody);
		payload.remove("eventType");
		payload.remove("routingHints");
		payload.remove("sourceSystem");
		return payload;
	}

	// 우선순위 문자열 목록을 허용 규칙(user_id/position_type/department)으로 정규화
	private List<String> parsePriorityRules(Object value) {
		List<String> raw = parseStringList(value);
		List<String> parsed = new ArrayList<>();

		for (String item : raw) {
			String normalized = normalizeText(item)
					.toLowerCase(Locale.ROOT)
					.replace("-", "_")
					.replace(" ", "_");

			if (ALLOWED_PRIORITY_RULES.contains(normalized)) {
				parsed.add(normalized);
			}
		}

		return dedupeStringList(parsed);
	}

	// CSV 또는 배열 입력을 문자열 리스트로 파싱
	private List<String> parseStringList(Object value) {
		Set<String> values = new LinkedHashSet<>();

		if (value instanceof List<?> listValue) {
			for (Object item : listValue) {
				String normalized = normalizeText(item);
				if (!normalized.isEmpty()) {
					values.add(normalized);
				}
			}
			return new ArrayList<>(values);
		}

		String raw = normalizeText(value);
		if (raw.isEmpty()) {
			return new ArrayList<>();
		}

		String[] split = raw.split(",");
		for (String item : split) {
			String normalized = normalizeText(item);
			if (!normalized.isEmpty()) {
				values.add(normalized);
			}
		}

		return new ArrayList<>(values);
	}

	// 문자열 리스트를 정수 리스트로 파싱
	private List<Integer> parseIntegerList(Object value) {
		List<String> rawValues = parseStringList(value);
		List<Integer> parsed = new ArrayList<>();

		for (String raw : rawValues) {
			try {
				parsed.add(Integer.valueOf(raw));
			} catch (NumberFormatException ex) {
				// 숫자 변환 실패 값은 무시
			}
		}

		return dedupeIntegerList(parsed);
	}

	// 문자열 리스트 중복 제거
	private List<String> dedupeStringList(List<String> values) {
		return new ArrayList<>(new LinkedHashSet<>(values));
	}

	// 정수 리스트 중복 제거
	private List<Integer> dedupeIntegerList(List<Integer> values) {
		return new ArrayList<>(new LinkedHashSet<>(values));
	}

	// 우선 목록이 비어있을 때 대체 목록을 선택
	private <T> List<T> firstNonEmpty(List<T> preferred, List<T> fallback) {
		if (preferred != null && !preferred.isEmpty()) {
			return preferred;
		}
		return fallback == null ? new ArrayList<>() : fallback;
	}

	// 공통 텍스트 정규화(Null-safe trim)
	private String normalizeText(Object value) {
		return value == null ? "" : String.valueOf(value).trim();
	}
}
