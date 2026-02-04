package com.example.demo.mapper;

import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AccountMapper {
	
	String NowDateKey();
	List<Map<String, Object>> AccountList(int accountType);												// 거래처 목록
	List<Map<String, Object>> AccountListV2(int accountType);											// 거래처 목록
	List<Map<String, Object>> AccountDirectList();														// 신사업 -> 직영점 목록
	List<Map<String, Object>> AccountMemberList();
	List<Map<String, Object>> AccountUtilMemberList();													// 직원관리 -> 유틸직원 조회
	List<Map<String, Object>> AccountUtilMappingList(Map<String, Object> paramMap);						// 직원관리 -> 유틸 직원 매핑정보 조회
	int AccountUtilMemberMappingSave(Map<String, Object> paramMap);										// 직원관리 -> 유틸 직원 매핑정보 저장
	List<Map<String, Object>> AccountTallySheetList(Map<String, Object> paramMap);						// 거래처 -> 집계표
	int AccountSave(Map<String, Object> paramMap);														// 거래처 -> 집계표 저장(예정)
	List<Map<String, Object>> AccountRecordDispatchList(Map<String, Object> paramMap); 					// 출근부 -> 파출정보
	List<Map<String, Object>> AccountRecordMemberList(Map<String, Object> paramMap); 					// 출근부 -> 직원정보
	List<Map<String, Object>> AccountRecordSheetList(Map<String, Object> paramMap); 					// 출근부 -> 출근현황
	List<Map<String, Object>> AccountMemberRecordTime(Map<String, Object> paramMap);					// 출근부 -> 출근현황 출퇴근 시간 조회
	int AccountMemberRecordSave(Map<String, Object> paramMap);											// 출근부 -> 상용출근 정보 저장
	int AccountMemberRecRecordSave(Map<String, Object> paramMap);										// 출근부 -> 채용현황 출근 정보 저장
	int AccountDispatchRecordSave(Map<String, Object> paramMap);										// 출근부 -> 파출출근 정보 저장
	int AccountDispatchMemberSave(Map<String, Object> paramMap);										// 출근부 -> 파출직원 정보 저장
	List<Map<String, Object>> AccountPropertiesList(Map<String, Object> paramMap); 						// 거래처 -> 기물리스트
	List<Map<String, Object>> AccountInfoList(Map<String, Object> paramMap); 							// 거래처 -> 거래처 상세
	List<Map<String, Object>> AccountInfoList_2(Map<String, Object> paramMap); 							// 거래처 -> 거래처 상세
	List<Map<String, Object>> AccountInfoList_3(Map<String, Object> paramMap); 							// 거래처 -> 거래처 상세
	List<Map<String, Object>> AccountInfoList_4(Map<String, Object> paramMap); 							// 거래처 -> 거래처 상세
	List<Map<String, Object>> AccountInfoList_5(Map<String, Object> paramMap); 							// 거래처 -> 거래처 상세
	int AccountInfoSave(Map<String, Object> paramMap);													// 거래처 -> 거래처 상세 저장
	int AccountDietPriceHistorySave(Map<String, Object> paramMap);										// 거래처 -> 거래처 상세 -> 식단가 변경내역 저장
	List<Map<String, Object>>AccountDietPriceHistoryList(Map<String, Object> paramMap);					// 거래처 -> 거래처 상세 -> 식단가 변경내역 조회
	List<Map<String, Object>> AccountBusinessImgList(Map<String, Object> paramMap); 					// 거래처 -> 거래처 상세 이미지 조회
	int insertOrUpdateFile(Map<String, Object> paramMap); 												// 거래처 -> 거래처 상세 이미지 업로드
	List<Map<String, Object>> AccountDeadlineBalanceList(Map<String, Object> paramMap); 				// 회계 -> 매출마감/미수잔액 조회
	List<Map<String, Object>> AccountDepositHistoryList(Map<String, Object> paramMap); 					// 회계 -> 매출마감/미수잔액 입금내역 조회
	int AccountDeadlineBalanceSave(Map<String, Object> paramMap);										// 회계 -> 매출마감/미수잔액 저장
	int AccountBalancePriceSave(Map<String, Object> paramMap);											// 회계 -> 매출마감/미수잔액 총 미수금액 저장
	int AccountDepositHistorySave(Map<String, Object> paramMap);										// 회계 -> 매출마감/미수잔액 입금내역 저장
	List<Map<String, Object>> AccountDeadlineDifferencePriceSearch(Map<String, Object> paramMap); 		// 회계 -> 타입별 차액 조회
	int AccountDeadlineFilesSave(Map<String, Object> paramMap);											// 회계 -> 마감자료 저장
	List<Map<String, Object>> AccountDeadlineFilesList(Map<String, Object> paramMap); 					// 회계 -> 마감자료 조회
	int AccountIssueSave(Map<String, Object> paramMap);													// 운영,회계 -> 거래처 이슈 저장
	List<Map<String, Object>> AccountIssueList(Map<String, Object> paramMap); 							// 운영,회계 -> 거래처 이슈 조회
	// 배치성 데이터
	List<Map<String, Object>> BatchForPayBack(Map<String, Object> paramMap); 							// 본사 -> 관리표 -> 손익표 (판장금)
	int AccountAnnualLeaveLedgerSave(Map<String, Object> paramMap);										// 출근부 -> 연차관리 저장
	int AccountOverTimeLedgerSave(Map<String, Object> paramMap);										// 출근부 -> 초과관리 저장
	List<Map<String, Object>> AccountMappingList(String account_id); 									// 현장 -> 집계표 -> 영수증 매장 확인 조회
	int AccountPurchaseSave(Map<String, Object> paramMap);												// 현장 -> 집계표 -> 매입집계 저장
	int AccountPurchaseDetailSave(Map<String, Object> paramMap);										// 현장 -> 집계표 -> 매입집계 상세 저장
	List<Map<String, Object>> AccountPurchaseTallyList(Map<String, Object> paramMap); 					// 회계 -> 매입 -> 매입마감 조회
	List<Map<String, Object>> AccountPurchaseDetailList(Map<String, Object> paramMap); 					// 회계 -> 매입 -> 매입집계 조회
	List<Map<String, Object>> AccountPurchaseDetailList_tmp(Map<String, Object> paramMap); 				// 회계 -> 매입 -> 매입집계(임시) 조회
	List<Map<String, Object>> HeadOfficeCorporateCardList(Map<String, Object> paramMap); 				// 회계 -> 본사 법인카드 목록 조회
	List<Map<String, Object>> HeadOfficeCorporateCardPaymentList(Map<String, Object> paramMap); 		// 회계 -> 본사 법인카드 결제내역 조회
	List<Map<String, Object>> HeadOfficeCorporateCardPaymentDetailList(Map<String, Object> paramMap); 	// 회계 -> 본사 법인카드 결제 상세내역 조회
	int HeadOfficeCorporateCardSave(Map<String, Object> paramMap);										// 회계 -> 본사 법인카드 저장
	int HeadOfficeCorporateCardPaymentSave(Map<String, Object> paramMap);								// 회계 -> 본사 법인카드 결제내역 저장
	int HeadOfficeCorporateCardPaymentDetailLSave(Map<String, Object> paramMap);						// 회계 -> 본사 법인카드 상세내역 저장
	List<Map<String, Object>> AccountCorporateCardList(Map<String, Object> paramMap); 					// 회계 -> 본사 법인카드 목록 조회
	List<Map<String, Object>> AccountCorporateCardPaymentList(Map<String, Object> paramMap); 			// 회계 -> 본사 법인카드 결제내역 조회
	List<Map<String, Object>> AccountCorporateCardPaymentDetailList(Map<String, Object> paramMap); 		// 회계 -> 본사 법인카드 결제 상세내역 조회
	int AccountCorporateCardSave(Map<String, Object> paramMap);											// 회계 -> 본사 법인카드 저장
	int AccountCorporateCardPaymentSave(Map<String, Object> paramMap);									// 회계 -> 본사 법인카드 결제내역 저장
	int AccountCorporateCardPaymentDetailLSave(Map<String, Object> paramMap);							// 회계 -> 본사 법인카드 상세내역 저장
	void TallySheetCorporateCardPaymentSave(Map<String, Object> paramMap);								// 회계 -> 현장 법인카드 집계표 적용
	void TallySheetCorporateCardPaymentSaveV2(Map<String, Object> paramMap);							// 회계 -> 본사 법인카드 집계표 적용
	List<Map<String, Object>> AccountPurchaseTallyPaymentList(Map<String, Object> paramMap); 			// 집계표 -> 결제 리스트 조회
	void TallySheetPaymentSave(Map<String, Object> paramMap);											// 집계표 -> 집계표 적용
}
