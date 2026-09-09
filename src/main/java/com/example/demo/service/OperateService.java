package com.example.demo.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import com.example.demo.mapper.HeadOfficeMapper;
import com.example.demo.mapper.OperateMapper;

@Service
public class OperateService {

	OperateMapper operateMapper;
	HeadOfficeMapper headOfficeMapper;
	private final RestTemplate restTemplate = new RestTemplate();

	@Value("${public-data.holiday.service-key:}")
	private String holidayServiceKey;

	public OperateService(OperateMapper operateMapper, HeadOfficeMapper headOfficeMapper) {
		this.operateMapper = operateMapper;
		this.headOfficeMapper = headOfficeMapper;
	}

	public String NowDateKey() {
		String accountKey = operateMapper.NowDateKey();
		return accountKey;
	}

	// 공휴일 목록 조회
	public List<Map<String, Object>> HolidayList(Map<String, Object> paramMap) {
		List<Map<String, Object>> resultList = new ArrayList<>();
		resultList = operateMapper.HolidayList(paramMap);
		return resultList;
	}

	// 급식사업부 -> 운영관리 -> 집계표 조회
	public List<Map<String, Object>> TallySheetList(Map<String, Object> paramMap) {
		List<Map<String, Object>> resultList = new ArrayList<>();
		resultList = operateMapper.TallySheetList(paramMap);
		return resultList;
	}

	// 급식사업부 -> 운영관리 -> 집계표 전체 업장 조회 (엑셀 다운로드 전용)
	public List<Map<String, Object>> TallySheetAllList(Map<String, Object> paramMap) {
		List<Map<String, Object>> resultList = new ArrayList<>();
		resultList = operateMapper.TallySheetAllList(paramMap);
		return resultList;
	}

	// 급식사업부 -> 운영관리 -> 집계표 메모 조회
	public Map<String, Object> TallySheetNote(Map<String, Object> paramMap) {
		Map<String, Object> resultMap = new HashMap<String, Object>();
		resultMap = operateMapper.TallySheetNote(paramMap);
		return resultMap;
	}

	// 급식사업부 -> 운영관리 -> 집계표(본월) 저장
	public int TallyNowMonthSave(Map<String, Object> paramMap) {
		int iResult = 0;
		iResult = operateMapper.TallyNowMonthSave(paramMap);
		return iResult;
	}

	// 급식사업부 -> 운영관리 -> 집계표(이월) 저장
	public int TallyBeforeMonthSave(Map<String, Object> paramMap) {
		int iResult = 0;
		iResult = operateMapper.TallyBeforeMonthSave(paramMap);
		return iResult;
	}

	// 급식사업부 -> 운영관리 -> 집계표 메모 저장
	public int TallySheetNoteSave(Map<String, Object> paramMap) {
		int iResult = 0;
		iResult = operateMapper.TallySheetNoteSave(paramMap);
		if (iResult == 0) {
			iResult = operateMapper.TallySheetNoteInitSave(paramMap);
		}
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

	@Transactional(rollbackFor = Exception.class) // ✅ 전체 작업 트랜잭션
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

		// 손익 집계가 확정된 월부터 소모품 예산과 누계를 함께 갱신
		param.put("result", 0);
		operateMapper.SuppliesBudgetSave(param);
		result = (int) param.get("result");
		if (result != 1) {
			throw new RuntimeException("❌ SuppliesBudgetSave 프로시저 실패");
		}

		return 1; // ✅ 전체 성공
	}

