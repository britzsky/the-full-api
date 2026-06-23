package com.example.demo.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.mapper.HeadOfficeMapper;
import com.example.demo.mapper.OperateMapper;

@Service
public class HeadOfficeService {
	
	HeadOfficeMapper headOfficeMapper;
	OperateMapper operateMapper;

	public HeadOfficeService(HeadOfficeMapper userMapper, OperateMapper operateMapper) {
        this.headOfficeMapper = userMapper;
        this.operateMapper = operateMapper;
    }
	// 본사 -> 주간식단 저장
	public int WeekMenuSave(Map<String, Object> paramMap) {
		return headOfficeMapper.WeekMenuSave(paramMap);
	};
	// 본사 -> 주간식단 목록 조회
	public List<Map<String, Object>> WeekMenuList(Map<String, Object> paramMap) {
		return headOfficeMapper.WeekMenuList(paramMap);
	}
	// 본사 -> 주간식단 오늘 조회
	public List<Map<String, Object>> WeekMenuTodayList(Map<String, Object> paramMap) {
		return headOfficeMapper.WeekMenuTodayList(paramMap);
	}
	// 본사 -> 이벤트 저장
	public int EventSave(Map<String, Object> paramMap) {
		return headOfficeMapper.EventSave(paramMap);
	};
	// 본사 -> 이벤트 목록 조회
	public List<Map<String, Object>> EventList(Map<String, Object> paramMap) {
		return headOfficeMapper.EventList(paramMap);
	}
	// 본사 -> 인원카운팅 목록 조회
	public List<Map<String, Object>> PeopleCountingList(Map<String, Object> paramMap) {
		return headOfficeMapper.PeopleCountingList(paramMap);
	}
	// 본사 -> 손익표 저장
	public int ProfitLossTableSave(Map<String, Object> paramMap) {
		return headOfficeMapper.ProfitLossTableSave(paramMap);
	}
	// 본사 -> 인건비 엑셀 일괄 저장
	public int PersonCostExcelSave(Map<String, Object> paramMap) {
		return headOfficeMapper.PersonCostExcelSave(paramMap);
	}
	// 본사 -> 손익표 인건비 조회
	public Map<String, Object> getProfitLossPersonCost(Map<String, Object> paramMap) {
		return headOfficeMapper.getProfitLossPersonCost(paramMap);
	}
	// 본사 -> 인건비 변경 히스토리 저장
	public int savePersonCostHistory(Map<String, Object> paramMap) {
		return headOfficeMapper.savePersonCostHistory(paramMap);
	}
	// 본사 -> 손익표 목록 조회
	public List<Map<String, Object>> ProfitLossTableList(Map<String, Object> paramMap) {
		return headOfficeMapper.ProfitLossTableList(paramMap);
	}
	// 본사 -> 손익표 엑셀 다운
	public List<Map<String, Object>> ExcelDownProfitLossTableList(Map<String, Object> paramMap) {
		return headOfficeMapper.ExcelDownProfitLossTableList(paramMap);
	}
	// 본사 -> 손익표 월별 엑셀 다운
	public List<Map<String, Object>> ExcelDownMonthProfitLossTableList(Map<String, Object> paramMap) {
		return headOfficeMapper.ExcelDownMonthProfitLossTableList(paramMap);
	}
	// 본사 -> 손익표 합계 저장
	public void ProfitLossTotalSave(Map<String, Object> paramMap) {
		headOfficeMapper.ProfitLossTotalSave(paramMap);
	}	
	// 본사 -> 회계관리 목록 조회
	public List<Map<String, Object>> AccountManagermentTableList (Map<String, Object> paramMap) {
		List<Map<String, Object>> resultList = new ArrayList<>();
		resultList = headOfficeMapper.AccountManagermentTableList(paramMap);
		return resultList;
	}
	@Transactional(rollbackFor = Exception.class)  // 전체 업무 트랜잭션 관리
    public int processProfitLoss(Map<String, Object> param) {

        int result = 0;

        // 손익표 합계 + 예산 동시 저장 호출
        param.put("result", 0); // OUT 값 초기화
        headOfficeMapper.ProfitLossTotalSave(param);

        // OUT 값 확인
        result = (int) param.get("result");
        if (result != 1) {
            throw new RuntimeException("ProfitLossTotalSave 저장 실패");
        }
        
        // 예산 합계 저장 호출
        param.put("result", 0); // OUT 값 초기화
        operateMapper.BudgetTotalSave(param);

        // OUT 값 확인
        result = (int) param.get("result");
        if (result != 1) {
            throw new RuntimeException("BudgetTotalSave 저장 실패");
        }
        
        return 1; // 전체 성공
    }
	// 본사 -> 회계관리 -> 구매맵핑
	public List<Map<String, Object>> AccountMappingPurchaseList (Map<String, Object> paramMap) {
		List<Map<String, Object>> resultList = new ArrayList<>();
		resultList = headOfficeMapper.AccountMappingPurchaseList(paramMap);
		return resultList;
	}
	// 본사 -> 회계관리 -> 구매맵핑(날짜별 상세)
	public List<Map<String, Object>> AccountMappingPurchaseDetailList (Map<String, Object> paramMap) {
		List<Map<String, Object>> resultList = new ArrayList<>();
		resultList = headOfficeMapper.AccountMappingPurchaseDetailList(paramMap);
		return resultList;
	}
	
