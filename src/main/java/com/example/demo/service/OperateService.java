package com.example.demo.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.mapper.HeadOfficeMapper;
import com.example.demo.mapper.OperateMapper;

@Service
public class OperateService {

	OperateMapper operateMapper;
	HeadOfficeMapper headOfficeMapper;
	
	public OperateService(OperateMapper operateMapper, HeadOfficeMapper headOfficeMapper) {
		this.operateMapper = operateMapper;
		this.headOfficeMapper = headOfficeMapper;
	}
	public String NowDateKey() {
		String accountKey = operateMapper.NowDateKey();
		return accountKey;
	}
	// 급식사업부 -> 운영관리 -> 집계표 조회
	public List<Map<String, Object>> TallySheetList(Map<String, Object> paramMap) {
		List<Map<String, Object>> resultList = new ArrayList<>();
		resultList = operateMapper.TallySheetList(paramMap);
		return resultList;
	}
	// 급식사업부 -> 운영관리 -> 집계표(본월) 저장
	public int TallyNowMonthSave (Map<String, Object> paramMap) {
		int iResult = 0;
		iResult = operateMapper.TallyNowMonthSave(paramMap);
		return iResult;
	}	
	// 급식사업부 -> 운영관리 -> 집계표(이월) 저장
	public int TallyBeforeMonthSave (Map<String, Object> paramMap) {
		int iResult = 0;
		iResult = operateMapper.TallyBeforeMonthSave(paramMap);
		return iResult;
	}
	// 급식사업부 -> 운영관리 -> 기물리스트 저장
	public int PropertiesSave(Map<String, Object> paramMap) {
		int iResult = 0;
		iResult = operateMapper.PropertiesSave(paramMap);
		return iResult;
	}
	// 급식사업부 -> 운영관리 -> 기물리스트 조회
	public List<Map<String, Object>> PropertiesList(Map<String, Object> paramMap) {
		List<Map<String, Object>> resultList = new ArrayList<>();
		resultList = operateMapper.PropertiesList(paramMap);
		return resultList;
	}
	// 급식사업부 -> 운영관리 -> 위생관리 조회
	public List<Map<String, Object>> HygieneList(Map<String, Object> paramMap) {
		List<Map<String, Object>> resultList = new ArrayList<>();
		resultList = operateMapper.HygieneList(paramMap);
		return resultList;
	}
	// 급식사업부 -> 운영관리 -> 위생관리 저장
	public int HygieneSave(Map<String, Object> paramMap) {
		int iResult = 0;
		iResult = operateMapper.HygieneSave(paramMap);
		return iResult;
	}
	// 급식사업부 -> 운영관리 -> 인수인계서 조회
	public Map<String, Object> HandOverSearch(Map<String, Object> paramMap) {
		Map<String, Object> resultMap = new HashMap<String, Object>();
		resultMap = operateMapper.HandOverSearch(paramMap);
		return resultMap;
	}
	// 급식사업부 -> 운영관리 -> 인수인계서 저장
	public int HandOverSave(Map<String, Object> paramMap) {
		int iResult = 0;
		iResult = operateMapper.HandOverSave(paramMap);
		return iResult;
	}
	// 급식사업부 -> 운영관리 -> 집계표 Modal 거래처 매핑 조회 V2
	public List<Map<String, Object>> AccountMappingList(Map<String, Object> paramMap) {
		List<Map<String, Object>> resultList = new ArrayList<>();
		resultList = operateMapper.AccountMappingList(paramMap);
		return resultList;
	}
	// 급식사업부 -> 운영관리 -> 집계표 Modal 거래처 매핑 조회 V2
	public List<Map<String, Object>> AccountMappingV2List(Map<String, Object> paramMap) {
		List<Map<String, Object>> resultList = new ArrayList<>();
		resultList = operateMapper.AccountMappingV2List(paramMap);
		return resultList;
	}
	// 급식사업부 -> 운영관리 -> 집계표 Modal 거래처 매핑 저장
	public int AccountMappingSave(Map<String, Object> paramMap) {
		int iResult = 0;
		iResult = operateMapper.AccountMappingSave(paramMap);
		return iResult;
	}
	// 급식사업부 -> 운영관리 -> 집계표 Modal 거래처 저장
	public int AccountRetailBusinessSave(Map<String, Object> paramMap) {
		int iResult = 0;
		iResult = operateMapper.AccountRetailBusinessSave(paramMap);
		return iResult;
	}
	// 급식사업부 -> 운영관리 -> 고객사관리 -> 거래처관리 조회
	public List<Map<String, Object>> AccountRetailBusinessList(Map<String, Object> paramMap) {
		List<Map<String, Object>> resultList = new ArrayList<>();
		resultList = operateMapper.AccountRetailBusinessList(paramMap);
		return resultList;
	}
	@Transactional(rollbackFor = Exception.class)  // ✅ 전체 작업 트랜잭션
    public int processProfitLoss(Map<String, Object> param) {
		
		param.put("month", param.get("count_month"));
		param.put("year", param.get("count_year"));
		
        int result = 0;
        
        // ③ 손익표 합계 + 비율 저장 프로시저 호출
        param.put("result", 0); // OUT 값 초기화
        headOfficeMapper.ProfitLossTotalSave(param);

        // OUT 값 확인
        result = (int) param.get("result");
        if (result != 1) {
            throw new RuntimeException("❌ ProfitLossTotalSave 프로시저 실패");
        }
        
        // 예산 저장 프로시저 호출
        param.put("result", 0); // OUT 값 초기화
        operateMapper.BudgetTotalSave(param);

        // OUT 값 확인
        result = (int) param.get("result");
        if (result != 1) {
            throw new RuntimeException("❌ BudgetTotalSave 프로시저 실패");
        }

        return 1; // ✅ 전체 성공
    }
	// 급식사업부 -> 운영관리 -> 고객사관리 -> 면허증 및 자격증관리 조회
	public List<Map<String, Object>> AccountMembersFilesList(Map<String, Object> paramMap) {
		List<Map<String, Object>> resultList = new ArrayList<>();
		resultList = operateMapper.AccountMembersFilesList(paramMap);
		return resultList;
	}
	// 급식사업부 -> 운영관리 -> 고객사관리 -> 면허증 및 자격증관리 타입별 조회
	public List<Map<String, Object>> AccountTypeForFileList(Map<String, Object> paramMap) {
		List<Map<String, Object>> resultList = new ArrayList<>();
		resultList = operateMapper.AccountTypeForFileList(paramMap);
		return resultList;
	}
	// 급식사업부 -> 운영관리 -> 고객사관리 -> 면허증 및 자격증관리 저장
	public int AccountMembersFilesSave(Map<String, Object> paramMap) {
		int iResult = 0;
		iResult = operateMapper.AccountMembersFilesSave(paramMap);
		return iResult;
	}
	// 급식사업부 -> 운영관리 -> 고객사관리 -> 대체업체 조회
	public List<Map<String, Object>> AccountSubRestaurantList(Map<String, Object> paramMap) {
		List<Map<String, Object>> resultList = new ArrayList<>();
		resultList = operateMapper.AccountSubRestaurantList(paramMap);
		return resultList;
	}
	// 급식사업부 -> 운영관리 -> 고객사관리 -> 대체업체 저장
	public int AccountSubRestaurantSave(Map<String, Object> paramMap) {
		int iResult = 0;
		iResult = operateMapper.AccountSubRestaurantSave(paramMap);
		return iResult;
	}
	// 운영/인사 근무형태 조회 
	public List<Map<String, Object>> AccountMemberWorkSystemList() {
		List<Map<String, Object>> resultList = new ArrayList<>();
		resultList = operateMapper.AccountMemberWorkSystemList();
		return resultList;
	}
	// 운영/인사 근무형태 저장
	public int AccountMemberWorkSystemSave(Map<String, Object> paramMap) {
		int iResult = 0;
		iResult = operateMapper.AccountMemberWorkSystemSave(paramMap);
		return iResult;
	}
	// 급식사업부 -> 운영관리 -> 거래처관리 -> 인사기록카드 조회
	public List<Map<String, Object>> AccountMemberSheetList(Map<String, Object> paramMap) {
		List<Map<String, Object>> resultList = new ArrayList<>();
		resultList = operateMapper.AccountMemberSheetList(paramMap);
		return resultList;
	}
	// 급식사업부 -> 운영->현장관리, 인사->현장관리 -> 조회
	public List<Map<String, Object>> AccountMemberAllList(Map<String, Object> paramMap) {
		List<Map<String, Object>> resultList = new ArrayList<>();
		resultList = operateMapper.AccountMemberAllList(paramMap);
		return resultList;
	}
	// 급식사업부 -> 운영->현장관리, 인사->현장관리 -> 직원관리 저장
	public int AccountMembersSave(Map<String, Object> paramMap) {
		int iResult = 0;
		iResult = operateMapper.AccountMembersSave(paramMap);
		return iResult;
	}
	// 급식사업부 -> 운영->현장관리 채용확정 시, 출근부 적용
	public int AccountRecordSetRecRecordDataSave(Map<String, Object> paramMap) {
		int iResult = 0;
		iResult = operateMapper.AccountRecordSetRecRecordDataSave(paramMap);
		return iResult;
	}
	// 급식사업부 -> 운영->현장관리 채용확정 또는 채용취소 시, 채용현황 출근부 삭제 적용
	public int AccountRecRecordDataDelete(Map<String, Object> paramMap) {
		int iResult = 0;
		iResult = operateMapper.AccountRecRecordDataDelete(paramMap);
		return iResult;
	}
	// 급식사업부 -> 운영 -> 채용관리 -> 현장 채용현황 조회
	public List<Map<String, Object>> AccountRecMemberList(Map<String, Object> paramMap) {
		List<Map<String, Object>> resultList = new ArrayList<>();
		resultList = operateMapper.AccountRecMemberList(paramMap);
		return resultList;
	}
	// 급식사업부 -> 운영 -> 채용관리 -> 현장 채용현황 저장
	public int AccountRecMembersSave(Map<String, Object> paramMap) {
		int iResult = 0;
		iResult = operateMapper.AccountRecMembersSave(paramMap);
		return iResult;
	}
	// 급식사업부 -> 운영->현장관리, 인사->현장관리 -> 파출관리 조회
	public List<Map<String, Object>> AccountDispatchMemberAllList(Map<String, Object> paramMap) {
		List<Map<String, Object>> resultList = new ArrayList<>();
		resultList = operateMapper.AccountDispatchMemberAllList(paramMap);
		return resultList;
	}
	// 급식사업부 -> 운영->현장관리, 인사->현장관리 -> 파출관리 저장
	public int AccountDispatchMembersSave(Map<String, Object> paramMap) {
		int iResult = 0;
		iResult = operateMapper.AccountDispatchMembersSave(paramMap);
		return iResult;
	}
	
