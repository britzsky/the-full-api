package com.example.demo.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.mapper.AccountMapper;
import com.example.demo.mapper.HeadOfficeMapper;

@Service
public class AccountService {

	AccountMapper accountMapper;
	HeadOfficeMapper headOfficeMapper;
	
	public AccountService(AccountMapper accountMapper, HeadOfficeMapper headOfficeMapper) {
		this.accountMapper = accountMapper;
		this.headOfficeMapper = headOfficeMapper;
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

        return 1; // ✅ 전체 성공
    }
}