	// 본사 -> 전자결재 관리 -> 전자결재 문서타입 목록 조회
	public List<Map<String, Object>> HeadOfficeElectronicPaymentTypeList (Map<String, Object> paramMap) {
		List<Map<String, Object>> resultList = new ArrayList<>();
		resultList = headOfficeMapper.HeadOfficeElectronicPaymentTypeList(paramMap);
		return resultList;
	}
	
	// 본사 -> 전자결재 관리 -> 결재 문서 목록 조회
	public List<Map<String, Object>> HeadOfficeElectronicPaymentList (Map<String, Object> paramMap) {
		List<Map<String, Object>> resultList = new ArrayList<>();
		resultList = headOfficeMapper.HeadOfficeElectronicPaymentList(paramMap);
		return resultList;
	}
	
	// 본사 -> 전자결재 관리 -> 구매요청 품의서 메인 저장(전자결재 main table)
	public int HeadOfficeElectronicPaymentSave(Map<String, Object> paramMap) {
		return headOfficeMapper.HeadOfficeElectronicPaymentSave(paramMap);
	};
	
	// 본사 -> 전자결재 관리 -> 구매요청 품의서 품목 저장(구매요청 item table)
	public int HeadOfficePurchaseRequestSave(Map<String, Object> paramMap) {
		return headOfficeMapper.HeadOfficePurchaseRequestSave(paramMap);
	};

	// 본사 -> 전자결재 관리 -> 결재 문서 메인 저장
	public int ElectronicPaymentSave(Map<String, Object> paramMap) {
		return headOfficeMapper.ElectronicPaymentSave(paramMap);
	}

	// 본사 -> 전자결재 관리 -> 문서번호 기준 구매요청 품목 삭제
	public int HeadOfficePurchaseRequestDeleteByPaymentId(Map<String, Object> paramMap) {
		return headOfficeMapper.HeadOfficePurchaseRequestDeleteByPaymentId(paramMap);
	}

	// 본사 -> 전자결재 관리 -> 품목 일괄 저장(반복)
	public int HeadOfficePurchaseRequestBulkSave(Map<String, Object> paramMap) {
		return headOfficeMapper.HeadOfficePurchaseRequestBulkSave(paramMap);
	}

	// 본사 -> 전자결재 관리 -> 문서번호 기준 기안서 본문 삭제
	public int HeadOfficeDraftDeleteByPaymentId(Map<String, Object> paramMap) {
		return headOfficeMapper.HeadOfficeDraftDeleteByPaymentId(paramMap);
	}

	// 본사 -> 전자결재 관리 -> 기안서 본문 저장
	public int HeadOfficeDraftSave(Map<String, Object> paramMap) {
		return headOfficeMapper.HeadOfficeDraftSave(paramMap);
	}

