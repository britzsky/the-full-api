package com.example.demo.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.example.demo.mapper.WebMapper;

@Service
public class WebService {

	// ===== 공통/기존 웹 도메인 영역 =====
	// webservice: 웹 도메인 매퍼
	private final WebMapper webMapper;

	public WebService(WebMapper webMapper) {
		this.webMapper = webMapper;
	}

	// ERP 문의 목록/답변대기 목록을 조회하는 메소드
	// webservice: 웹 문의 목록 조회
	public List<Map<String, Object>> selectContactInquiryPendingList(Map<String, Object> paramMap) {
		Map<String, Object> query = new HashMap<>();
		query.put("answer_yn", resolveAnswerYnFilter(paramMap));
		query.put("user_id", normalizeText(paramMap == null ? null : paramMap.get("user_id")));
		return webMapper.selectContactInquiryPendingList(query);
	}

	// ERP 라우팅 대상자 원본 목록을 조회하는 메소드
	// webservice: ERP 웹훅 라우팅 대상자 조회
	public List<Map<String, Object>> selectActiveUsersForErpRouting() {
		return webMapper.selectActiveUsersForErpRouting();
	}

	// ===== ERP 훅 라우팅 영역 =====
	// 우선순위(user_id > position_type > department) 기준으로 실제 수신자를 계산하는 메소드
	public Map<String, Object> resolveRecipients(
			List<String> priorityRules,
			List<String> preferredUserIds,
			List<Integer> preferredPositionTypes,
			List<Integer> preferredDepartments) {

		List<RoutingUser> activeUsers = loadActiveRoutingUsers();
		Map<String, RoutingUser> activeUserMap = new LinkedHashMap<>();
		for (RoutingUser user : activeUsers) {
			activeUserMap.put(user.userId, user);
		}

		List<String> matchedUserIds = new ArrayList<>();
		String selectedBy = "";

		for (String rule : priorityRules) {
			if ("user_id".equals(rule)) {
				matchedUserIds = matchByUserId(preferredUserIds, activeUserMap);
			} else if ("position_type".equals(rule)) {
				matchedUserIds = matchByPositionType(preferredPositionTypes, activeUsers);
			} else if ("department".equals(rule)) {
				matchedUserIds = matchByDepartment(preferredDepartments, activeUsers);
			}

			if (!matchedUserIds.isEmpty()) {
				selectedBy = rule;
				break;
			}
		}

		String primaryUserId = matchedUserIds.isEmpty() ? "" : matchedUserIds.get(0);
		List<Map<String, Object>> recipientProfiles = new ArrayList<>();
		for (String userId : matchedUserIds) {
			RoutingUser user = activeUserMap.get(userId);
			if (user == null) {
				continue;
			}
			Map<String, Object> profile = new HashMap<>();
			profile.put("user_id", user.userId);
			profile.put("user_name", user.userName);
			profile.put("department", user.department);
			profile.put("position_types", new ArrayList<>(user.positionTypes));
			recipientProfiles.add(profile);
		}

		Map<String, Object> result = new HashMap<>();
		result.put("priority", priorityRules);
		result.put("selected_by", selectedBy);
		result.put("matched_user_ids", matchedUserIds);
		result.put("primary_user_id", primaryUserId);
		result.put("matched_count", matchedUserIds.size());
		result.put("available_user_count", activeUsers.size());
		result.put("recipient_profiles", recipientProfiles);
		return result;
	}

	// user_id 우선순위 규칙으로 수신자 후보를 필터링하는 메소드
	private List<String> matchByUserId(List<String> preferredUserIds, Map<String, RoutingUser> activeUserMap) {
		if (preferredUserIds == null || preferredUserIds.isEmpty()) {
			return List.of();
		}

		Set<String> matched = new LinkedHashSet<>();
		for (String userId : preferredUserIds) {
			String normalized = normalizeText(userId);
			if (normalized.isEmpty()) {
				continue;
			}
			if (activeUserMap.containsKey(normalized)) {
				matched.add(normalized);
			}
		}
		return new ArrayList<>(matched);
	}

