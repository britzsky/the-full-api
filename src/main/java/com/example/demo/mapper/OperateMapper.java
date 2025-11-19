package com.example.demo.mapper;

import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface OperateMapper {
	
	List<Map<String, Object>> TallySheetList(Map<String, Object> paramMap);				// 급식사업부 -> 운영관리 -> 집계표 조회
	
	String NowDateKey();
	
	int TallyNowMonthSave(Map<String, Object> paramMap);								// 급식사업부 -> 운영관리 -> 본월 집계표 저장
	int TallyBeforeMonthSave(Map<String, Object> paramMap);								// 급식사업부 -> 운영관리 -> 이월 집계표 저장
	int PropertiesSave(Map<String, Object> paramMap);									// 급식사업부 -> 운영관리 -> 기물리스트 저장
	List<Map<String, Object>> PropertiesList(Map<String, Object> paramMap);				// 급식사업부 -> 운영관리 -> 기물리스트 조회
	List<Map<String, Object>> HygieneList(Map<String, Object> paramMap);				// 급식사업부 -> 운영관리 -> 위생관리 조회
	int HygieneSave(Map<String, Object> paramMap);										// 급식사업부 -> 운영관리 -> 위생관리 저장
	Map<String, Object> HandOverSearch(Map<String, Object> paramMap);					// 급식사업부 -> 운영관리 -> 인수인계서 조회
	int HandOverSave(Map<String, Object> paramMap);										// 급식사업부 -> 운영관리 -> 인수인계서 저장
	List<Map<String, Object>> AccountMappingList(Map<String, Object> paramMap);			// 급식사업부 -> 운영관리 -> 집계표 Modal 거래처 매핑 조회
	List<Map<String, Object>> AccountMappingV2List(Map<String, Object> paramMap);		// 급식사업부 -> 운영관리 -> 집계표 Modal 거래처 매핑 조회 V2
	int AccountMappingSave(Map<String, Object> paramMap);								// 급식사업부 -> 운영관리 -> 집계표 Modal 거래처 매핑 저장
	int AccountRetailBusinessSave(Map<String, Object> paramMap);						// 급식사업부 -> 운영관리 -> 집계표 Modal 거래처 저장
	List<Map<String, Object>> AccountRetailBusinessList(Map<String, Object> paramMap);	// 급식사업부 -> 운영관리 -> 고객사관리 -> 거래처관리 조회
	List<Map<String, Object>> AccountMembersFilesList(Map<String, Object> paramMap);	// 급식사업부 -> 운영관리 -> 고객사관리 -> 면허증 및 자격증관리 조회
	List<Map<String, Object>> AccountTypeForFileList(Map<String, Object> paramMap);		// 급식사업부 -> 운영관리 -> 고객사관리 -> 면허증 및 자격증관리 타입별 조회
	int AccountMembersFilesSave(Map<String, Object> paramMap);							// 급식사업부 -> 운영관리 -> 고객사관리 -> 면허증 및 자격증관리 저장
	List<Map<String, Object>> AccountSubRestaurantList(Map<String, Object> paramMap);	// 급식사업부 -> 운영관리 -> 고객사관리 -> 대체업체 조회
	int AccountSubRestaurantSave(Map<String, Object> paramMap);							// 급식사업부 -> 운영관리 -> 고객사관리 -> 대체업체 저장
	List<Map<String, Object>> AccountMemberSheetList(Map<String, Object> paramMap); 	// 급식사업부 -> 운영관리 -> 고객사관리 -> 인사기록카드 조회
	List<Map<String, Object>> AccountMemberAllList(Map<String, Object> paramMap); 		// 급식사업부 -> 운영관리 -> 직원관리 조회
	int AccountMembersSave(Map<String, Object> paramMap);								// 급식사업부 -> 운영관리 -> 직원관리 저장
	List<Map<String, Object>> AccountDinnersNumberList(Map<String, Object> paramMap); 	// 급식사업부 -> 운영관리 -> 거래처관리 -> 식수현황
	int AccountDinnersNumberSave(Map<String, Object> paramMap);							// 급식사업부 -> 운영관리 -> 거래처관리 -> 식수현황 저장
}
