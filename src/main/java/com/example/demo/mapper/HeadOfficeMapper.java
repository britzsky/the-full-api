package com.example.demo.mapper;

import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface HeadOfficeMapper {
	
	int WeekMenuSave(Map<String, Object> paramMap);													// 본사 -> 식단표 저장
	List<Map<String, Object>> WeekMenuList(Map<String, Object> paramMap);							// 본사 -> 식단표 조회
	List<Map<String, Object>> WeekMenuTodayList(Map<String, Object> paramMap);						// 본사 -> 식단표 당일 조회
	int EventSave(Map<String, Object> paramMap);													// 본사 -> 행사달력 저장
	List<Map<String, Object>> EventList(Map<String, Object> paramMap);								// 본사 -> 행사달력 조회
	List<Map<String, Object>> PeopleCountingList(Map<String, Object> paramMap);						// 본사 -> 인원증감 조회
	int ProfitLossTableSave(Map<String, Object> paramMap);											// 본사 -> 손익표 저장
	List<Map<String, Object>> ProfitLossTableList(Map<String, Object> paramMap);					// 본사 -> 손익표 조회
	List<Map<String, Object>> ExcelDaownProfitLossTableList(Map<String, Object> paramMap);			// 본사 -> 손익표 엑셀다운
	List<Map<String, Object>> ExcelDaownMonthProfitLossTableList(Map<String, Object> paramMap);		// 본사 -> 손익표 엑셀다운
	void ProfitLossTotalSave(Map<String, Object> paramMap);											// 손익표 계산 프로시저
	List<Map<String, Object>> AccountManagermentTableList(Map<String, Object> paramMap); 			// 본사 -> 관리표 조회
	List<Map<String, Object>> AccountMappingPurchaseList(Map<String, Object> paramMap); 			// 본사 -> 관리표 -> 거래처 통계
	List<Map<String, Object>> HeadOfficeElectronicPaymentTypeList(Map<String, Object> paramMap); 	// 본사 -> 전자결재 관리 -> 전자결재 타입 리스트 조회
	List<Map<String, Object>> HeadOfficeElectronicPaymentList(Map<String, Object> paramMap); 		// 본사 -> 전자결재 관리 -> 결재 문서 조회
	int HeadOfficeElectronicPaymentSave(Map<String, Object> paramMap);								// 본사 -> 전자결재 관리 -> 소모품 구매 품의서 메인 저장(전자결제 main table)
	int HeadOfficePurchaseRequestSave(Map<String, Object> paramMap);								// 본사 -> 전자결재 관리 -> 소모품 구매 품의서 품목 저장(구매요청 item table)
	List<Map<String, Object>> HeadOfficeDepartmentList(Map<String, Object> paramMap); 				// 본사 -> 전자결재 관리 -> 부서목록 조회
	List<Map<String, Object>> HeadOfficeUserListByDepartment(Map<String, Object> paramMap); 		// 본사 -> 전자결재 관리 -> 부서목록 선택 시, 부서 직원 조회
}
	