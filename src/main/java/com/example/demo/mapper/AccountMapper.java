package com.example.demo.mapper;

import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AccountMapper {
	
	List<Map<String, Object>> AccountList(int accountType);											// 거래처 목록
	List<Map<String, Object>> AccountDirectList();													// 신사업 -> 직영점 목록
	List<Map<String, Object>> AccountMemberList();
	List<Map<String, Object>> AccountTallySheetList(Map<String, Object> paramMap);					// 거래처 -> 집계표
	int AccountSave(Map<String, Object> paramMap);													// 거래처 -> 집계표 저장(예정)
	List<Map<String, Object>> AccountRecordDispatchList(Map<String, Object> paramMap); 				// 출근부 -> 파출정보
	List<Map<String, Object>> AccountRecordMemberList(Map<String, Object> paramMap); 				// 출근부 -> 직원정보
	List<Map<String, Object>> AccountRecordSheetList(Map<String, Object> paramMap); 				// 출근부 -> 출근현황
	List<Map<String, Object>> AccountMemberRecordTime(Map<String, Object> paramMap);				// 출근부 -> 출근현황 출퇴근 시간 조회
	int AccountMemberRecordSave(Map<String, Object> paramMap);										// 출근부 -> 상용출근 정보 저장
	int AccountDispatchRecordSave(Map<String, Object> paramMap);									// 출근부 -> 파출출근 정보 저장
	int AccountDispatchMemberSave(Map<String, Object> paramMap);									// 출근부 -> 파출직원 정보 저장
	List<Map<String, Object>> AccountPropertiesList(Map<String, Object> paramMap); 					// 거래처 -> 기물리스트
	List<Map<String, Object>> AccountInfoList(Map<String, Object> paramMap); 						// 거래처 -> 거래처 상세
	List<Map<String, Object>> AccountInfoList_2(Map<String, Object> paramMap); 						// 거래처 -> 거래처 상세
	List<Map<String, Object>> AccountInfoList_3(Map<String, Object> paramMap); 						// 거래처 -> 거래처 상세
	List<Map<String, Object>> AccountInfoList_4(Map<String, Object> paramMap); 						// 거래처 -> 거래처 상세
	List<Map<String, Object>> AccountInfoList_5(Map<String, Object> paramMap); 						// 거래처 -> 거래처 상세
	int AccountInfoSave(Map<String, Object> paramMap);												// 거래처 -> 거래처 저장
	List<Map<String, Object>> AccountBusinessImgList(Map<String, Object> paramMap); 				// 거래처 -> 거래처 상세 이미지 조회
	int insertOrUpdateFile(Map<String, Object> paramMap); 											// 거래처 -> 거래처 상세 이미지 업로드
	List<Map<String, Object>> AccountDeadlineBalanceList(Map<String, Object> paramMap); 			// 회계 -> 매출마감/미수잔액 조회
	List<Map<String, Object>> AccountDepositHistoryList(Map<String, Object> paramMap); 				// 회계 -> 매출마감/미수잔액 입금내역 조회
	int AccountDeadlineBalanceSave(Map<String, Object> paramMap);									// 회계 -> 매출마감/미수잔액 저장
	int AccountBalancePriceSave(Map<String, Object> paramMap);										// 회계 -> 매출마감/미수잔액 총 미수금액 저장
	int AccountDepositHistorySave(Map<String, Object> paramMap);									// 회계 -> 매출마감/미수잔액 입금내역 저장
	List<Map<String, Object>> AccountDeadlineDifferencePriceSearch(Map<String, Object> paramMap); 	// 회계 -> 타입별 차액 조회
	int AccountDeadlineFilesSave(Map<String, Object> paramMap);										// 회계 -> 마감자료 저장
	List<Map<String, Object>> AccountDeadlineFilesList(Map<String, Object> paramMap); 				// 회계 -> 마감자료 조회
	int AccountIssueSave(Map<String, Object> paramMap);												// 운영,회계 -> 거래처 이슈 저장
	List<Map<String, Object>> AccountIssueList(Map<String, Object> paramMap); 						// 운영,회계 -> 거래처 이슈 조회
	// 배치성 데이터
	List<Map<String, Object>> BatchForPayBack(Map<String, Object> paramMap); 						// 본사 -> 관리표 -> 손익표 (판장금)
}