	// 급식사업부 -> 운영관리 -> 거래처관리 -> 식수현황 조회
	public List<Map<String, Object>> AccountDinnersNumberList(Map<String, Object> paramMap) {
		List<Map<String, Object>> resultList = new ArrayList<>();
		resultList = operateMapper.AccountDinnersNumberList(paramMap);
		return resultList;
	}
	// 급식사업부 -> 운영관리 -> 거래처관리 -> 식수현황 저장
	public int AccountDinnersNumberSave(Map<String, Object> paramMap) {
		int iResult = 0;
		iResult = operateMapper.AccountDinnersNumberSave(paramMap);
		return iResult;
	}
	// 급식사업부 -> 운영관리 -> 예산관리 조회
	public List<Map<String, Object>> BudgetManageMentList(Map<String, Object> paramMap) {
		List<Map<String, Object>> resultList = new ArrayList<>();
		resultList = operateMapper.BudgetManageMentList(paramMap);
		return resultList;
	}
	// 급식사업부 -> 운영관리 -> 예산관리(예산기준) 조회
	public List<Map<String, Object>> BudgetStandardList(Map<String, Object> paramMap) {
		List<Map<String, Object>> resultList = new ArrayList<>();
		resultList = operateMapper.BudgetStandardList(paramMap);
		return resultList;
	}
	// 급식사업부 -> 운영관리 -> 예산관리(배식횟수) 조회
	public List<Map<String, Object>> MealsNumberList(Map<String, Object> paramMap) {
		List<Map<String, Object>> resultList = new ArrayList<>();
		resultList = operateMapper.MealsNumberList(paramMap);
		return resultList;
	}
	// 급식사업부 -> 운영관리 -> 예산관리 저장
	public int BudgetTableSave(Map<String, Object> paramMap) {
		return operateMapper.BudgetTableSave(paramMap);
	}
	@Transactional(rollbackFor = Exception.class)  // ✅ 전체 작업 트랜잭션
    public int BudgetTotalSave(Map<String, Object> param) {

        int result = 0;
        
        // 예산 저장 프로시저 호출
        param.put("result", 0); // OUT 값 초기화
        operateMapper.BudgetTotalSave(param);

        // OUT 값 확인
        result = (int) param.get("result");
        if (result != 1) {
            throw new RuntimeException("❌ BudgetTotalSave 프로시저 실패");
        }
        
        return 1; // ✅ 전체 성공
    }
	// 현장관리 -> 근태관리 -> 연차 정보 조회
	public List<Map<String, Object>> AnnualLeaveList(Map<String, Object> paramMap) {
		List<Map<String, Object>> resultList = new ArrayList<>();
		resultList = operateMapper.AnnualLeaveList(paramMap);
		return resultList;
	}
	// 현장관리 -> 근태관리 -> 초과근무 조회
	public List<Map<String, Object>> OverTimeList(Map<String, Object> paramMap) {
		List<Map<String, Object>> resultList = new ArrayList<>();
		resultList = operateMapper.OverTimeList(paramMap);
		return resultList;
	}
	// 운영 -> 일정관리 -> 운영팀 조회 
	public List<Map<String, Object>> OperateMemberList() {
		List<Map<String, Object>> resultList = new ArrayList<>();
		resultList = operateMapper.OperateMemberList();
		return resultList;
	}
	// 운영 -> 일정관리 -> 캘린더 조회
	public List<Map<String, Object>> OperateScheduleList(Map<String, Object> paramMap) {
		List<Map<String, Object>> resultList = new ArrayList<>();
		resultList = operateMapper.OperateScheduleList(paramMap);
		return resultList;
	}
	// 메인화면 -> 운영팀 당일 일정 조회
	public List<Map<String, Object>> OperateScheduleTodayList(Map<String, Object> paramMap) {
		List<Map<String, Object>> resultList = new ArrayList<>();
		resultList = operateMapper.OperateScheduleTodayList(paramMap);
		return resultList;
	}
	// 운영 -> 일정관리 -> 캘린더 저장
	public int OperateScheduleSave (Map<String, Object> paramMap) {
		int iResult = 0;
		iResult = operateMapper.OperateScheduleSave(paramMap);
		return iResult;
	}
	// 운영 -> 긴급인력 -> 업장별 요일 인력 기준 조회
	public List<Map<String, Object>> AccountRecordStandardList(Map<String, Object> paramMap) {
		List<Map<String, Object>> resultList = new ArrayList<>();
		resultList = operateMapper.AccountRecordStandardList(paramMap);
		return resultList;
	}
	// 운영 -> 긴급인력 -> 업장별 요일 인력 기준 저장
	public int AccountRecordStandardSave (Map<String, Object> paramMap) {
		int iResult = 0;
		iResult = operateMapper.AccountRecordStandardSave(paramMap);
		return iResult;
	}
	// 집계표 -> 셀 포인트 조회
	public List<Map<String, Object>> TallySheetPointList(Map<String, Object> paramMap) {
		List<Map<String, Object>> resultList = new ArrayList<>();
		resultList = operateMapper.TallySheetPointList(paramMap);
		return resultList;
	}
	// 집계표 -> 셀 포인트 저장
	public int TallySheetPointSave (Map<String, Object> paramMap) {
		int iResult = 0;
		iResult = operateMapper.TallySheetPointSave(paramMap);
		return iResult;
	}
	// 집계표 -> type 입력가능여부 조회
	public List<Map<String, Object>> TallySheetUseList(Map<String, Object> paramMap) {
		List<Map<String, Object>> resultList = new ArrayList<>();
		resultList = operateMapper.TallySheetUseList(paramMap);
		return resultList;
	}
	// 집계표 -> type 입력가능여부 저장
	public int TallySheetUseSave (Map<String, Object> paramMap) {
		int iResult = 0;
		iResult = operateMapper.TallySheetUseSave(paramMap);
		return iResult;
	}
}
