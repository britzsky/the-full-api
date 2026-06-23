package com.example.demo.mapper;

import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface HeadOfficeMapper {
	
	int WeekMenuSave(Map<String, Object> paramMap);													// 본사 -> 주간식단 저장
	List<Map<String, Object>> WeekMenuList(Map<String, Object> paramMap);							// 본사 -> 주간식단 목록 조회
	List<Map<String, Object>> WeekMenuTodayList(Map<String, Object> paramMap);						// 본사 -> 주간식단 오늘 조회
	int EventSave(Map<String, Object> paramMap);													// 본사 -> 이벤트 저장
	List<Map<String, Object>> EventList(Map<String, Object> paramMap);								// 본사 -> 이벤트 목록 조회
	List<Map<String, Object>> PeopleCountingList(Map<String, Object> paramMap);						// 본사 -> 인원카운팅 목록 조회
	int ProfitLossTableSave(Map<String, Object> paramMap);											// 본사 -> 손익표 저장
	int PersonCostExcelSave(Map<String, Object> paramMap);										// 본사 -> 인건비 엑셀 일괄 저장
	Map<String, Object> getProfitLossPersonCost(Map<String, Object> paramMap);					// 본사 -> 손익표 인건비 조회
	int savePersonCostHistory(Map<String, Object> paramMap);										// 본사 -> 인건비 변경 히스토리 저장
	List<Map<String, Object>> ProfitLossTableList(Map<String, Object> paramMap);					// 본사 -> 손익표 목록 조회
	List<Map<String, Object>> ExcelDownProfitLossTableList(Map<String, Object> paramMap);			// 본사 -> 손익표 엑셀 다운
	List<Map<String, Object>> ExcelDownMonthProfitLossTableList(Map<String, Object> paramMap);		// 본사 -> 손익표 월별 엑셀 다운
	void ProfitLossTotalSave(Map<String, Object> paramMap);											// 손익표 계산 저장
	List<Map<String, Object>> AccountManagermentTableList(Map<String, Object> paramMap); 			// 본사 -> 회계관리 목록 조회
	List<Map<String, Object>> AccountMappingPurchaseList(Map<String, Object> paramMap); 			// 본사 -> 회계관리 -> 구매맵핑
	List<Map<String, Object>> AccountMappingPurchaseDetailList(Map<String, Object> paramMap); 	// 본사 -> 회계관리 -> 구매맵핑(날짜별 상세)
	List<Map<String, Object>> HeadOfficeElectronicPaymentTypeList(Map<String, Object> paramMap); 	// 본사 -> 전자결재 관리 -> 전자결재 문서타입 목록 조회
	List<Map<String, Object>> HeadOfficeElectronicPaymentList(Map<String, Object> paramMap); 		// 본사 -> 전자결재 관리 -> 결재 문서 목록 조회
	int HeadOfficeElectronicPaymentSave(Map<String, Object> paramMap);								// 본사 -> 전자결재 관리 -> 구매요청 품의서 메인 저장(전자결재 main table)
	int ElectronicPaymentSave(Map<String, Object> paramMap);											// 본사 -> 전자결재 관리 -> 결재 문서 메인 저장
	int HeadOfficePurchaseRequestDeleteByPaymentId(Map<String, Object> paramMap);					// 본사 -> 전자결재 관리 -> 문서번호 기준 구매요청 품목 삭제
	int HeadOfficePurchaseRequestSave(Map<String, Object> paramMap);								// 본사 -> 전자결재 관리 -> 구매요청 품의서 품목 저장(구매요청 item table)
	int HeadOfficePurchaseRequestBulkSave(Map<String, Object> paramMap);							// 본사 -> 전자결재 관리 -> 품목 일괄 저장(반복)
	int HeadOfficeDraftDeleteByPaymentId(Map<String, Object> paramMap);							// 본사 -> 전자결재 관리 -> 문서번호 기준 기안서 본문 삭제
	int HeadOfficeDraftSave(Map<String, Object> paramMap);										// 본사 -> 전자결재 관리 -> 기안서 본문 저장
	int HeadOfficePaymentDocDeleteByPaymentId(Map<String, Object> paramMap);						// 본사 -> 전자결재 관리 -> 문서번호 기준 지출결의서 본문 삭제
	int HeadOfficePaymentDocSave(Map<String, Object> paramMap);									// 본사 -> 전자결재 관리 -> 지출결의서 본문 저장
	List<Map<String, Object>> ElectronicPaymentFileList(Map<String, Object> paramMap);			// 본사 -> 전자결재 관리 -> 첨부파일 목록 조회
	int ElectronicPaymentFileDelete(Map<String, Object> paramMap);								// 본사 -> 전자결재 관리 -> 첨부파일 삭제
	Integer GetMaxElectronicPaymentImageOrder(Map<String, Object> paramMap);						// 본사 -> 전자결재 관리 -> 첨부파일 최대 image_order 조회
	void SaveElectronicPaymentFile(Map<String, Object> paramMap);								// 본사 -> 전자결재 관리 -> 첨부파일 저장
	List<Map<String, Object>> ElectronicPaymentManageList(Map<String, Object> paramMap);			// 본사 -> 전자결재 관리 -> 내문서/결재대기 목록 조회
	Map<String, Object> ElectronicPaymentManageMain(Map<String, Object> paramMap);					// 본사 -> 전자결재 관리 -> 문서 메인 조회
	List<Map<String, Object>> ElectronicPaymentManageItems(Map<String, Object> paramMap);			// 본사 -> 전자결재 관리 -> 문서 품목 조회
	int ElectronicPaymentManageTmSignSave(Map<String, Object> paramMap);							// 본사 -> 전자결재 관리 -> 팀장 결재/반려 저장
	int ElectronicPaymentManageCeoSignSave(Map<String, Object> paramMap);							// 본사 -> 전자결재 관리 -> 대표 결재/반려 저장
	int ElectronicPaymentManagePayerSignSave(Map<String, Object> paramMap);							// 본사 -> 전자결재 관리 -> 결재자 결재/반려 저장
	int ElectronicPaymentItemBuyYnSave(Map<String, Object> paramMap);								// 본사 -> 전자결재 관리 -> 구매요청품목 저장
	List<Map<String, Object>> ElectronicPaymentNotificationList(Map<String, Object> paramMap);		// 본사 -> 전자결재 알림 목록
	int ElectronicPaymentNotificationReadSave(Map<String, Object> paramMap);							// 본사 -> 전자결재 알림 읽음 처리
	List<Map<String, Object>> HeadOfficeDepartmentList(Map<String, Object> paramMap); 				// 본사 -> 전자결재 관리 -> 부서별 목록 조회
	List<Map<String, Object>> HeadOfficeUserListByDepartment(Map<String, Object> paramMap); 		// 본사 -> 전자결재 관리 -> 부서별 전체 사용자 조회
	List<Map<String, Object>> HeadOfficeScheduleList(Map<String, Object> paramMap);				// 본사 -> 일정관리 -> 영업팀/운영팀/급식사업부 일정 조회
	String SelectMultiUserNames(@org.apache.ibatis.annotations.Param("userIds") String[] userIds);	// 본사 -> 일정관리 -> user_ids 배열로 이름 목록 조회
	List<Map<String, Object>> NoticeList(Map<String, Object> paramMap);							// 본사 -> 공지사항 -> 목록 조회
	Map<String, Object> NoticeDetail(Map<String, Object> paramMap);								// 본사 -> 공지사항 -> 상세 조회
	int NoticeSave(Map<String, Object> paramMap);												// 본사 -> 공지사항 -> 등록/수정 (upsert)
	int NoticeDelete(Map<String, Object> paramMap);												// 본사 -> 공지사항 -> 삭제
	List<Map<String, Object>> NoticeFileList(Map<String, Object> paramMap);						// 본사 -> 공지사항 -> 첨부파일 목록 조회
	void NoticeFileSave(Map<String, Object> paramMap);											// 본사 -> 공지사항 -> 첨부파일 저장
	int NoticeFileDelete(Map<String, Object> paramMap);											// 본사 -> 공지사항 -> 첨부파일 삭제
	Integer GetMaxNoticeFileOrder(Map<String, Object> paramMap);								// 본사 -> 공지사항 -> 첨부파일 image_order 최대값 조회

	List<Map<String, Object>> EducationList(Map<String, Object> paramMap);					// 인사 -> 교육 -> 목록 조회
	Map<String, Object> EducationDetail(Map<String, Object> paramMap);						// 인사 -> 교육 -> 상세 조회
	int EducationSave(Map<String, Object> paramMap);										// 인사 -> 교육 -> 등록/수정 (upsert)
	int EducationDelete(Map<String, Object> paramMap);										// 인사 -> 교육 -> 삭제
	List<Map<String, Object>> EducationFileList(Map<String, Object> paramMap);				// 인사 -> 교육 -> 첨부파일 목록 조회
	void EducationFileSave(Map<String, Object> paramMap);									// 인사 -> 교육 -> 첨부파일 저장
	int EducationFileDelete(Map<String, Object> paramMap);									// 인사 -> 교육 -> 첨부파일 삭제
	Integer GetMaxEducationFileOrder(Map<String, Object> paramMap);							// 인사 -> 교육 -> 첨부파일 image_order 최대값 조회

	// 인사 평가 (EvaluationFormInit 단건 호출로 타입·사용자 동시 반환)
	List<Map<String, Object>> EvaluationFormTypes();
	List<Map<String, Object>> EvaluationTypeList();
	int EvaluationTypeInsert(Map<String, Object> paramMap);
	int EvaluationTypeUpdate(Map<String, Object> paramMap);
	int EvaluationTypeDelete(Map<String, Object> paramMap);									// 인사 -> 평가 설정 삭제 (tb_hr_evaluation_type)
	List<Map<String, Object>> EvaluationFormUsers();										// 인사 -> 평가 전체 사용자 목록 (dept_name 포함)
	List<Map<String, Object>> EvaluationList(Map<String, Object> paramMap);					// 인사 -> 평가 -> 목록 조회 (세션 집계, 권한별 조건)
	List<Map<String, Object>> EvaluationDetail(Map<String, Object> paramMap);				// 인사 -> 평가 -> 상세 조회 (단일 세션 KPI 전체 행별 반환)
	int EvaluationUpdatePeriod(Map<String, Object> paramMap);								// 인사 -> 평가 -> 수정 시 평가기간(start_time, end_time) 세션 일괄 UPDATE
	int EvaluationSave(Map<String, Object> paramMap);										// 인사 -> 평가 -> 헤더 저장 (세션 1건)
	int EvaluationKpiSave(Map<String, Object> paramMap);									// 인사 -> 평가 -> KPI 행 저장 (tb_hr_evaluation_kpi)
	int EvaluationKpiDeleteByEvalIdx(Map<String, Object> paramMap);							// 인사 -> 평가 -> KPI 행 삭제 (evaluation idx 기준)
	int EvaluationCountByDocTypeAndDate(String docType);									// 인사 -> 평가 -> doc_type + 오늘 날짜 기준 문서 수 조회
	int EvaluationDelete(Map<String, Object> paramMap);										// 인사 -> 평가 -> 헤더 소프트삭제
	List<Map<String, Object>> EvaluationFileList(Map<String, Object> paramMap);				// 인사 -> 평가 -> 첨부파일 목록 조회
	void EvaluationFileSave(Map<String, Object> paramMap);									// 인사 -> 평가 -> 첨부파일 저장
	int EvaluationFileDelete(Map<String, Object> paramMap);									// 인사 -> 평가 -> 첨부파일 삭제
	Integer GetMaxEvaluationFileOrder(Map<String, Object> paramMap);						// 인사 -> 평가 -> 첨부파일 image_order 최대값 조회
	int EvaluationPerformanceUpdate(Map<String, Object> paramMap);							// 인사 -> 평가 -> 실적 업데이트 (단일 KPI 행 idx 기준, performance 일괄 적용)
	int EvaluationTeamLeaderConfirm(Map<String, Object> paramMap);							// 인사 -> 평가 -> 팀장확인 처리 (단일 세션 전체 업데이트, tm_sign='4')
	int EvaluationHpLeaderConfirm(Map<String, Object> paramMap);							// 인사 -> 평가 -> 실장 확인 처리 (dept 4·5 문서, hp_sign='4')
	int EvaluationHrLeaderConfirm(Map<String, Object> paramMap);							// 인사 -> 평가 -> 인사팀장 확인 처리 (hr_sign='4')
	int EvaluationCeoLeaderConfirm(Map<String, Object> paramMap);							// 인사 -> 평가 -> 대표확인 처리 (단일 세션 전체 업데이트, ceo_sign='4')
	List<Map<String, Object>> EvaluationNotificationList(Map<String, Object> paramMap);		// 인사 -> 평가 -> 알림 목록 (팀장결재요청 / 인사팀장결재요청 / 확인완료)
	int EvaluationNotificationReadSave(Map<String, Object> paramMap);						// 인사 -> 평가 -> 알림 읽음 처리 (reg_read_dt / tm_read_dt / payer_read_dt 업데이트)
	int MigrateEvaluationFiles(Map<String, Object> paramMap);								// 인사 -> 평가 -> 수정 시 첨부파일 notice_idx 재연결 (old_idx → new_idx)
}