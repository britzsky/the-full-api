package com.example.demo.service;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

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
	// 사용자 등록
	public int UserRgt(Map<String, Object> paramMap) {
		return userMapper.UserRgt(paramMap);
	};
	// 신사업팀(우선...) 근태관리 조회
	public List<Map<String, Object>> UserRecordSheetList(Map<String, Object> paramMap) {
		return userMapper.UserRecordSheetList(paramMap);
	}
	// 신사업팀(우선...) 근태관리 직원정보 조회
	public List<Map<String, Object>> UserMemberList(Map<String, Object> paramMap) {
		return userMapper.UserMemberList(paramMap);
	}
}