	// 본사 -> 전자결재 관리 -> 문서번호 기준 지출결의서 본문 삭제
	public int HeadOfficePaymentDocDeleteByPaymentId(Map<String, Object> paramMap) {
		return headOfficeMapper.HeadOfficePaymentDocDeleteByPaymentId(paramMap);
	}

	// 본사 -> 전자결재 관리 -> 지출결의서 본문 저장
	public int HeadOfficePaymentDocSave(Map<String, Object> paramMap) {
		return headOfficeMapper.HeadOfficePaymentDocSave(paramMap);
	}

	// 본사 -> 전자결재 관리 -> 첨부파일 목록 조회
	public List<Map<String, Object>> ElectronicPaymentFileList(Map<String, Object> paramMap) {
		List<Map<String, Object>> resultList = new ArrayList<>();
		resultList = headOfficeMapper.ElectronicPaymentFileList(paramMap);
		return resultList;
	}

	// 본사 -> 전자결재 관리 -> 첨부파일 삭제
	public int ElectronicPaymentFileDelete(Map<String, Object> paramMap) {
		return headOfficeMapper.ElectronicPaymentFileDelete(paramMap);
	}

	// 본사 -> 전자결재 관리 -> 첨부파일 최대 image_order + 1 반환
	public int GetNextElectronicPaymentImageOrder(String paymentId) {
		Map<String, Object> paramMap = new java.util.HashMap<>();
		paramMap.put("payment_id", paymentId);
		Integer maxOrder = headOfficeMapper.GetMaxElectronicPaymentImageOrder(paramMap);
		return maxOrder == null ? 1 : maxOrder + 1;
	}

	// 본사 -> 전자결재 관리 -> 첨부파일 저장
	public void SaveElectronicPaymentFile(Map<String, Object> paramMap) {
		headOfficeMapper.SaveElectronicPaymentFile(paramMap);
	}

	// 본사 -> 전자결재 관리 -> 내문서/결재대기 목록 조회
	public List<Map<String, Object>> ElectronicPaymentManageList(Map<String, Object> paramMap) {
		List<Map<String, Object>> resultList = new ArrayList<>();
		resultList = headOfficeMapper.ElectronicPaymentManageList(paramMap);
		return resultList;
	}

	// 본사 -> 전자결재 관리 -> 문서 메인 조회
	public Map<String, Object> ElectronicPaymentManageMain(Map<String, Object> paramMap) {
		return headOfficeMapper.ElectronicPaymentManageMain(paramMap);
	}

	// 본사 -> 전자결재 관리 -> 문서 품목 조회
	public List<Map<String, Object>> ElectronicPaymentManageItems(Map<String, Object> paramMap) {
		List<Map<String, Object>> resultList = new ArrayList<>();
		resultList = headOfficeMapper.ElectronicPaymentManageItems(paramMap);
		return resultList;
	}

	// 본사 -> 전자결재 관리 -> 결재/반려 저장 (팀장→결재자→대표 순으로 시도)
	public int ElectronicPaymentManageSignSave(Map<String, Object> paramMap) {
		int iResult = headOfficeMapper.ElectronicPaymentManageTmSignSave(paramMap);
		if (iResult > 0) return iResult;

		iResult = headOfficeMapper.ElectronicPaymentManageCeoSignSave(paramMap);
		if (iResult > 0) return iResult;

		iResult = headOfficeMapper.ElectronicPaymentManagePayerSignSave(paramMap);
		return iResult;
	}

	// 본사 -> 전자결재 관리 -> 구매요청품목 저장
	public int ElectronicPaymentItemBuyYnSave(Map<String, Object> paramMap) {
		return headOfficeMapper.ElectronicPaymentItemBuyYnSave(paramMap);
	}

	// 본사 -> 전자결재 알림 목록 조회
	public List<Map<String, Object>> ElectronicPaymentNotificationList(Map<String, Object> paramMap) {
		List<Map<String, Object>> resultList = new ArrayList<>();
		resultList = headOfficeMapper.ElectronicPaymentNotificationList(paramMap);
		return resultList;
	}

