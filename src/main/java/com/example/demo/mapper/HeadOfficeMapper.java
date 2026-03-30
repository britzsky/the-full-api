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
	List<Map<String, Object>> ExcelDownProfitLossTableList(Map<String, Object> paramMap);			// 본사 -> 손익표 엑셀다운
	List<Map<String, Object>> ExcelDownMonthProfitLossTableList(Map<String, Object> paramMap);		// 본사 -> 손익표 엑셀다운
	void ProfitLossTotalSave(Map<String, Object> paramMap);											// 손익표 계산 프로시저
	List<Map<String, Object>> AccountManagermentTableList(Map<String, Object> paramMap); 			// 본사 -> 관리표 조회
	List<Map<String, Object>> AccountMappingPurchaseList(Map<String, Object> paramMap); 			// 본사 -> 관리표 -> 거래처 통계
	List<Map<String, Object>> AccountMappingPurchaseDetailList(Map<String, Object> paramMap); 	// 본사 -> 관리표 -> 거래처 통계(업장별 상세)
	List<Map<String, Object>> HeadOfficeElectronicPaymentTypeList(Map<String, Object> paramMap); 	// 본사 -> 전자결재 관리 -> 전자결재 타입 리스트 조회
	List<Map<String, Object>> HeadOfficeElectronicPaymentList(Map<String, Object> paramMap); 		// 본사 -> 전자결재 관리 -> 결재 문서 조회
	int HeadOfficeElectronicPaymentSave(Map<String, Object> paramMap);								// 본사 -> 전자결재 관리 -> 소모품 구매 품의서 메인 저장(전자결제 main table)
	int ElectronicPaymentSave(Map<String, Object> paramMap);											// 본사 -> 전자결재 관리 -> 결재 문서 메인 저장
	int HeadOfficePurchaseRequestDeleteByPaymentId(Map<String, Object> paramMap);					// 본사 -> 전자결재 관리 -> 문서번호 기준 품목 초기화
	int HeadOfficePurchaseRequestSave(Map<String, Object> paramMap);								// 본사 -> 전자결재 관리 -> 소모품 구매 품의서 품목 저장(구매요청 item table)
	int HeadOfficePurchaseRequestBulkSave(Map<String, Object> paramMap);							// 본사 -> 전자결재 관리 -> 품목 일괄 저장(배치)
	int HeadOfficeDraftDeleteByPaymentId(Map<String, Object> paramMap);							// 본사 -> 전자결재 관리 -> 문서번호 기준 기안서 본문 초기화
	int HeadOfficeDraftSave(Map<String, Object> paramMap);										// 본사 -> 전자결재 관리 -> 기안서 본문 저장
	int HeadOfficePaymentDocDeleteByPaymentId(Map<String, Object> paramMap);						// 본사 -> 전자결재 관리 -> 문서번호 기준 지출결의서 본문 초기화
	int HeadOfficePaymentDocSave(Map<String, Object> paramMap);									// 본사 -> 전자결재 관리 -> 지출결의서 본문 저장
	List<Map<String, Object>> ElectronicPaymentFileList(Map<String, Object> paramMap);			// 본사 -> 전자결재 관리 -> 지출결의서 첨부 이미지 조회
	int ElectronicPaymentFileDelete(Map<String, Object> paramMap);								// 본사 -> 전자결재 관리 -> 지출결의서 첨부 이미지 삭제
	Integer GetMaxElectronicPaymentImageOrder(Map<String, Object> paramMap);						// 본사 -> 전자결재 관리 -> 지출결의서 첨부 이미지 순번 최대값 조회
	void SaveElectronicPaymentFile(Map<String, Object> paramMap);								// 본사 -> 전자결재 관리 -> 지출결의서 첨부 이미지 저장
	List<Map<String, Object>> ElectronicPaymentManageList(Map<String, Object> paramMap);			// 본사 -> 전자결재 관리 -> 내 문서/결재대상 목록 조회
	Map<String, Object> ElectronicPaymentManageMain(Map<String, Object> paramMap);					// 본사 -> 전자결재 관리 -> 문서 메인 조회
	List<Map<String, Object>> ElectronicPaymentManageItems(Map<String, Object> paramMap);			// 본사 -> 전자결재 관리 -> 문서 품목 조회
	int ElectronicPaymentManageTmSignSave(Map<String, Object> paramMap);							// 본사 -> 전자결재 관리 -> 팀장 결재/반려 저장
	int ElectronicPaymentManageCeoSignSave(Map<String, Object> paramMap);							// 본사 -> 전자결재 관리 -> 대표 결재/반려 저장
	int ElectronicPaymentManagePayerSignSave(Map<String, Object> paramMap);							// 본사 -> 전자결재 관리 -> 결재자 결재/반려 저장
	int ElectronicPaymentItemBuyYnSave(Map<String, Object> paramMap);								// 본사 -> 전자결재 관리 -> 소모품 구매여부 저장
	List<Map<String, Object>> ElectronicPaymentNotificationList(Map<String, Object> paramMap);		// 본사 -> 전자결재 알림 목록
	int ElectronicPaymentNotificationReadSave(Map<String, Object> paramMap);							// 본사 -> 전자결재 알림 읽음 처리
	List<Map<String, Object>> HeadOfficeDepartmentList(Map<String, Object> paramMap); 				// 본사 -> 전자결재 관리 -> 부서목록 조회
	List<Map<String, Object>> HeadOfficeUserListByDepartment(Map<String, Object> paramMap); 		// 본사 -> 전자결재 관리 -> 부서목록 선택 시, 부서 직원 조회
}
	
