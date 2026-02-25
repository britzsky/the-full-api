package com.example.demo.mapper;

import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface OperateMapper {
	
	List<Map<String, Object>> TallySheetList(Map<String, Object> paramMap);						// 급식사업부 -> 운영관리 -> 집계표 조회
	
	String NowDateKey();
	
	int TallyNowMonthSave(Map<String, Object> paramMap);										// 급식사업부 -> 운영관리 -> 본월 집계표 저장
	int TallyBeforeMonthSave(Map<String, Object> paramMap);										// 급식사업부 -> 운영관리 -> 이월 집계표 저장
	int PropertiesSave(Map<String, Object> paramMap);											// 급식사업부 -> 운영관리 -> 기물리스트 저장
	List<Map<String, Object>> PropertiesList(Map<String, Object> paramMap);						// 급식사업부 -> 운영관리 -> 기물리스트 조회
	List<Map<String, Object>> HygieneList(Map<String, Object> paramMap);						// 급식사업부 -> 운영관리 -> 위생관리 조회
	int HygieneSave(Map<String, Object> paramMap);												// 급식사업부 -> 운영관리 -> 위생관리 저장
	Map<String, Object> HandOverSearch(Map<String, Object> paramMap);							// 급식사업부 -> 운영관리 -> 인수인계서 조회
	int HandOverSave(Map<String, Object> paramMap);												// 급식사업부 -> 운영관리 -> 인수인계서 저장
	List<Map<String, Object>> AccountMappingList(Map<String, Object> paramMap);					// 급식사업부 -> 운영관리 -> 집계표 Modal 거래처 매핑 조회
	List<Map<String, Object>> AccountMappingV2List(Map<String, Object> paramMap);				// 급식사업부 -> 운영관리 -> 집계표 Modal 거래처 매핑 조회 V2
	int AccountMappingSave(Map<String, Object> paramMap);										// 급식사업부 -> 운영관리 -> 집계표 Modal 거래처 매핑 저장
	int AccountRetailBusinessSave(Map<String, Object> paramMap);								// 급식사업부 -> 운영관리 -> 집계표 Modal 거래처 저장
	List<Map<String, Object>> AccountRetailBusinessList(Map<String, Object> paramMap);			// 급식사업부 -> 운영관리 -> 고객사관리 -> 거래처관리 조회
	List<Map<String, Object>> AccountMembersFilesList(Map<String, Object> paramMap);			// 급식사업부 -> 운영관리 -> 고객사관리 -> 면허증 및 자격증관리 조회
	List<Map<String, Object>> AccountTypeForFileList(Map<String, Object> paramMap);				// 급식사업부 -> 운영관리 -> 고객사관리 -> 면허증 및 자격증관리 타입별 조회
	int AccountMembersFilesSave(Map<String, Object> paramMap);									// 급식사업부 -> 운영관리 -> 고객사관리 -> 면허증 및 자격증관리 저장
	List<Map<String, Object>> AccountSubRestaurantList(Map<String, Object> paramMap);			// 급식사업부 -> 운영관리 -> 고객사관리 -> 대체업체 조회
	int AccountSubRestaurantSave(Map<String, Object> paramMap);									// 급식사업부 -> 운영관리 -> 고객사관리 -> 대체업체 저장
	List<Map<String, Object>> AccountMemberWorkSystemList(); 									// 운영/인사 근무형태 조회
	int AccountMemberWorkSystemSave(Map<String, Object> paramMap);								// 운영/인사 근무형태 저장
	List<Map<String, Object>> AccountMemberSheetList(Map<String, Object> paramMap); 			// 급식사업부 -> 운영관리 -> 고객사관리 -> 인사기록카드 조회
	List<Map<String, Object>> AccountMemberAllList(Map<String, Object> paramMap); 				// 급식사업부 -> 운영->현장관리, 인사->현장관리 -> 직원관리 조회
	List<Map<String, Object>> AccountMemberAllListExcel(Map<String, Object> paramMap);       	// 급식사업부 -> 운영->현장관리, 인사->현장관리 -> 직원관리 전체엑셀 조회
	int AccountMembersSave(Map<String, Object> paramMap);										// 급식사업부 -> 운영->현장관리, 인사->현장관리 -> 직원관리 저장
	List<Map<String, Object>> AccountRecMemberList(Map<String, Object> paramMap); 				// 급식사업부 -> 운영->채용관리 -> 현장 채용현황 조회
	int AccountRecMembersSave(Map<String, Object> paramMap);									// 급식사업부 -> 운영->채용관리 -> 현장 채용현황 저장
	int AccountRecordSetRecRecordDataSave(Map<String, Object> paramMap);						// 급식사업부 -> 운영->현장관리 채용확정 시, 출근부 적용
	int AccountRecRecordDataDelete(Map<String, Object> paramMap);								// 급식사업부 -> 운영->현장관리 채용확정 또는 채용취소 시, 채용현황 출근부 삭제 적용
	List<Map<String, Object>> AccountDispatchMemberAllList(Map<String, Object> paramMap); 		// 급식사업부 -> 운영->현장관리, 인사->현장관리 -> 파출관리 조회
	int AccountDispatchMembersSave(Map<String, Object> paramMap);								// 급식사업부 -> 운영->현장관리, 인사->현장관리 -> 파출관리 저장
	List<Map<String, Object>> AccountDinnersNumberList(Map<String, Object> paramMap); 			// 급식사업부 -> 운영관리 -> 거래처관리 -> 식수현황
	int AccountDinnersNumberSave(Map<String, Object> paramMap);									// 급식사업부 -> 운영관리 -> 거래처관리 -> 식수현황 저장
	void BudgetTotalSave(Map<String, Object> paramMap);											// 예산 계산 프로시저
	List<Map<String, Object>> BudgetManageMentList(Map<String, Object> paramMap); 				// 급식사업부 -> 운영관리 -> 예산관리 조회
	int BudgetTableSave(Map<String, Object> paramMap);											// 급식사업부 -> 운영관리 -> 예산관리 저장
	List<Map<String, Object>> BudgetStandardList(Map<String, Object> paramMap); 				// 급식사업부 -> 운영관리 -> 예산관리(예산기준) 조회
	List<Map<String, Object>> MealsNumberList(Map<String, Object> paramMap); 					// 급식사업부 -> 운영관리 -> 예산관리(배식횟수) 조회
	List<Map<String, Object>> AnnualLeaveList(Map<String, Object> paramMap); 					// 급식사업부 -> 운영관리 -> 현장관리 -> 근태관리 -> 연차 정보 조회
	List<Map<String, Object>> OverTimeList(Map<String, Object> paramMap); 						// 급식사업부 -> 운영관리 -> 현장관리 -> 근태관리 -> 초과근무 조회
	List<Map<String, Object>> OperateMemberList();												// 운영관리 -> 일정관리 -> 운영팀 조회
	int OperateScheduleSave(Map<String, Object> paramMap);										// 운영관리 -> 일정관리 저장
	List<Map<String, Object>> OperateScheduleList(Map<String, Object> paramMap);				// 운영관리 -> 일정관리 조회
	List<Map<String, Object>> OperateScheduleTodayList(Map<String, Object> paramMap);			// 메인화면 -> 운영팀 당일 일정 조회
	List<Map<String, Object>> AccountRecordStandardList(Map<String, Object> paramMap);			// 운영 -> 긴급인력 -> 업장별 요일 인력 기준 조회
	int AccountRecordStandardSave(Map<String, Object> paramMap);								// 운영 -> 긴급인력 -> 업장별 요일 인력 기준 저장
	List<Map<String, Object>> TallySheetPointList(Map<String, Object> paramMap);				// 집계표 -> 셀 포인트 조회
	int TallySheetPointSave(Map<String, Object> paramMap);										// 집계표 -> 셀 포인트 저장
	List<Map<String, Object>> TallySheetUseList(Map<String, Object> paramMap);					// 집계표 -> type 입력가능여부 조회
	int TallySheetUseSave(Map<String, Object> paramMap);										// 집계표 -> type 입력가능여부 저장
	List<Map<String, Object>> SidoList(Map<String, Object> paramMap);							// 긴급인력관리 -> 근무가능지역 관리 -> 시도 조회
	List<Map<String, Object>> SigunguList(Map<String, Object> paramMap);						// 긴급인력관리 -> 근무가능지역 관리 -> 시군구 조회
	List<Map<String, Object>> EupmyeondongList(Map<String, Object> paramMap);					// 긴급인력관리 -> 근무가능지역 관리 -> 읍면동 조회
	List<Map<String, Object>> RootList(Map<String, Object> paramMap);							// 긴급인력관리 -> 근무가능지역 관리 -> 권역루트 조회
	int RootSave(Map<String, Object> paramMap);													// 긴급인력관리 -> 근무가능지역 관리 -> 권역루트 저장
	List<Map<String, Object>> RecordSituationList(Map<String, Object> paramMap);				// 긴급인력관리 -> 현재 출근부 현황 조회
	List<Map<String, Object>> RecordStandardList(Map<String, Object> paramMap);					// 긴급인력관리 -> 필수인력 조회
	List<Map<String, Object>> FieldPersonMasterList(Map<String, Object> paramMap);				// 긴급인력관리 -> 인력정보 조회
	List<Map<String, Object>> PersonToRootList(Map<String, Object> paramMap);					// 긴급인력관리 -> 인력, 루트 매핑 조회
	List<Map<String, Object>> EmergencyPersonList(Map<String, Object> paramMap);				// 긴급인력관리 -> 긴급인력 조회
	int PersonToRootSave(Map<String, Object> paramMap);											// 긴급인력관리 -> 인력, 근무가능지역 매핑 저장
	int FieldPersonSave(Map<String, Object> paramMap);											// 긴급인력관리 -> 인력정보 저장
}
