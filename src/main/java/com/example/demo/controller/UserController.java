package com.example.demo.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.demo.WebConfig;
import com.example.demo.service.UserService;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import jakarta.servlet.http.HttpServletRequest;

@RestController
public class UserController {

	private final UserService userService;
	private final String internalApiSecret;

	@Autowired
	public UserController(
			UserService userService,
			WebConfig webConfig,
			@Value("${internal.api.secret:}") String internalApiSecret) {
		this.userService = userService;
		this.internalApiSecret = internalApiSecret == null ? "" : internalApiSecret.trim();
	}

	// 내부 연동용 user_id 문자열 정규화
	private String normalizeText(Object value) {
		return value == null ? "" : String.valueOf(value).trim();
	}

	// 내부 연동용 user_id 형식 검증(영문/숫자/._- 만 허용)
	private boolean isValidInternalUserId(String userId) {
		return userId.matches("^[A-Za-z0-9._-]{1,40}$");
	}

	// 내부 API 호출 허용 여부 확인(로컬 호출 또는 공유 시크릿)
	private boolean hasInternalApiAccess(HttpServletRequest request) {
		String remoteAddr = normalizeText(request.getRemoteAddr());
		if ("127.0.0.1".equals(remoteAddr) || "::1".equals(remoteAddr) || "0:0:0:0:0:0:0:1".equals(remoteAddr)) {
			return true;
		}

		String requestSecret = normalizeText(request.getHeader("X-THEFULL-INTERNAL-SECRET"));
		return !internalApiSecret.isEmpty() && internalApiSecret.equals(requestSecret);
	}

	// 사용자 조회 결과 첫 행 반환
	private Map<String, Object> getFirstUserInfo(String userId) {
		Map<String, Object> paramMap = new HashMap<>();
		paramMap.put("user_id", userId);
		List<Map<String, Object>> resultList = userService.SelectUserInfo(paramMap);
		return resultList == null || resultList.isEmpty() ? null : resultList.get(0);
	}

	// 내부 API 공통 접근 차단 응답
	private ResponseEntity<Map<String, Object>> forbiddenInternalApi() {
		Map<String, Object> payload = new HashMap<>();
		payload.put("error", "내부 연동 전용 API입니다.");
		return ResponseEntity.status(HttpStatus.FORBIDDEN).body(payload);
	}