	// position_type 우선순위 규칙으로 수신자 후보를 필터링하는 메소드
	private List<String> matchByPositionType(List<Integer> preferredPositionTypes, List<RoutingUser> activeUsers) {
		if (preferredPositionTypes == null || preferredPositionTypes.isEmpty()) {
			return List.of();
		}

		Set<Integer> preferredSet = new LinkedHashSet<>(preferredPositionTypes);
		Set<String> matched = new LinkedHashSet<>();
		for (RoutingUser user : activeUsers) {
			boolean anyMatched = user.positionTypes.stream().anyMatch(preferredSet::contains);
			if (anyMatched) {
				matched.add(user.userId);
			}
		}

		return new ArrayList<>(matched);
	}

	// department 우선순위 규칙으로 수신자 후보를 필터링하는 메소드
	private List<String> matchByDepartment(List<Integer> preferredDepartments, List<RoutingUser> activeUsers) {
		if (preferredDepartments == null || preferredDepartments.isEmpty()) {
			return List.of();
		}

		Set<Integer> preferredSet = new LinkedHashSet<>(preferredDepartments);
		Set<String> matched = new LinkedHashSet<>();
		for (RoutingUser user : activeUsers) {
			if (user.department != null && preferredSet.contains(user.department)) {
				matched.add(user.userId);
			}
		}

		return new ArrayList<>(matched);
	}

	// 라우팅 판단에 필요한 활성 사용자 프로필을 로딩/그룹핑하는 메소드
	private List<RoutingUser> loadActiveRoutingUsers() {
		List<Map<String, Object>> rows = selectActiveUsersForErpRouting();
		if (rows == null || rows.isEmpty()) {
			return new ArrayList<>();
		}
		Map<String, RoutingUser> grouped = new LinkedHashMap<>();

		for (Map<String, Object> row : rows) {
			String userId = normalizeText(row.get("user_id"));
			if (userId.isEmpty()) {
				continue;
			}

			RoutingUser user = grouped.get(userId);
			if (user == null) {
				user = new RoutingUser();
				user.userId = userId;
				user.userName = normalizeText(row.get("user_name"));
				user.department = toInteger(row.get("department"));
				grouped.put(userId, user);
			}

			Integer positionType = toInteger(row.get("position_type"));
			if (positionType != null) {
				user.positionTypes.add(positionType);
			}
		}

		return new ArrayList<>(grouped.values());
	}

	// 문자열/숫자 혼합 입력값을 Integer로 안전 변환하는 메소드
	private Integer toInteger(Object value) {
		String normalized = normalizeText(value);
		if (normalized.isEmpty()) {
			return null;
		}

		try {
			return Integer.valueOf(normalized);
		} catch (NumberFormatException ex) {
			return null;
		}
	}

	// answer_yn 값을 Y/N 형태로 정규화하는 메소드
	// webservice: answer_yn 정규화
	private String normalizeYn(Object value) {
		String normalized = normalizeText(value).toUpperCase();
		return ("Y".equals(normalized) || "N".equals(normalized)) ? normalized : "";
	}

	// 문의 목록 조회 시 answer_yn 필터 적용 여부를 해석하는 메소드
	// webservice: 문의 목록 answer_yn 필터 결정
	private String resolveAnswerYnFilter(Map<String, Object> paramMap) {
		if (paramMap == null || !paramMap.containsKey("answer_yn")) {
			return "";
		}

		String normalized = normalizeText(paramMap.get("answer_yn")).toUpperCase();
		if (normalized.isEmpty() || "ALL".equals(normalized) || "*".equals(normalized)) {
			return "";
		}

		return normalizeYn(normalized);
	}

	// 공통 텍스트 트리밍 정규화 메소드
	// webservice: 공통 텍스트 정규화
	private String normalizeText(Object value) {
		if (value == null) {
			return "";
		}
		return String.valueOf(value).trim();
	}

	// 라우팅 계산용 내부 사용자 모델
	private static class RoutingUser {
		String userId;
		String userName;
		Integer department;
		Set<Integer> positionTypes = new LinkedHashSet<>();
	}
}