	// 본사 -> 전자결재 알림 읽음 처리
	public int ElectronicPaymentNotificationReadSave(Map<String, Object> paramMap) {
		return headOfficeMapper.ElectronicPaymentNotificationReadSave(paramMap);
	}
	
	// 본사 -> 전자결재 관리 -> 부서별 목록 조회
	public List<Map<String, Object>> HeadOfficeDepartmentList (Map<String, Object> paramMap) {
		List<Map<String, Object>> resultList = new ArrayList<>();
		resultList = headOfficeMapper.HeadOfficeDepartmentList(paramMap);
		return resultList;
	}
	
	// 본사 -> 전자결재 관리 -> 부서별 전체 사용자 조회
	public List<Map<String, Object>> HeadOfficeUserListByDepartment (Map<String, Object> paramMap) {
		List<Map<String, Object>> resultList = new ArrayList<>();
		resultList = headOfficeMapper.HeadOfficeUserListByDepartment(paramMap);
		return resultList;
	}

	// 본사 -> 일정관리 -> 영업팀/운영팀/급식사업부 일정 조회
	public List<Map<String, Object>> HeadOfficeScheduleList(Map<String, Object> paramMap) {
		return headOfficeMapper.HeadOfficeScheduleList(paramMap);
	}

	// 본사 -> 일정관리 -> user_ids 배열로 이름 목록 조회
	public String SelectMultiUserNames(String[] userIds) {
		return headOfficeMapper.SelectMultiUserNames(userIds);
	}

	// 본사 -> 공지사항 -> 목록 조회
	public List<Map<String, Object>> NoticeList(Map<String, Object> paramMap) {
		return headOfficeMapper.NoticeList(paramMap);
	}

	// 본사 -> 공지사항 -> 상세 조회
	public Map<String, Object> NoticeDetail(Map<String, Object> paramMap) {
		return headOfficeMapper.NoticeDetail(paramMap);
	}

	// 본사 -> 공지사항 -> 등록/수정 (upsert)
	public int NoticeSave(Map<String, Object> paramMap) {
		return headOfficeMapper.NoticeSave(paramMap);
	}

	// 본사 -> 공지사항 -> 삭제
	public int NoticeDelete(Map<String, Object> paramMap) {
		return headOfficeMapper.NoticeDelete(paramMap);
	}

	// 본사 -> 공지사항 -> 첨부파일 목록 조회
	public List<Map<String, Object>> NoticeFileList(Map<String, Object> paramMap) {
		return headOfficeMapper.NoticeFileList(paramMap);
	}

	// 본사 -> 공지사항 -> 첨부파일 저장
	public void NoticeFileSave(Map<String, Object> paramMap) {
		headOfficeMapper.NoticeFileSave(paramMap);
	}

	// 본사 -> 공지사항 -> 첨부파일 삭제
	public int NoticeFileDelete(Map<String, Object> paramMap) {
		return headOfficeMapper.NoticeFileDelete(paramMap);
	}

	// 본사 -> 공지사항 -> 첨부파일 image_order 최대값 + 1 반환
	public int GetNextNoticeFileOrder(int noticeIdx) {
		Map<String, Object> paramMap = new java.util.HashMap<>();
		paramMap.put("notice_idx", noticeIdx);
		Integer maxOrder = headOfficeMapper.GetMaxNoticeFileOrder(paramMap);
		return maxOrder == null ? 1 : maxOrder + 1;
	}

	// 인사 -> 교육 -> 목록 조회
	public List<Map<String, Object>> EducationList(Map<String, Object> paramMap) {
		return headOfficeMapper.EducationList(paramMap);
	}

	// 인사 -> 교육 -> 상세 조회
	public Map<String, Object> EducationDetail(Map<String, Object> paramMap) {
		return headOfficeMapper.EducationDetail(paramMap);
	}

	// 인사 -> 교육 -> 등록/수정 (upsert)
	public int EducationSave(Map<String, Object> paramMap) {
		return headOfficeMapper.EducationSave(paramMap);
	}

	// 인사 -> 교육 -> 삭제
	public int EducationDelete(Map<String, Object> paramMap) {
		return headOfficeMapper.EducationDelete(paramMap);
	}

