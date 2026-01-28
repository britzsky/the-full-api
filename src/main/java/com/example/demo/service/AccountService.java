package com.example.demo.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.mapper.AccountMapper;
import com.example.demo.mapper.HeadOfficeMapper;
import com.example.demo.mapper.OperateMapper;

@Service
public class AccountService {

	AccountMapper accountMapper;
	HeadOfficeMapper headOfficeMapper;
	OperateMapper operateMapper;
	
	public AccountService(AccountMapper accountMapper, HeadOfficeMapper headOfficeMapper, OperateMapper operateMapper) {
		this.accountMapper = accountMapper;
		this.headOfficeMapper = headOfficeMapper;
		this.operateMapper = operateMapper;
	}
	
	public String NowDateKey() {
		String accountKey = accountMapper.NowDateKey();
		return accountKey;
	}
	
	public List<Map<String, Object>> AccountMemberList(int accountType) {
		List<Map<String, Object>> resultList = new ArrayList<>();
		resultList = accountMapper.AccountList(accountType);
		return resultList;
	}
	
	public List<Map<String, Object>> AccountDirectList() {
		List<Map<String, Object>> resultList = new ArrayList<>();
		resultList = accountMapper.AccountDirectList();
		return resultList;
	}
	
	// 거래처 -> 집계표 조회
	public List<Map<String, Object>> AccountTallySheetList(Map<String, Object> paramMap) {
		List<Map<String, Object>> resultList = new ArrayList<>();
		resultList = accountMapper.AccountTallySheetList(paramMap);
		return resultList;
	}
	// 거래처 -> 집계표 저장(예정)
	public int AccountSave(Map<String, Object> paramMap) {
		int iResult = 0;
		iResult = accountMapper.AccountSave(paramMap);
		return iResult;
	}
	// 거래처 -> 출근부 -> 파출직원 조회
	public List<Map<String, Object>> AccountRecordDispatchList(Map<String, Object> paramMap) {
		List<Map<String, Object>> resultList = new ArrayList<>();
		resultList = accountMapper.AccountRecordDispatchList(paramMap);
		return resultList;
	}
	// 거래처 -> 인사기록카드 조회
	public List<Map<String, Object>> AccountRecordMemberList(Map<String, Object> paramMap) {
		List<Map<String, Object>> resultList = new ArrayList<>();
		resultList = accountMapper.AccountRecordMemberList(paramMap);
		return resultList;
	}
	// 거래처 -> 출근부 -> 출근현황 조회
	public List<Map<String, Object>> AccountRecordSheetList(Map<String, Object> paramMap) {
		List<Map<String, Object>> resultList = new ArrayList<>();
		resultList = accountMapper.AccountRecordSheetList(paramMap);
		return resultList;
	}
	// 거래처 -> 출근부 -> 츨퇴근 시간 조회
	public List<Map<String, Object>> AccountMemberRecordTime(Map<String, Object> paramMap) {
		List<Map<String, Object>> resultList = new ArrayList<>();
    	resultList = accountMapper.AccountMemberRecordTime(paramMap);
		return resultList;
	}
	// 거래처 -> 출근부 -> 상용출근 정보 저장
	public int AccountMemberRecordSave(Map<String, Object> paramMap) {
		int iResult = 0;
		iResult = accountMapper.AccountMemberRecordSave(paramMap);
		return iResult;
	}
	// 거래처 -> 출근부 -> 채용현황 출근 정보 저장
	public int AccountMemberRecRecordSave(Map<String, Object> paramMap) {
		int iResult = 0;
		iResult = accountMapper.AccountMemberRecRecordSave(paramMap);
		return iResult;
	}
	// 거래처 -> 출근부 -> 파출출근 정보 저장
	public int AccountDispatchRecordSave(Map<String, Object> paramMap) {
		int iResult = 0;
		iResult = accountMapper.AccountDispatchRecordSave(paramMap);
		return iResult;
	}
	// 거래처 -> 출근부 -> 파출직원 정보 저장
	public int AccountDispatchMemberSave(Map<String, Object> paramMap) {
		int iResult = 0;
		iResult = accountMapper.AccountDispatchMemberSave(paramMap);
		return iResult;
	}
	// 거래처 -> 출근부 -> 연차대장 저장
	public int AccountAnnualLeaveLedgerSave(Map<String, Object> paramMap) {
		int iResult = 0;
		iResult = accountMapper.AccountAnnualLeaveLedgerSave(paramMap);
		return iResult;
	}
	// 거래처 -> 출근부 -> 초과대장 저장
	public int AccountOverTimeLedgerSave(Map<String, Object> paramMap) {
		int iResult = 0;
		iResult = accountMapper.AccountOverTimeLedgerSave(paramMap);
		return iResult;
	}
	// 거래처 -> 기물리스트 조회
	public List<Map<String, Object>> AccountPropertiesList(Map<String, Object> paramMap) {
		List<Map<String, Object>> resultList = new ArrayList<>();
		resultList = accountMapper.AccountPropertiesList(paramMap);
		return resultList;
	}
	// 거래처 -> 거래처상세 조회
	public List<Map<String, Object>> AccountInfoList(Map<String, Object> paramMap) {
		List<Map<String, Object>> resultList = new ArrayList<>();
		resultList = accountMapper.AccountInfoList(paramMap);
		return resultList;
	}
	// 거래처 -> 거래처상세 조회
	public List<Map<String, Object>> AccountInfoList_2(Map<String, Object> paramMap) {
		List<Map<String, Object>> resultList = new ArrayList<>();
		resultList = accountMapper.AccountInfoList_2(paramMap);
		return resultList;
	}
	// 거래처 -> 거래처상세 조회
	public List<Map<String, Object>> AccountInfoList_3(Map<String, Object> paramMap) {
		List<Map<String, Object>> resultList = new ArrayList<>();
		resultList = accountMapper.AccountInfoList_3(paramMap);
		return resultList;
	}
	// 거래처 -> 거래처상세 조회
	public List<Map<String, Object>> AccountInfoList_4(Map<String, Object> paramMap) {
		List<Map<String, Object>> resultList = new ArrayList<>();
		resultList = accountMapper.AccountInfoList_4(paramMap);
		return resultList;
	}
	// 거래처 -> 거래처상세 조회
	public List<Map<String, Object>> AccountInfoList_5(Map<String, Object> paramMap) {
		List<Map<String, Object>> resultList = new ArrayList<>();
		resultList = accountMapper.AccountInfoList_5(paramMap);
		return resultList;
	}
	// 거래처 -> 거래처상세 저장
	public int AccountInfoSave(Map<String, Object> paramMap) {
		int iResult = 0;
		iResult = accountMapper.AccountInfoSave(paramMap);
		return iResult;
	}
	public List<Map<String, Object>> AccountDietPriceHistoryList(Map<String, Object> paramMap) {
		List<Map<String, Object>> resultList = new ArrayList<>();
		resultList = accountMapper.AccountDietPriceHistoryList(paramMap);
		return resultList;
	}
	// 거래처 -> 거래처상세 -> 식단가 변경내역 저장 
	public int AccountDietPriceHistorySave(Map<String, Object> paramMap) {
		int iResult = 0;
		iResult = accountMapper.AccountDietPriceHistorySave(paramMap);
		return iResult;
	}
	// 거래처 -> 거래처상세 이미지 업로드
	public int insertOrUpdateFile (Map<String, Object> paramMap) {
		int iResult = 0;
		iResult = accountMapper.insertOrUpdateFile(paramMap);
		return iResult;
	}
	// 거래처 -> 거래처상세 이미지 조회
	public List<Map<String, Object>> AccountBusinessImgList (Map<String, Object> paramMap) {
		List<Map<String, Object>> resultList = new ArrayList<>();
		resultList = accountMapper.AccountBusinessImgList(paramMap);
		return resultList;
	}
	// 회계 -> 매출마감/미수잔액 조회
	public List<Map<String, Object>> AccountDeadlineBalanceList (Map<String, Object> paramMap) {
		List<Map<String, Object>> resultList = new ArrayList<>();
		resultList = accountMapper.AccountDeadlineBalanceList(paramMap);
		return resultList;
	}
	// 회계 -> 매출마감/미수잔액 입금내역 조회
	public List<Map<String, Object>> AccountDepositHistoryList (Map<String, Object> paramMap) {
		List<Map<String, Object>> resultList = new ArrayList<>();
		resultList = accountMapper.AccountDepositHistoryList(paramMap);
		return resultList;
	}
	// 회계 -> 매출마감/미수잔액 저장
	public int AccountDeadlineBalanceSave(Map<String, Object> paramMap) {
		int iResult = 0;
		iResult = accountMapper.AccountDeadlineBalanceSave(paramMap);
		return iResult;
	}
	// 회계 -> 매출마감/미수잔액 총 미수금액 저장
	public int AccountBalancePriceSave(Map<String, Object> paramMap) {
		int iResult = 0;
		iResult = accountMapper.AccountBalancePriceSave(paramMap);
		return iResult;
	}
	// 회계 -> 매출마감/미수잔액 입금내역 저장
	public int AccountDepositHistorySave(Map<String, Object> paramMap) {
		int iResult = 0;
		iResult = accountMapper.AccountDepositHistorySave(paramMap);
		return iResult;
	}
	