	// 손익 합계와 해당 월 이후의 소모품 예산 누계를 함께 갱신
	public void callProfitLossTotalSave(Map<String, Object> param) {
		param.put("result", 0);
		headOfficeMapper.ProfitLossTotalSave(param);
		int result = (int) param.get("result");
		if (result != 1) {
			throw new RuntimeException("❌ ProfitLossTotalSave 프로시저 실패");
		}

		param.put("result", 0);
		operateMapper.SuppliesBudgetSave(param);
		result = (int) param.get("result");
		if (result != 1) {
			throw new RuntimeException("❌ SuppliesBudgetSave 프로시저 실패");
		}
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

	// 운영/인사 근무형태 삭제(del_yn=Y)
	public int AccountMemberWorkSystemDelete(Map<String, Object> paramMap) {
		return operateMapper.AccountMemberWorkSystemDelete(paramMap);
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

	// 급식사업부 -> 운영->현장관리, 인사->현장관리 -> 전체 엑셀 조회
	public List<Map<String, Object>> AccountMemberAllListExcel(Map<String, Object> paramMap) {
		List<Map<String, Object>> resultList = new ArrayList<>();
		resultList = operateMapper.AccountMemberAllListExcel(paramMap);
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

	// 급식사업부 -> 운영관리 -> 예산관리(인건비, 매출대비 45% 이상) 조회
	public List<Map<String, Object>> PersonCostBudgetList(Map<String, Object> paramMap) {
		List<Map<String, Object>> resultList = new ArrayList<>();
		resultList = operateMapper.PersonCostBudgetList(paramMap);
		return resultList;
	}

	// 업장별 일반 직원 급여와 유틸·통합 배부액을 포함한 인건비 예산 현황을 조회
	public List<Map<String, Object>> PersonCostBudgetProjectionList(Map<String, Object> paramMap) {
		List<Map<String, Object>> resultList = new ArrayList<>();
		resultList = operateMapper.PersonCostBudgetProjectionList(paramMap);
		return resultList;
	}

	// 급식사업부 -> 운영관리 -> 예산관리 저장
	public int BudgetTableSave(Map<String, Object> paramMap) {
		return operateMapper.BudgetTableSave(paramMap);
	}

	// 소모품 예산 누계 저장 — 소모품 저장 화면에서 저장 성공 시 백그라운드 호출
	@Transactional(rollbackFor = Exception.class)
	public int SuppliesBudgetSave(Map<String, Object> param) {
		param.put("result", 0); // OUT 파라미터 초기화
		operateMapper.SuppliesBudgetSave(param);
		int result = (int) param.get("result");
		if (result != 1) {
			throw new RuntimeException("SuppliesBudgetSave 프로시저 실패");
		}
		return 1;
	}

	@Transactional(rollbackFor = Exception.class) // ✅ 전체 작업 트랜잭션
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

		// 소모품 예산 누계 갱신
		param.put("result", 0);
		operateMapper.SuppliesBudgetSave(param);
		result = (int) param.get("result");
		if (result != 1) {
			throw new RuntimeException("❌ SuppliesBudgetSave 프로시저 실패");
		}

		return 1; // ✅ 전체 성공
	}

	// 현장관리 -> 근태관리 -> 연차 정보 조회
	public List<Map<String, Object>> AnnualLeaveList(Map<String, Object> paramMap) {
		List<Map<String, Object>> resultList = new ArrayList<>();
		resultList = operateMapper.AnnualLeaveList(paramMap);
		return resultList;
	}

	// 현장관리 -> 근태관리 -> 연차 항목 삭제
	public int AnnualLeaveDelete(Map<String, Object> paramMap) {
		return operateMapper.AnnualLeaveDelete(paramMap);
	}

	// 현장관리 -> 근태관리 -> 프로시저 생성 연차 레코드 일괄 삭제
	public int AnnualLeaveDeleteProcedureRecords(Map<String, Object> paramMap) {
		return operateMapper.AnnualLeaveDeleteProcedureRecords(paramMap);
	}

	// 현장관리 -> 근태관리 -> 연차 항목 저장(INSERT/UPDATE)
	public int AnnualLeaveSave(Map<String, Object> paramMap) {
		return operateMapper.AnnualLeaveSave(paramMap);
	}

	// 현장관리 -> 근태관리 -> 연차부여여부 저장
	public int AnnualLeaveLedgerYnSave(Map<String, Object> paramMap) {
		return operateMapper.AnnualLeaveLedgerYnSave(paramMap);
	}

	// 현장관리 -> 근태관리 -> 시간외근무 항목 삭제
	public int OverTimeDelete(Map<String, Object> paramMap) {
		return operateMapper.OverTimeDelete(paramMap);
	}

	// 현장관리 -> 근태관리 -> 초과근무 조회
	public List<Map<String, Object>> OverTimeList(Map<String, Object> paramMap) {
		List<Map<String, Object>> resultList = new ArrayList<>();
		resultList = operateMapper.OverTimeList(paramMap);
		return resultList;
	}

	// 운영 -> 긴급인력 -> 업장별 요일 인력 기준 조회
	public List<Map<String, Object>> AccountRecordStandardList(Map<String, Object> paramMap) {
		List<Map<String, Object>> resultList = new ArrayList<>();
		resultList = operateMapper.AccountRecordStandardList(paramMap);
		return resultList;
	}

	// 운영 -> 긴급인력 -> 업장별 요일 인력 기준 저장
	public int AccountRecordStandardSave(Map<String, Object> paramMap) {
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
	public int TallySheetPointSave(Map<String, Object> paramMap) {
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
	public int TallySheetUseSave(Map<String, Object> paramMap) {
		int iResult = 0;
		iResult = operateMapper.TallySheetUseSave(paramMap);
		return iResult;
	}

	// 긴급인력관리 -> 근무가능지역 관리 -> 시도 조회
	public List<Map<String, Object>> SidoList(Map<String, Object> paramMap) {
		List<Map<String, Object>> resultList = new ArrayList<>();
		resultList = operateMapper.SidoList(paramMap);
		return resultList;
	}

	// 긴급인력관리 -> 근무가능지역 관리 -> 시군구 조회
	public List<Map<String, Object>> SigunguList(Map<String, Object> paramMap) {
		List<Map<String, Object>> resultList = new ArrayList<>();
		resultList = operateMapper.SigunguList(paramMap);
		return resultList;
	}

	// 긴급인력관리 -> 근무가능지역 관리 -> 읍면동 조회
	public List<Map<String, Object>> EupmyeondongList(Map<String, Object> paramMap) {
		List<Map<String, Object>> resultList = new ArrayList<>();
		resultList = operateMapper.EupmyeondongList(paramMap);
		return resultList;
	}

	// 긴급인력관리 -> 근무가능지역 관리 -> 권역루트 조회
	public List<Map<String, Object>> RootList(Map<String, Object> paramMap) {
		List<Map<String, Object>> resultList = new ArrayList<>();
		resultList = operateMapper.RootList(paramMap);
		return resultList;
	}

	// 긴급인력관리 -> 근무가능지역 관리 -> 권역루트 저장
	public int RootSave(Map<String, Object> paramMap) {
		int iResult = 0;
		iResult = operateMapper.RootSave(paramMap);
		return iResult;
	}

	// 긴급인력관리 -> 현재 출근부 현황 조회
	public List<Map<String, Object>> RecordSituationList(Map<String, Object> paramMap) {
		List<Map<String, Object>> resultList = new ArrayList<>();
		resultList = operateMapper.RecordSituationList(paramMap);
		return resultList;
	}

	// 긴급인력관리 -> 필수인력 조회
	public List<Map<String, Object>> RecordStandardList(Map<String, Object> paramMap) {
		List<Map<String, Object>> resultList = new ArrayList<>();
		resultList = operateMapper.RecordStandardList(paramMap);
		return resultList;
	}

	// 긴급인력관리 -> 인력정보 조회
	public List<Map<String, Object>> FieldPersonMasterList(Map<String, Object> paramMap) {
		List<Map<String, Object>> resultList = new ArrayList<>();
		resultList = operateMapper.FieldPersonMasterList(paramMap);
		return resultList;
	}

	// 긴급인력관리 -> 인력, 루트 매핑 조회
	public List<Map<String, Object>> PersonToRootList(Map<String, Object> paramMap) {
		List<Map<String, Object>> resultList = new ArrayList<>();
		resultList = operateMapper.PersonToRootList(paramMap);
		return resultList;
	}

	// 긴급인력관리 -> 긴급인력 조회
	public List<Map<String, Object>> EmergencyPersonList(Map<String, Object> paramMap) {
		List<Map<String, Object>> resultList = new ArrayList<>();
		resultList = operateMapper.EmergencyPersonList(paramMap);
		return resultList;
	}

	// 긴급인력관리 -> 인력, 근무가능지역 매핑 저장
	public int PersonToRootSave(Map<String, Object> paramMap) {
		int iResult = 0;
		iResult = operateMapper.PersonToRootSave(paramMap);
		return iResult;
	}

	// 긴급인력관리 -> 인력정보 저장
	public int FieldPersonSave(Map<String, Object> paramMap) {
		int iResult = 0;
		iResult = operateMapper.FieldPersonSave(paramMap);
		return iResult;
	}

	// 긴급인력관리 -> 긴급인력 채용정보 저장
	public int EmergencyPersonEmployment(Map<String, Object> paramMap) {
		int iResult = 0;
		iResult = operateMapper.EmergencyPersonEmployment(paramMap);
		return iResult;
	}

	// 긴급인력관리 -> 연락 이력 저장
	public int SaveCallHistory(Map<String, Object> paramMap) {
		int iResult = 0;
		iResult = operateMapper.SaveCallHistory(paramMap);
		return iResult;
	}

	// 현재 연도와 다음 연도의 한국 공휴일 정보 저장
	public int KoreaHolidaySync() {
		int iResult = 0;
		if (holidayServiceKey == null || holidayServiceKey.isBlank()) {
			return iResult;
		}

		int currentYear = LocalDate.now().getYear();
		for (int year = currentYear; year <= currentYear + 1; year++) {
			for (int month = 1; month <= 12; month++) {
				iResult += KoreaHolidaySync(year, month);
			}
		}
		return iResult;
	}

	// 한국천문연구원 공휴일 정보를 연월 단위로 조회
	private int KoreaHolidaySync(int year, int month) {
		int iResult = 0;
		String url = UriComponentsBuilder
				.fromHttpUrl("http://apis.data.go.kr/B090041/openapi/service/SpcdeInfoService/getRestDeInfo")
				.queryParam("ServiceKey", holidayServiceKey)
				.queryParam("solYear", year)
				.queryParam("solMonth", String.format("%02d", month))
				.queryParam("numOfRows", 100)
				.build(false)
				.toUriString();

		byte[] responseBytes = restTemplate.getForObject(url, byte[].class);
		if (responseBytes == null || responseBytes.length == 0) {
			return iResult;
		}
		String responseText = new String(responseBytes, StandardCharsets.UTF_8);

		NodeList itemList = getXmlDocument(responseText).getElementsByTagName("item");
		for (int i = 0; i < itemList.getLength(); i++) {
			Element item = (Element) itemList.item(i);
			if ("Y".equals(getXmlValue(item, "isHoliday"))) {
				iResult += HolidaySave(item);
			}
		}

		return iResult;
	}

	// 공공데이터 공휴일 항목을 DB 저장 형식으로 변환하는 메서드
	private int HolidaySave(Element item) {
		Map<String, Object> paramMap = new HashMap<String, Object>();
		paramMap.put("holiday_date", getXmlValue(item, "locdate"));
		paramMap.put("country_code", "KR");
		paramMap.put("country_name", "대한민국");
		paramMap.put("holiday_name", getXmlValue(item, "dateName"));
		paramMap.put("holiday_sn", getXmlValue(item, "seq"));

		return operateMapper.HolidaySave(paramMap);
	}

	// 공공데이터 XML 응답을 문서 객체로 변환하는 메서드
	private Document getXmlDocument(String responseText) {
		try {
			DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
			DocumentBuilder builder = factory.newDocumentBuilder();
			return builder.parse(new InputSource(new StringReader(responseText)));
		} catch (Exception e) {
			throw new IllegalStateException("공공데이터 공휴일 XML 응답 파싱 오류", e);
		}
	}

	// 공공데이터 XML 항목 태그 값 조회
	private String getXmlValue(Element item, String tagName) {
		if (item == null || item.getElementsByTagName(tagName).getLength() == 0) {
			return "";
		}

		return item.getElementsByTagName(tagName).item(0).getTextContent();
	}

	// ===== 급식사업부 -> 운영관리 -> 메뉴/레시피 관리 (메뉴 마스터) =====

	// page/pageSize가 같이 넘어오면 해당 페이지만, 없으면(과거 호출 호환) 전체를 조회한다.
	public List<Map<String, Object>> MenuList(Map<String, Object> paramMap) {
		Object pageSize = paramMap.get("pageSize");
		if (pageSize != null && !String.valueOf(pageSize).isBlank()) {
			int page = Integer.parseInt(String.valueOf(paramMap.getOrDefault("page", "1")));
			int size = Integer.parseInt(String.valueOf(pageSize));
			// 쿼리 파라미터로 넘어온 값은 문자열이라, LIMIT 바인딩 시 MyBatis가 숫자가 아닌
			// 문자열로 렌더링해 SQL 문법 에러가 난다(LIMIT ?, '20'). 반드시 Integer로 되돌려 넣는다.
			paramMap.put("pageSize", size);
			paramMap.put("offset", Math.max(0, (page - 1) * size));
		}
		return operateMapper.MenuList(paramMap);
	}

	// 메뉴 목록 전체 건수 (MenuList와 동일한 검색 조건 기준, 페이지네이션 표시용)
	public int MenuListCount(Map<String, Object> paramMap) {
		return operateMapper.MenuListCount(paramMap);
	}

	// 메뉴 신규 등록/수정 (menu_id 있으면 수정, 없으면 신규 채번 후 upsert)
	// menu_name_raw는 별도 입력 화면이 없어 일단 menu_name과 동일하게 채운다(추후 키워드 용도로 분리 예정).
	@Transactional
	public Map<String, Object> MenuSave(Map<String, Object> paramMap) {
		String menuId = (String) paramMap.get("menu_id");

		if (menuId == null || menuId.isBlank()) {
			menuId = operateMapper.NewMenuId();
			paramMap.put("menu_id", menuId);
		}

		Object menuNameRaw = paramMap.get("menu_name_raw");
		if (menuNameRaw == null || String.valueOf(menuNameRaw).isBlank()) {
			paramMap.put("menu_name_raw", paramMap.get("menu_name"));
		}

		operateMapper.MenuUpsert(paramMap);

		return operateMapper.MenuOne(paramMap);
	}

	public int MenuDelete(Map<String, Object> paramMap) {
		return operateMapper.MenuDelete(paramMap);
	}

	// ===== 급식사업부 -> 운영관리 -> 메뉴/레시피 관리 (표준 레시피 정보) =====

	public Map<String, Object> RecipeInfoGet(Map<String, Object> paramMap) {
		Map<String, Object> recipeInfo = operateMapper.RecipeInfoByMenuId(paramMap);
		return recipeInfo != null ? recipeInfo : new HashMap<>();
	}

	@Transactional
	public Map<String, Object> RecipeInfoSave(Map<String, Object> paramMap) {
		String menuId = (String) paramMap.get("menu_id");
		Map<String, Object> recipeInfo = ensureRecipeInfo(menuId, (String) paramMap.get("user_id"));

		paramMap.put("recipe_id", recipeInfo.get("recipe_id"));
		operateMapper.RecipeInfoUpdate(paramMap);

		Map<String, Object> lookup = new HashMap<>();
		lookup.put("menu_id", menuId);
		return operateMapper.RecipeInfoByMenuId(lookup);
	}

	// menu_id에 해당하는 tb_recipe_info가 없으면 빈 레코드를 만들어 recipe_id를 발급한다.
	private Map<String, Object> ensureRecipeInfo(String menuId, String userId) {
		Map<String, Object> lookup = new HashMap<>();
		lookup.put("menu_id", menuId);
		Map<String, Object> existing = operateMapper.RecipeInfoByMenuId(lookup);
		if (existing != null) return existing;

		Map<String, Object> menu = operateMapper.MenuOne(lookup);
		String menuName = menu != null ? String.valueOf(menu.get("menu_name")) : "";

		Map<String, Object> insertMap = new HashMap<>();
		insertMap.put("menu_id", menuId);
		insertMap.put("menu_name", menuName);
		insertMap.put("title", menuName);
		insertMap.put("summary", null);
		insertMap.put("servings_note", null);
		insertMap.put("ingredients_json", null);
		insertMap.put("steps_json", null);
		insertMap.put("tips_json", null);
		insertMap.put("storage_json", null);
		insertMap.put("allergens_json", null);
		insertMap.put("recipe_json", null);
		insertMap.put("user_id", userId);
		operateMapper.RecipeInfoInsert(insertMap); // insertMap에 recipe_id가 채워짐(useGeneratedKeys)

		return operateMapper.RecipeInfoByMenuId(lookup);
	}

	// ===== 급식사업부 -> 운영관리 -> 메뉴/레시피 관리 (레시피 식재료 상세) =====

	public List<Map<String, Object>> RecipeDetailList(Map<String, Object> paramMap) {
		return operateMapper.RecipeDetailListByMenuId(paramMap);
	}

	// 한 메뉴의 식재료 행 배열을 통째로 upsert (recipe_detail_id 있으면 수정, 없으면 신규)
	@Transactional
	public List<Map<String, Object>> RecipeDetailSaveAll(String menuId, List<Map<String, Object>> rows, String userId) {
		Map<String, Object> recipeInfo = ensureRecipeInfo(menuId, userId);
		Object recipeId = recipeInfo.get("recipe_id");

		for (Map<String, Object> row : rows) {
			row.put("menu_id", menuId);
			row.put("recipe_id", recipeId);
			row.put("user_id", userId);

			Object detailId = row.get("recipe_detail_id");
			if (detailId == null || String.valueOf(detailId).isBlank()) {
				row.put("recipe_detail_id", null); // 빈 문자열이면 AUTO_INCREMENT가 신규 채번하도록 null로 정규화
			}
			operateMapper.RecipeDetailUpsert(row);
		}

		Map<String, Object> lookup = new HashMap<>();
		lookup.put("menu_id", menuId);
		return operateMapper.RecipeDetailListByMenuId(lookup);
	}

	public int RecipeDetailDelete(Map<String, Object> paramMap) {
		return operateMapper.RecipeDetailDelete(paramMap);
	}

	// ===== 급식사업부 -> 운영관리 -> 메뉴/레시피 관리 (표준 식재료 마스터) =====

	// page/pageSize가 같이 넘어오면 해당 페이지만, 없으면(과거 호출 호환) 전체를 조회한다. (MenuList와 동일한 패턴)
	public List<Map<String, Object>> IngredientList(Map<String, Object> paramMap) {
		Object pageSize = paramMap.get("pageSize");
		if (pageSize != null && !String.valueOf(pageSize).isBlank()) {
			int page = Integer.parseInt(String.valueOf(paramMap.getOrDefault("page", "1")));
			int size = Integer.parseInt(String.valueOf(pageSize));
			// 쿼리 파라미터로 넘어온 값은 문자열이라, LIMIT 바인딩 시 MyBatis가 숫자가 아닌
			// 문자열로 렌더링해 SQL 문법 에러가 난다(LIMIT ?, '20'). 반드시 Integer로 되돌려 넣는다.
			paramMap.put("pageSize", size);
			paramMap.put("offset", Math.max(0, (page - 1) * size));
		}
		return operateMapper.IngredientList(paramMap);
	}

	// 식재료 목록 전체 건수 (IngredientList와 동일한 검색 조건 기준, 페이지네이션 표시용)
	public int IngredientListCount(Map<String, Object> paramMap) {
		return operateMapper.IngredientListCount(paramMap);
	}

	public List<Map<String, Object>> IngredientSearchList(Map<String, Object> paramMap) {
		return operateMapper.IngredientSearchList(paramMap);
	}

	// 식재료 즉석 등록: 이름/기준단위만 받아 새 ingredient_id를 채번해 등록
	// 같은 표준명(대소문자/앞뒤공백 무시)의 식재료가 이미 있으면 새로 만들지 않고 기존 것을 그대로 반환한다.
	// ingredient_name_raw는 별도 입력 화면이 없어 일단 ingredient_name_std와 동일하게 채운다(추후 키워드 용도로 분리 예정).
	@Transactional
	public Map<String, Object> IngredientQuickSave(Map<String, Object> paramMap) {
		Map<String, Object> existing = operateMapper.IngredientByName(paramMap);
		if (existing != null) return existing;

		Object nameRaw = paramMap.get("ingredient_name_raw");
		if (nameRaw == null || String.valueOf(nameRaw).isBlank()) {
			paramMap.put("ingredient_name_raw", paramMap.get("ingredient_name_std"));
		}

		String ingredientId = operateMapper.NewIngredientId();
		paramMap.put("ingredient_id", ingredientId);
		operateMapper.IngredientInsert(paramMap);
		return operateMapper.IngredientOne(paramMap);
	}

	// 식재료 단건 상세 조회 (레시피/메뉴 화면에서 식재료 상세 보완용)
	public Map<String, Object> IngredientGet(Map<String, Object> paramMap) {
		return operateMapper.IngredientOne(paramMap);
	}

	// 식재료 상세정보 수정. 표준명이 바뀌면 원본명도 같이 맞춰준다(위와 동일한 이유).
	@Transactional
	public Map<String, Object> IngredientUpdate(Map<String, Object> paramMap) {
		Object nameStd = paramMap.get("ingredient_name_std");
		if (nameStd != null && !String.valueOf(nameStd).isBlank()) {
			paramMap.put("ingredient_name_raw", nameStd);
		}
		operateMapper.IngredientUpdate(paramMap);
		return operateMapper.IngredientOne(paramMap);
	}

	// 식재료 관리 탭 전용 신규 등록/수정 (ingredient_id 있으면 수정, 없으면 신규 채번 후 등록).
	// 기존 IngredientQuickSave/IngredientUpdate를 그대로 재사용해 이름 중복 방지·raw 자동 동기화 로직을 공유한다.
	@Transactional
	public Map<String, Object> IngredientSave(Map<String, Object> paramMap) {
		Object ingredientId = paramMap.get("ingredient_id");
		if (ingredientId == null || String.valueOf(ingredientId).isBlank()) {
			return IngredientQuickSave(paramMap);
		}
		return IngredientUpdate(paramMap);
	}

	// 식재료 삭제. 레시피에서 이미 사용 중인 식재료는 tb_recipe_detail의 FK(ON DELETE RESTRICT)에 걸려
	// DataIntegrityViolationException이 나는데, 이걸 화면에서 이해할 수 있는 메시지로 바꿔서 던진다.
	public int IngredientDelete(Map<String, Object> paramMap) {
		try {
			return operateMapper.IngredientDelete(paramMap);
		} catch (DataIntegrityViolationException e) {
			throw new IllegalStateException("이미 레시피에서 사용 중인 식재료라 삭제할 수 없습니다.");
		}
	}

	// ===== 급식사업부 -> 운영관리 -> 메뉴/레시피 관리 (레시피 영상 - 유튜브 링크) =====

	public List<Map<String, Object>> RecipeVideoList(Map<String, Object> paramMap) {
		return operateMapper.RecipeVideoListByMenuId(paramMap);
	}

	// video_id 없으면 신규 등록, 있으면 수정. 저장 후 최신 목록을 통째로 돌려준다.
	@Transactional
	public List<Map<String, Object>> RecipeVideoSave(Map<String, Object> paramMap) {
		String menuId = (String) paramMap.get("menu_id");
		String userId = (String) paramMap.get("user_id");
		Map<String, Object> recipeInfo = ensureRecipeInfo(menuId, userId);
		paramMap.put("recipe_id", recipeInfo.get("recipe_id"));

		Object videoId = paramMap.get("video_id");
		if (videoId == null || String.valueOf(videoId).isBlank()) {
			operateMapper.RecipeVideoInsert(paramMap);
		} else {
			operateMapper.RecipeVideoUpdate(paramMap);
		}

		Map<String, Object> lookup = new HashMap<>();
		lookup.put("menu_id", menuId);
		return operateMapper.RecipeVideoListByMenuId(lookup);
	}

	public int RecipeVideoDelete(Map<String, Object> paramMap) {
		return operateMapper.RecipeVideoDelete(paramMap);
	}

	// ===== 급식사업부 -> 운영관리 -> 메뉴/레시피 관리 (레시피 이미지) =====

	public List<Map<String, Object>> RecipeImageList(Map<String, Object> paramMap) {
		return operateMapper.RecipeImageListByMenuId(paramMap);
	}

	// 이미지 파일은 공용 업로드 엔드포인트로 먼저 올리고, 여기서는 그 결과(file_url 등) 메타정보만 저장한다.
	@Transactional
	public List<Map<String, Object>> RecipeImageSave(Map<String, Object> paramMap) {
		String menuId = (String) paramMap.get("menu_id");
		String userId = (String) paramMap.get("user_id");
		Map<String, Object> recipeInfo = ensureRecipeInfo(menuId, userId);
		paramMap.put("recipe_id", recipeInfo.get("recipe_id"));
		paramMap.put("file_id", java.util.UUID.randomUUID().toString());

		operateMapper.RecipeImageInsert(paramMap);

		Map<String, Object> lookup = new HashMap<>();
		lookup.put("menu_id", menuId);
		return operateMapper.RecipeImageListByMenuId(lookup);
	}

	public int RecipeImageSetPrimary(Map<String, Object> paramMap) {
		return operateMapper.RecipeImageSetPrimary(paramMap);
	}

	public int RecipeImageDelete(Map<String, Object> paramMap) {
		return operateMapper.RecipeImageDelete(paramMap);
	}
}