	// 내부 API: 문의관리 접근 확인용 사용자 존재 여부 조회
	@GetMapping("/Internal/User/Exists")
	public ResponseEntity<Map<String, Object>> SelectInternalUserExists(
			@RequestParam("user_id") String userId,
			HttpServletRequest request) {
		if (!hasInternalApiAccess(request)) {
			return forbiddenInternalApi();
		}

		String normalizedUserId = normalizeText(userId);
		if (normalizedUserId.isEmpty() || !isValidInternalUserId(normalizedUserId)) {
			return ResponseEntity.badRequest().body(Map.of("error", "user_id가 올바르지 않습니다."));
		}

		Map<String, Object> userInfo = getFirstUserInfo(normalizedUserId);
		if (userInfo == null) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "사용자 정보를 찾을 수 없습니다."));
		}

		return ResponseEntity.ok(Map.of("user_id", normalizeText(userInfo.get("user_id"))));
	}

	// 내부 API: 답변 메일 발송용 사용자 계정 조회
	@GetMapping("/Internal/User/MailAuth")
	public ResponseEntity<Map<String, Object>> SelectInternalUserMailAuth(
			@RequestParam("user_id") String userId,
			HttpServletRequest request) {
		if (!hasInternalApiAccess(request)) {
			return forbiddenInternalApi();
		}

		String normalizedUserId = normalizeText(userId);
		if (normalizedUserId.isEmpty() || !isValidInternalUserId(normalizedUserId)) {
			return ResponseEntity.badRequest().body(Map.of("error", "user_id가 올바르지 않습니다."));
		}

		Map<String, Object> userInfo = getFirstUserInfo(normalizedUserId);
		if (userInfo == null) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "사용자 정보를 찾을 수 없습니다."));
		}

		Map<String, Object> payload = new HashMap<>();
		payload.put("user_id", normalizeText(userInfo.get("user_id")));
		payload.put("password", normalizeText(userInfo.get("password")));
		return ResponseEntity.ok(payload);
	}

	/*
	 * method : Login
	 * comment : 로그인
	 */
	@PostMapping("/User/Login")
	public String Login(@RequestBody HashMap<String, Object> map) {
		
		System.out.println(map);
		
		Map<String, Object> resultMap = userService.Login(map);
		JsonObject obj = new JsonObject();

		// null 안전 처리
		String statusCode = "400";
		if (resultMap != null && resultMap.get("status_code") != null) {
			statusCode = String.valueOf(resultMap.get("status_code"));
		}

		// ✅ 1) 아이디/비번 실패 OR ✅ 2) 미승인(use_yn='N') 차단
		if (!"200".equals(statusCode)) {
			obj.addProperty("code", statusCode);

			if ("400".equals(statusCode)) {
				obj.addProperty("msg", "아이디 혹은 비밀번호를 확인하세요.");
			} else {
				obj.addProperty("msg", "승인되지 않은 계정입니다. 관리자에게 문의해주세요.");
			}
			return obj.toString();
		}

		// ===== 성공 응답 =====
		obj.addProperty("user_id", String.valueOf(resultMap.get("user_id")));
		obj.addProperty("user_type", String.valueOf(resultMap.get("user_type")));
		obj.addProperty("position", String.valueOf(resultMap.get("position")));
		
		String userId = String.valueOf(resultMap.get("user_id"));
		int position = Integer.parseInt(String.valueOf(resultMap.get("position")));

		if ("ceo".equals(userId)) {
			obj.addProperty("position_name", "CEO");
		} else if ("britzsky".equals(userId) || "hh2".equals(userId) || "mh2".equals(userId) || "bh4".equals(userId)
				|| "yh2".equals(userId)) {
			obj.addProperty("position_name", "Team Leader");
		} else if ("sy7".equals(userId) || "jr1".equals(userId)) {
			obj.addProperty("position_name", "Part Leader");
		} else if (position == 8) {
			obj.addProperty("position_name", "Dietitian");
		} else {
			obj.addProperty("position_name", "Manager");
		}
		
		obj.addProperty("department", String.valueOf(resultMap.get("department")));
		obj.addProperty("account_id", String.valueOf(resultMap.get("account_id")));
		obj.addProperty("user_name", String.valueOf(resultMap.get("user_name")));
		obj.addProperty("web_position", normalizeText(resultMap.get("web_position")).toUpperCase());

		obj.addProperty("code", statusCode);

		return obj.toString();
	}

	/*
	 * method : ApprovalPendingList
	 * comment : 승인대기 목록 조회
	 */
	@GetMapping("/User/ApprovalPendingList")
	public Map<String, Object> ApprovalPendingList() {
		Map<String, Object> out = new HashMap<>();
		out.put("code", "200");
		out.put("list", userService.SelectApprovalPendingUsers(new HashMap<>()));
		return out;
	}

	/*
	 * method : ApprovalSave
	 * comment : 저장(ApprovalSave) 엔드포인트 추가: 프론트의 /User/ApprovalSave 대응
	 */
	@PostMapping("/User/ApprovalSave")
	@SuppressWarnings("unchecked")
	public Map<String, Object> ApprovalSave(@RequestBody Map<String, Object> body) {
		Map<String, Object> out = new HashMap<>();
		try {
			Object listObj = body.get("list");
			List<Map<String, Object>> list = (List<Map<String, Object>>) listObj;

			int updated = userService.ApprovalSave(list);
			out.put("code", "200");
			out.put("updated", updated);
		} catch (Exception e) {
			out.put("code", "400");
			out.put("msg", "승인 저장 처리 중 오류가 발생했습니다.");
		}
		return out;
	}

	/*
	 * method : UserDelYnSave
	 * comment : 사용자 재직/퇴사 저장
	 */
	@PostMapping("/User/UserDelYnSave")
	public Map<String, Object> UserDelYnSave(@RequestBody Map<String, Object> body) {
		Map<String, Object> out = new HashMap<>();
		try {
			String userId = String.valueOf(body.get("user_id"));
			String delYn = String.valueOf(body.get("del_yn")).toUpperCase();

			if (userId == null || userId.trim().isEmpty()) {
				out.put("code", "400");
				out.put("msg", "user_id가 비어 있습니다.");
				return out;
			}

			if (!"Y".equals(delYn) && !"N".equals(delYn)) {
				out.put("code", "400");
				out.put("msg", "del_yn 값이 올바르지 않습니다. (Y 또는 N)");
				return out;
			}

			Map<String, Object> param = new HashMap<>();
			param.put("user_id", userId);
			param.put("del_yn", delYn);

			int updated = userService.UpdateUserDelYn(param);
			out.put("code", "200");
			out.put("updated", updated);
		} catch (Exception e) {
			out.put("code", "400");
			out.put("msg", "처리 중 오류가 발생했습니다.");
		}
		return out;
	}

	/*
	 * method : UserRgt
	 * comment : 사용자 등록
	 */
	@PostMapping("/User/UserRgt")
	public String UserRgt(@RequestBody Map<String, Object> paramMap) {

		JsonObject obj = new JsonObject();
		int iResult = 0;

		try {
			Map<String, Object> info = (Map<String, Object>) paramMap.get("info");
			Map<String, Object> detail = (Map<String, Object>) paramMap.get("detail");
			Map<String, Object> reqAccountMember = (Map<String, Object>) paramMap.get("account_member");
			Map<String, Object> accountMember = null;

			if (info == null || detail == null) {
				obj.addProperty("code", 400);
				obj.addProperty("message", "요청 형식이 올바르지 않습니다.");
				return obj.toString();
			}

			String userType = info == null ? "" : String.valueOf(info.getOrDefault("user_type", "")).trim();
			String userId = info == null ? "" : String.valueOf(info.getOrDefault("user_id", "")).trim();
			String isUpdateRaw = String.valueOf(paramMap.getOrDefault("is_update", "")).trim();
			boolean isUpdate = "true".equalsIgnoreCase(isUpdateRaw) || "y".equalsIgnoreCase(isUpdateRaw)
					|| "1".equals(isUpdateRaw);

			if (userId.isEmpty()) {
				obj.addProperty("code", 400);
				obj.addProperty("message", "user_id는 필수입니다.");
				return obj.toString();
			}

			Map<String, Object> userIdParam = new HashMap<>();
			userIdParam.put("user_id", userId);
			int existsUserId = userService.CountUserId(userIdParam);
			if (existsUserId > 0 && !isUpdate) {
				obj.addProperty("code", 400);
				obj.addProperty("message", "이미 사용 중인 아이디입니다.");
				return obj.toString();
			}

			if ("4".equals(userType)) {
				info.put("department", 7); // 통합/유틸은 현장(7) 고정
				String positionType = "";
				if (reqAccountMember != null && reqAccountMember.get("position_type") != null) {
					positionType = String.valueOf(reqAccountMember.get("position_type")).trim();
				}
				if (positionType.isEmpty() && info != null && info.get("util_member_type") != null) {
					positionType = String.valueOf(info.get("util_member_type")).trim();
				}

				if (!"6".equals(positionType) && !"7".equals(positionType)) {
					obj.addProperty("code", 400);
					obj.addProperty("message", "통합/유틸 구분값이 필요합니다.");
					return obj.toString();
				}

				// 통합/유틸 인력의 member_id는 기존값 재사용, 없으면 기존 키 생성 로직으로 신규 발급
				String utilMemberId = userService.SelectUtilMemberIdByUserId(userIdParam);
				if (utilMemberId == null || utilMemberId.trim().isEmpty()) {
					utilMemberId = userService.NowDateKey();
				}

				accountMember = new HashMap<>();
				accountMember.put("member_id", utilMemberId);
				accountMember.put("account_id", "6".equals(positionType) ? "2" : "1"); // 유틸:2, 통합:1
				accountMember.put("name", info == null ? "" : info.get("user_name"));
				accountMember.put("join_dt", info == null ? null : info.get("join_dt"));
				accountMember.put("del_yn", "N");
				accountMember.put("display_yn", "Y");
				accountMember.put("position_type", Integer.valueOf(positionType)); // 유틸:6, 통합:7
				accountMember.put("address", detail == null ? null : detail.get("address"));
				accountMember.put("phone", detail == null ? null : detail.get("phone"));
				accountMember.put("note", reqAccountMember == null ? null : reqAccountMember.get("note"));
				accountMember.put("user_id", userId);
			}

			iResult += userService.UserRgtAll(info, detail, accountMember);

			if (iResult > 0) {
				obj.addProperty("code", 200);
				obj.addProperty("message", "성공");
			} else {
				obj.addProperty("code", 400);
				obj.addProperty("message", "실패");
			}
		} catch (Exception e) {
			e.printStackTrace();
			obj.addProperty("code", 400);
			obj.addProperty("message", e.getMessage() == null ? "실패" : e.getMessage());
		}

		return obj.toString();
	}

	/*
	 * method : SelectUserInfo
	 * comment : 직원 정보 조회
	 */
	@GetMapping("/User/SelectUserInfo")
	public String SelectUserInfo(@RequestParam Map<String, Object> paramMap) {
		List<Map<String, Object>> resultList = userService.SelectUserInfo(paramMap);
		return new Gson().toJson(resultList);
	}

	/*
	 * method : UserRecordSheetList
	 * comment : 신사업(일단,...)근태관리 조회
	 */
	@GetMapping("User/UserRecordSheetList")
	public String UserRecordSheetList(@RequestParam Map<String, Object> paramMap) {
		List<Map<String, Object>> resultList = userService.UserRecordSheetList(paramMap);
		return new Gson().toJson(resultList);
	}

	/*
	 * method : UserMemberList
	 * comment : 신사업(일단,...)근태관리 직원정보 조회
	 */
	@GetMapping("User/UserMemberList")
	public String UserMemberList(@RequestParam Map<String, Object> paramMap) {
		List<Map<String, Object>> resultList = userService.UserMemberList(paramMap);
		return new Gson().toJson(resultList);
	}

	/*
	 * method : UserManageList
	 * comment : 사용자 목록 조회
	 */
	@GetMapping("/User/UserManageList")
	public String UserManageList(@RequestParam Map<String, Object> paramMap) {
		List<Map<String, Object>> resultList = userService.UserManageList(paramMap);
		return new Gson().toJson(resultList);
	}

	/*
	 * method : ContractEndAccountList
	 * comment : 3개월 이내 종료업장 조회
	 */
	@GetMapping("/User/ContractEndAccountList")
	public String ContractEndAccountList() {
		List<Map<String, Object>> resultList = userService.ContractEndAccountList();
		return new Gson().toJson(resultList);
	}

	/*
	 * 메서드 : BirthdayMemberList
	 * 설명 : 이달 생일자 목록 조회
	 */
	@GetMapping("/User/BirthdayMemberList")
	public String BirthdayMemberList() {
		List<Map<String, Object>> resultList = userService.BirthdayMemberList();
		return new Gson().toJson(resultList);
	}
}