	// 회계 -> 회계 -> 타입별 차액 조회
	public List<Map<String, Object>> AccountDeadlineDifferencePriceSearch (Map<String, Object> paramMap) {
		List<Map<String, Object>> resultList = new ArrayList<>();
		resultList = accountMapper.AccountDeadlineDifferencePriceSearch(paramMap);
		return resultList;
	}
	
	// 회계 -> 마감자료 조회
	public List<Map<String, Object>> AccountDeadlineFilesList (Map<String, Object> paramMap) {
		List<Map<String, Object>> resultList = new ArrayList<>();
		resultList = accountMapper.AccountDeadlineFilesList(paramMap);
		return resultList;
	}
	// 회계 -> 마감자료 저장
	public int AccountDeadlineFilesSave(Map<String, Object> paramMap) {
		int iResult = 0;
		iResult = accountMapper.AccountDeadlineFilesSave(paramMap);
		return iResult;
	}
	
	// 운영,회계 -> 거래처 이슈 저장
	public int AccountIssueSave(Map<String, Object> paramMap) {
		int iResult = 0;
		iResult = accountMapper.AccountIssueSave(paramMap);
		return iResult;
	}
	
	// 운영,회계 -> 거래처 이슈 조회
	public List<Map<String, Object>> AccountIssueList (Map<String, Object> paramMap) {
		List<Map<String, Object>> resultList = new ArrayList<>();
		resultList = accountMapper.AccountIssueList(paramMap);
		return resultList;
	}
	/*
	 * 배치성 데이터 처리
	*/
	// 본사 -> 관리표 -> 손익표 (판장금)
	public List<Map<String, Object>> BatchForPayBack (Map<String, Object> paramMap) {
		List<Map<String, Object>> resultList = new ArrayList<>();
		resultList = accountMapper.BatchForPayBack(paramMap);
		return resultList;
	}
	