	// 인사 -> 교육 -> 첨부파일 목록 조회
	public List<Map<String, Object>> EducationFileList(Map<String, Object> paramMap) {
		return headOfficeMapper.EducationFileList(paramMap);
	}

	// 인사 -> 교육 -> 첨부파일 저장
	public void EducationFileSave(Map<String, Object> paramMap) {
		headOfficeMapper.EducationFileSave(paramMap);
	}

	// 인사 -> 교육 -> 첨부파일 삭제
	public int EducationFileDelete(Map<String, Object> paramMap) {
		return headOfficeMapper.EducationFileDelete(paramMap);
	}

	// 인사 -> 교육 -> 첨부파일 image_order 최대값 + 1 반환
	public int GetNextEducationFileOrder(int educationIdx) {
		Map<String, Object> paramMap = new java.util.HashMap<>();
		paramMap.put("education_idx", educationIdx);
		Integer maxOrder = headOfficeMapper.GetMaxEducationFileOrder(paramMap);
		return maxOrder == null ? 1 : maxOrder + 1;
	}

	// ── 인사 평가 ──────────────────────────────────────────────────────────

	// 인사 -> 평가 폼 초기화용 타입 목록 (EvaluationFormInit 단건 사용)
	public List<Map<String, Object>> EvaluationFormTypes() {
		return headOfficeMapper.EvaluationFormTypes();
	}

	// 인사 -> 평가 폼 초기화용 전체 사용자 목록 (dept_name 포함, EvaluationFormInit 단건 사용)
	public List<Map<String, Object>> EvaluationFormUsers() {
		return headOfficeMapper.EvaluationFormUsers();
	}

	public List<Map<String, Object>> EvaluationTypeList() {
		return headOfficeMapper.EvaluationTypeList();
	}

	public int EvaluationTypeSave(Map<String, Object> paramMap) {
		String docId = String.valueOf(paramMap.getOrDefault("doc_id", "")).trim();
		if (docId.isEmpty()) return headOfficeMapper.EvaluationTypeInsert(paramMap);
		return headOfficeMapper.EvaluationTypeUpdate(paramMap);
	}

	public int EvaluationTypeDelete(Map<String, Object> paramMap) {
		return headOfficeMapper.EvaluationTypeDelete(paramMap);
	}

	// 인사 -> 평가 -> 목록 조회 (세션 기준 집계, 관리자·팀장·사용자 권한별 조건을 paramMap에 주입)
	public List<Map<String, Object>> EvaluationList(Map<String, Object> paramMap) {
		return headOfficeMapper.EvaluationList(paramMap);
	}

	// 인사 -> 평가 -> 상세 조회 (단일 세션의 KPI 전체 항목을 행별로 반환)
	public List<Map<String, Object>> EvaluationDetail(Map<String, Object> paramMap) {
		return headOfficeMapper.EvaluationDetail(paramMap);
	}

	// 인사 -> 평가 -> 수정 시 평가기간(start_time, end_time) 세션 일괄 UPDATE
	public int EvaluationUpdatePeriod(Map<String, Object> paramMap) {
		return headOfficeMapper.EvaluationUpdatePeriod(paramMap);
	}

	// 인사 -> 평가 -> 헤더 저장 (세션 1건)
	public int EvaluationSave(Map<String, Object> paramMap) {
		return headOfficeMapper.EvaluationSave(paramMap);
	}

	// 인사 -> 평가 -> KPI 행 저장 (tb_hr_evaluation_kpi)
	public int EvaluationKpiSave(Map<String, Object> paramMap) {
		return headOfficeMapper.EvaluationKpiSave(paramMap);
	}

	// 인사 -> 평가 -> doc_type + 오늘 날짜 기준 저장된 문서 수 조회 (document_id 시퀀스 계산용)
	public int EvaluationCountByDocTypeAndDate(String docType) {
		return headOfficeMapper.EvaluationCountByDocTypeAndDate(docType);
	}

	// 인사 -> 평가 -> 삭제 (KPI 행 먼저 삭제 후 헤더 소프트삭제)
	public int EvaluationDelete(Map<String, Object> paramMap) {
		headOfficeMapper.EvaluationKpiDeleteByEvalIdx(paramMap);
		return headOfficeMapper.EvaluationDelete(paramMap);
	}

