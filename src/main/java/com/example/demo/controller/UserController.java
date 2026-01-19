package com.example.demo.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.demo.WebConfig;
import com.example.demo.service.UserService;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

@RestController
public class UserController {

	private final UserService userService;

	@Autowired
	public UserController(UserService userService, WebConfig webConfig) {
		this.userService = userService;
	}

	/*
	 * method : Login
	 * comment : 로그인
	 */
	@PostMapping("/User/Login")
	public String Login(@RequestBody HashMap<String, Object> map) {

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

		String userId = String.valueOf(resultMap.get("user_id"));

		if ("ceo".equals(userId)) {
			obj.addProperty("position_name", "CEO");
		} else if ("britzsky".equals(userId) || "hh2".equals(userId) || "mh2".equals(userId) || "bh4".equals(userId)
				|| "yh2".equals(userId)) {
			obj.addProperty("position_name", "Team Leader");
		} else if ("sy7".equals(userId) || "jr1".equals(userId)) {
			obj.addProperty("position_name", "Part Leader");
		} else {
			obj.addProperty("position_name", "Manager");
		}

		obj.addProperty("position", String.valueOf(resultMap.get("position")));
		obj.addProperty("department", String.valueOf(resultMap.get("department")));
		obj.addProperty("account_id", String.valueOf(resultMap.get("account_id")));
		obj.addProperty("user_name", String.valueOf(resultMap.get("user_name")));

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
	 * method : UserRgt
	 * comment : 사용자 등록
	 */
	@PostMapping("/User/UserRgt")
	public String UserRgt(@RequestBody Map<String, Object> paramMap) {

		int iResult = 0;

		Map<String, Object> info = (Map<String, Object>) paramMap.get("info");
		Map<String, Object> detail = (Map<String, Object>) paramMap.get("detail");

		iResult += userService.UserRgt(info);
		iResult += userService.UserRgtDetail(detail);

		JsonObject obj = new JsonObject();

		if (iResult > 0) {
			obj.addProperty("code", 200);
			obj.addProperty("message", "성공");
		} else {
			obj.addProperty("code", 400);
			obj.addProperty("message", "실패");
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
	 * method : ContractEndAccountList
	 * comment : 3개월 이내 종료업장 조회
	 */
	@GetMapping("/User/ContractEndAccountList")
	public String ContractEndAccountList() {
		List<Map<String, Object>> resultList = userService.ContractEndAccountList();
		return new Gson().toJson(resultList);
	}
}