	@Transactional(rollbackFor = Exception.class)  // ✅ 전체 작업 트랜잭션
    public int processProfitLoss(Map<String, Object> param) {

        int result = 0;

        // ① 계좌 마감 잔액 저장
        if (accountMapper.AccountDeadlineBalanceSave(param) <= 0) {
            throw new RuntimeException("❌ AccountDeadlineBalanceSave 실패");
        }

        // ② 손익표 저장
        if (headOfficeMapper.ProfitLossTableSave(param) <= 0) {
            throw new RuntimeException("❌ ProfitLossTableSave 실패");
        }
        
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
	@Transactional(rollbackFor = Exception.class)  // ✅ 전체 작업 트랜잭션
    public int processProfitLossV2(Map<String, Object> param) {
		
		param.put("month", param.get("record_month"));
		param.put("year", param.get("record_year"));
		
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
	
	// 현장 -> 집계표 -> 영수증 매장 확인 조회
	public List<Map<String, Object>> AccountMappingList (String account_id) {
		List<Map<String, Object>> resultList = new ArrayList<>();
		resultList = accountMapper.AccountMappingList(account_id);
		return resultList;
	}
	// 현장 -> 집계표 -> 매입집계 저장
	public int AccountPurchaseSave(Map<String, Object> paramMap) {
		int iResult = 0;
		iResult = accountMapper.AccountPurchaseSave(paramMap);
		return iResult;
	}
	// 현장 -> 집계표 -> 매입집계 상세 저장
	public int AccountPurchaseDetailSave(Map<String, Object> paramMap) {
		int iResult = 0;
		iResult = accountMapper.AccountPurchaseDetailSave(paramMap);
		return iResult;
	}
	// 회계 -> 매입 -> 매입마감 조회
	public List<Map<String, Object>> AccountPurchaseTallyList (Map<String, Object> paramMap) {
		List<Map<String, Object>> resultList = new ArrayList<>();
		resultList = accountMapper.AccountPurchaseTallyList(paramMap);
		return resultList;
	}
	// 회계 -> 매입 -> 매입집계(임시) 조회
	public List<Map<String, Object>> AccountPurchaseDetailList_tmp (Map<String, Object> paramMap) {
		List<Map<String, Object>> resultList = new ArrayList<>();
		resultList = accountMapper.AccountPurchaseDetailList_tmp(paramMap);
		return resultList;
	}
	// 집계표 -> 결제 리스트 조회
	public List<Map<String, Object>> AccountPurchaseTallyPaymentList (Map<String, Object> paramMap) {
		List<Map<String, Object>> resultList = new ArrayList<>();
		resultList = accountMapper.AccountPurchaseTallyPaymentList(paramMap);
		return resultList;
	}
	// 회계 -> 매입 -> 매입집계 조회
	public List<Map<String, Object>> AccountPurchaseDetailList (Map<String, Object> paramMap) {
		List<Map<String, Object>> resultList = new ArrayList<>();
		resultList = accountMapper.AccountPurchaseDetailList(paramMap);
		return resultList;
	}
	// 회계 -> 본사 법인카드 목록 조회
	public List<Map<String, Object>> HeadOfficeCorporateCardList (Map<String, Object> paramMap) {
		List<Map<String, Object>> resultList = new ArrayList<>();
		resultList = accountMapper.HeadOfficeCorporateCardList(paramMap);
		return resultList;
	}
	// 회계 -> 본사 법인카드 결제내역 조회
	public List<Map<String, Object>> HeadOfficeCorporateCardPaymentList (Map<String, Object> paramMap) {
		List<Map<String, Object>> resultList = new ArrayList<>();
		resultList = accountMapper.HeadOfficeCorporateCardPaymentList(paramMap);
		return resultList;
	}
	// 회계 -> 본사 법인카드 결제 상세내역 조회
	public List<Map<String, Object>> HeadOfficeCorporateCardPaymentDetailList (Map<String, Object> paramMap) {
		List<Map<String, Object>> resultList = new ArrayList<>();
		resultList = accountMapper.HeadOfficeCorporateCardPaymentDetailList(paramMap);
		return resultList;
	}
	// 회계 -> 본사 법인카드 저장
	public int HeadOfficeCorporateCardSave(Map<String, Object> paramMap) {
		int iResult = 0;
		iResult = accountMapper.HeadOfficeCorporateCardSave(paramMap);
		return iResult;
	}
	// 회계 -> 본사 법인카드 결제내역 저장
	public int HeadOfficeCorporateCardPaymentSave(Map<String, Object> paramMap) {
		int iResult = 0;
		iResult = accountMapper.HeadOfficeCorporateCardPaymentSave(paramMap);
		return iResult;
	}
	// 회계 -> 본사 법인카드 상세내역 저장
	public int HeadOfficeCorporateCardPaymentDetailLSave(Map<String, Object> paramMap) {
		int iResult = 0;
		iResult = accountMapper.HeadOfficeCorporateCardPaymentDetailLSave(paramMap);
		return iResult;
	}
	// 회계 -> 현장 법인카드 목록 조회
	public List<Map<String, Object>> AccountCorporateCardList (Map<String, Object> paramMap) {
		List<Map<String, Object>> resultList = new ArrayList<>();
		resultList = accountMapper.AccountCorporateCardList(paramMap);
		return resultList;
	}
	// 회계 -> 현장 법인카드 결제내역 조회
	public List<Map<String, Object>> AccountCorporateCardPaymentList (Map<String, Object> paramMap) {
		List<Map<String, Object>> resultList = new ArrayList<>();
		resultList = accountMapper.AccountCorporateCardPaymentList(paramMap);
		return resultList;
	}
	// 회계 -> 현장 법인카드 결제 상세내역 조회
	public List<Map<String, Object>> AccountCorporateCardPaymentDetailList (Map<String, Object> paramMap) {
		List<Map<String, Object>> resultList = new ArrayList<>();
		resultList = accountMapper.AccountCorporateCardPaymentDetailList(paramMap);
		return resultList;
	}
	// 회계 -> 현장 법인카드 저장
	public int AccountCorporateCardSave(Map<String, Object> paramMap) {
		int iResult = 0;
		iResult = accountMapper.AccountCorporateCardSave(paramMap);
		return iResult;
	}
	// 회계 -> 본사 법인카드 결제내역 저장
	public int AccountCorporateCardPaymentSave(Map<String, Object> paramMap) {
		int iResult = 0;
		iResult = accountMapper.AccountCorporateCardPaymentSave(paramMap);
		return iResult;
	}
	// 회계 -> 본사 법인카드 상세내역 저장
	public int AccountCorporateCardPaymentDetailLSave(Map<String, Object> paramMap) {
		int iResult = 0;
		iResult = accountMapper.AccountCorporateCardPaymentDetailLSave(paramMap);
		return iResult;
	}
	// 회계 -> 현장 법인카드 집계표 적용, 손익표, 예산도 함께 적용해야 함.
	@Transactional(rollbackFor = Exception.class)  // ✅ 전체 작업 트랜잭션
	public int TallySheetCorporateCardPaymentSave(Map<String, Object> paramMap) {
		
		int result = 0;
		
		paramMap.put("result", 0); // OUT 값 초기화
		accountMapper.TallySheetCorporateCardPaymentSave(paramMap);
		// OUT 값 확인
        result = (int) paramMap.get("result");
        if (result != 1) {
            throw new RuntimeException("❌ sp_sync_corp_card_to_tally_sheet_one_day 프로시저 실패");
        }
		
        // ③ 손익표 합계 + 비율 저장 프로시저 호출
        paramMap.put("result", 0); // OUT 값 초기화
        headOfficeMapper.ProfitLossTotalSave(paramMap);
        
        // OUT 값 확인
        result = (int) paramMap.get("result");
        if (result != 1) {
            throw new RuntimeException("❌ ProfitLossTotalSave 프로시저 실패");
        }
        
        // 예산 저장 프로시저 호출
        paramMap.put("result", 0); // OUT 값 초기화
        operateMapper.BudgetTotalSave(paramMap);

        // OUT 값 확인
        result = (int) paramMap.get("result");
        if (result != 1) {
            throw new RuntimeException("❌ BudgetTotalSave 프로시저 실패");
        }
        
		return result;
	}
	// 회계 -> 본사 법인카드 집계표 적용, 손익표, 예산도 함께 적용해야 함.
	@Transactional(rollbackFor = Exception.class)  // ✅ 전체 작업 트랜잭션
	public int TallySheetCorporateCardPaymentSaveV2(Map<String, Object> paramMap) {
		
		int result = 0;
		
		paramMap.put("result", 0); // OUT 값 초기화
		accountMapper.TallySheetCorporateCardPaymentSaveV2(paramMap);
		// OUT 값 확인
        result = (int) paramMap.get("result");
        if (result != 1) {
            throw new RuntimeException("❌ sp_sync_corp_card_to_tally_sheet_one_day_v2 프로시저 실패");
        }
		
        // ③ 손익표 합계 + 비율 저장 프로시저 호출
        paramMap.put("result", 0); // OUT 값 초기화
        headOfficeMapper.ProfitLossTotalSave(paramMap);
        
        // OUT 값 확인
        result = (int) paramMap.get("result");
        if (result != 1) {
            throw new RuntimeException("❌ ProfitLossTotalSave 프로시저 실패");
        }
        
        // 예산 저장 프로시저 호출
        paramMap.put("result", 0); // OUT 값 초기화
        operateMapper.BudgetTotalSave(paramMap);

        // OUT 값 확인
        result = (int) paramMap.get("result");
        if (result != 1) {
            throw new RuntimeException("❌ BudgetTotalSave 프로시저 실패");
        }
        
		return result;
	}
	// 집계표 -> 집계표 적용, 손익표, 예산도 함께 적용해야 함.
	@Transactional(rollbackFor = Exception.class)  // ✅ 전체 작업 트랜잭션
	public int TallySheetPaymentSave(Map<String, Object> paramMap) {
		
		int result = 0;
		
		paramMap.put("result", 0); // OUT 값 초기화
		accountMapper.TallySheetPaymentSave(paramMap);
		// OUT 값 확인
        result = (int) paramMap.get("result");
        if (result != 1) {
            throw new RuntimeException("❌ sp_sync_corp_card_to_tally_sheet_one_day 프로시저 실패");
        }
		
        // ③ 손익표 합계 + 비율 저장 프로시저 호출
        paramMap.put("result", 0); // OUT 값 초기화
        headOfficeMapper.ProfitLossTotalSave(paramMap);
        
        // OUT 값 확인
        result = (int) paramMap.get("result");
        if (result != 1) {
            throw new RuntimeException("❌ ProfitLossTotalSave 프로시저 실패");
        }
        
        // 예산 저장 프로시저 호출
        paramMap.put("result", 0); // OUT 값 초기화
        operateMapper.BudgetTotalSave(paramMap);

        // OUT 값 확인
        result = (int) paramMap.get("result");
        if (result != 1) {
            throw new RuntimeException("❌ BudgetTotalSave 프로시저 실패");
        }
        
		return result;
	}
}