	// 인사 -> 평가 -> 첨부파일 목록 조회
	public List<Map<String, Object>> EvaluationFileList(Map<String, Object> paramMap) {
		return headOfficeMapper.EvaluationFileList(paramMap);
	}

	// 인사 -> 평가 -> 첨부파일 저장
	public void EvaluationFileSave(Map<String, Object> paramMap) {
		headOfficeMapper.EvaluationFileSave(paramMap);
	}

	// 인사 -> 평가 -> 첨부파일 삭제
	public int EvaluationFileDelete(Map<String, Object> paramMap) {
		return headOfficeMapper.EvaluationFileDelete(paramMap);
	}

	// 인사 -> 평가 -> 첨부파일 image_order 최대값 + 1 반환
	public int GetNextEvaluationFileOrder(int evaluationIdx) {
		Map<String, Object> paramMap = new java.util.HashMap<>();
		paramMap.put("evaluation_idx", evaluationIdx);
		Integer maxOrder = headOfficeMapper.GetMaxEvaluationFileOrder(paramMap);
		return maxOrder == null ? 1 : maxOrder + 1;
	}

	// 인사 -> 평가 -> 실적 업데이트 (팀장확인 전에만 작성자가 실적 수정 가능, tm_sign/hr_sign 무관)
	public void EvaluationPerformanceUpdate(Map<String, Object> paramMap) {
		@SuppressWarnings("unchecked")
		List<Map<String, Object>> items = (List<Map<String, Object>>) paramMap.get("items");
		if (items == null) return;
		for (Map<String, Object> item : items) {
			headOfficeMapper.EvaluationPerformanceUpdate(item);
		}
	}

	// 인사 -> 평가 -> 팀장확인 처리 (같은 부서 팀장이 확인 후 단일 세션 전체 업데이트, tm_sign='4')
	public int EvaluationTeamLeaderConfirm(Map<String, Object> paramMap) {
		return headOfficeMapper.EvaluationTeamLeaderConfirm(paramMap);
	}

	// 인사 -> 평가 -> 실장 확인 처리 (department=8 포함 dept 4·5 문서 대상)
	public int EvaluationHpLeaderConfirm(Map<String, Object> paramMap) {
		return headOfficeMapper.EvaluationHpLeaderConfirm(paramMap);
	}

	// 인사 -> 평가 -> 인사팀장 확인 처리 (인사팀장이 확인 후 단일 세션 전체 업데이트, hr_sign='4')
	public int EvaluationHrLeaderConfirm(Map<String, Object> paramMap) {
		return headOfficeMapper.EvaluationHrLeaderConfirm(paramMap);
	}

	// 인사 -> 평가 -> 대표 확인 처리 (ceo_sign='4')
	public int EvaluationCeoLeaderConfirm(Map<String, Object> paramMap) {
		return headOfficeMapper.EvaluationCeoLeaderConfirm(paramMap);
	}

	// 인사 -> 평가 -> 알림 목록 (팀장결재요청 / 인사팀장결재요청 / 확인완료 조건 반환)
	public List<Map<String, Object>> EvaluationNotificationList(Map<String, Object> paramMap) {
		return headOfficeMapper.EvaluationNotificationList(paramMap);
	}

	// 인사 -> 평가 -> 알림 읽음 처리 (reg_read_dt / tm_read_dt / payer_read_dt 업데이트)
	public int EvaluationNotificationReadSave(Map<String, Object> paramMap) {
		return headOfficeMapper.EvaluationNotificationReadSave(paramMap);
	}

	// 인사 -> 평가 -> 수정(upsert) 시 첨부파일 notice_idx를 새 idx로 재연결
	public void MigrateEvaluationFiles(int oldIdx, int newIdx) {
		Map<String, Object> paramMap = new java.util.HashMap<>();
		paramMap.put("old_idx", oldIdx);
		paramMap.put("new_idx", newIdx);
		headOfficeMapper.MigrateEvaluationFiles(paramMap);
	}
}