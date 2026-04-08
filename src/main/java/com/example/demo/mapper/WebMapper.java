package com.example.demo.mapper;

import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface WebMapper {

	// webmapper: 웹 문의 답변대기 목록 조회
	List<Map<String, Object>> selectContactInquiryPendingList(Map<String, Object> paramMap);

	// webmapper: ERP 웹훅 라우팅 대상자 조회
	List<Map<String, Object>> selectActiveUsersForErpRouting();
}
