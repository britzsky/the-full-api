package com.example.demo.service;

import java.util.List;
import java.util.Map;
import java.util.HashMap;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.mapper.UserMapper;

@Service
public class UserService {

	UserMapper userMapper;

	public UserService(UserMapper userMapper) {
		this.userMapper = userMapper;
	}

	// 로그인
	public Map<String, Object> Login(Map<String, Object> paramMap) {
		return userMapper.Login(paramMap);
	}

	// 공통 키 생성값
	public String NowDateKey() {
		return userMapper.NowDateKey();
	}

	// user_id 존재 여부
	public int CountUserId(Map<String, Object> paramMap) {
		return userMapper.CountUserId(paramMap);
	}

	// 통합/유틸 인력 기존 member_id 조회
	public String SelectUtilMemberIdByUserId(Map<String, Object> paramMap) {
		return userMapper.SelectUtilMemberIdByUserId(paramMap);
	}

	// ✅ 승인대기 목록 (use_yn='N')
	public List<Map<String, Object>> SelectApprovalPendingUsers(Map<String, Object> paramMap) {
		return userMapper.SelectApprovalPendingUsers(paramMap);
	}

	// 사용자 가입 승인 여부
	public int UpdateUserUseYn(Map<String, Object> paramMap) {
		return userMapper.UpdateUserUseYn(paramMap);
	}

	// 사용자 재직/퇴사 여부
	public int UpdateUserDelYn(Map<String, Object> paramMap) {
		return userMapper.UpdateUserDelYn(paramMap);
	}

	// ✅ 승인 저장 (리스트로 들어온 use_yn 반영)
	@Transactional
	public int ApprovalSave(List<Map<String, Object>> list) {
		int updated = 0;
		if (list == null)
			return 0;

		for (Map<String, Object> row : list) {
			String userId = String.valueOf(row.get("user_id"));
			String useYn = String.valueOf(row.get("use_yn")).toUpperCase();

			if (userId == null || userId.trim().isEmpty())
				continue;
			if (!"Y".equals(useYn) && !"N".equals(useYn))
				continue;

			Map<String, Object> param = new HashMap<>();
			param.put("user_id", userId);
			param.put("use_yn", useYn);

			updated += userMapper.UpdateUserUseYn(param);
		}
		return updated;
	}
	
	// 사용자관리 목록 조회
	public List<Map<String, Object>> UserManageList(Map<String, Object> paramMap) {
		return userMapper.UserManageList(paramMap);
	}

	// 사용자 등록
	public int UserRgt(Map<String, Object> paramMap) {
		return userMapper.UserRgt(paramMap);
	}

	// 사용자 상세등록
	public int UserRgtDetail(Map<String, Object> paramMap) {
		return userMapper.UserRgtDetail(paramMap);
	}

	// 통합/유틸 인력 등록(tb_account_members)
	public int UserRgtAccountMember(Map<String, Object> paramMap) {
		return userMapper.UserRgtAccountMember(paramMap);
	}

	// 사용자 + 상세 + (선택)통합/유틸 인력정보 저장
	@Transactional(rollbackFor = Exception.class)
	public int UserRgtAll(Map<String, Object> info, Map<String, Object> detail, Map<String, Object> accountMember) {
		int iResult = 0;
		iResult += userMapper.UserRgt(info);
		iResult += userMapper.UserRgtDetail(detail);

		if (accountMember != null && !accountMember.isEmpty()) {
			iResult += userMapper.UserRgtAccountMember(accountMember);
		}

		return iResult;
	}

	// 직원 정보 조회
	public List<Map<String, Object>> SelectUserInfo(Map<String, Object> paramMap) {
		return userMapper.SelectUserInfo(paramMap);
	}

	// 신사업팀(우선...) 근태관리 조회
	public List<Map<String, Object>> UserRecordSheetList(Map<String, Object> paramMap) {
		return userMapper.UserRecordSheetList(paramMap);
	}

	// 신사업팀(우선...) 근태관리 직원정보 조회
	public List<Map<String, Object>> UserMemberList(Map<String, Object> paramMap) {
		return userMapper.UserMemberList(paramMap);
	}

	// 3개월 이내 종료업장 조회
	public List<Map<String, Object>> ContractEndAccountList() {
		return userMapper.ContractEndAccountList();
	}
}
