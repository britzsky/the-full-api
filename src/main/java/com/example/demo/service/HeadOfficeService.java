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
	// 본사 -> 캘린더 저장
	public int WeekMenuSave(Map<String, Object> paramMap) {
		return headOfficeMapper.WeekMenuSave(paramMap);
	};
	// 본사 -> 식단표 캘린더 조회
	public List<Map<String, Object>> WeekMenuList(Map<String, Object> paramMap) {
		return headOfficeMapper.WeekMenuList(paramMap);
	}
	// 본사 -> 식단표 당일 조회
	public List<Map<String, Object>> WeekMenuTodayList(Map<String, Object> paramMap) {
		return headOfficeMapper.WeekMenuTodayList(paramMap);
	}
	// 본사 -> 캘린더 저장2
	public int EventSave(Map<String, Object> paramMap) {
		return headOfficeMapper.EventSave(paramMap);
	};
	// 본사 -> 캘린더 조회2
	public List<Map<String, Object>> EventList(Map<String, Object> paramMap) {
		return headOfficeMapper.EventList(paramMap);
	}
	// 본사 -> 관리표 -> 인원증감 조회
	public List<Map<String, Object>> PeopleCountingList(Map<String, Object> paramMap) {
		return headOfficeMapper.PeopleCountingList(paramMap);
	}
	// 본사 -> 관리표 -> 손익표 저장
	public int ProfitLossTableSave(Map<String, Object> paramMap) {
		return headOfficeMapper.ProfitLossTableSave(paramMap);
	}
	// 본사 -> 관리표 -> 손익표 조회
	public List<Map<String, Object>> ProfitLossTableList(Map<String, Object> paramMap) {
		return headOfficeMapper.ProfitLossTableList(paramMap);
	}
	// 본사 -> 관리표 -> 손익표 엑셀다운
	public List<Map<String, Object>> ExcelDaownProfitLossTableList(Map<String, Object> paramMap) {
		return headOfficeMapper.ExcelDaownProfitLossTableList(paramMap);
	}
	// 본사 -> 관리표 -> 손익표 합계 및 비율 저장
	public void ProfitLossTotalSave(Map<String, Object> paramMap) {
		headOfficeMapper.ProfitLossTotalSave(paramMap);
	}	
	// 본사 -> 관리표 조회
	public List<Map<String, Object>> AccountManagermentTableList (Map<String, Object> paramMap) {
		List<Map<String, Object>> resultList = new ArrayList<>();
		resultList = headOfficeMapper.AccountManagermentTableList(paramMap);
		return resultList;
	}
	@Transactional(rollbackFor = Exception.class)  // ✅ 전체 작업 트랜잭션
    public int processProfitLoss(Map<String, Object> param) {

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
	// 본사 -> 관리표 -> 거래처 통계
	public List<Map<String, Object>> AccountMappingPurchaseList (Map<String, Object> paramMap) {
		List<Map<String, Object>> resultList = new ArrayList<>();
		resultList = headOfficeMapper.AccountMappingPurchaseList(paramMap);
		return resultList;
	}
	
	// 본사 -> 전자결재 관리 -> 전자결재 타입 리스트 조회
	public List<Map<String, Object>> HeadOfficeElectronicPaymentTypeList (Map<String, Object> paramMap) {
		List<Map<String, Object>> resultList = new ArrayList<>();
		resultList = headOfficeMapper.HeadOfficeElectronicPaymentTypeList(paramMap);
		return resultList;
	}
	
	// 본사 -> 전자결재 관리 -> 전자결재 문서 조회
	public List<Map<String, Object>> HeadOfficeElectronicPaymentList (Map<String, Object> paramMap) {
		List<Map<String, Object>> resultList = new ArrayList<>();
		resultList = headOfficeMapper.HeadOfficeElectronicPaymentList(paramMap);
		return resultList;
	}
	
	// 본사 -> 전자결재 관리 -> 소모품 구매 품의서 메인 저장(전자결제 main table)
	public int HeadOfficeElectronicPaymentSave(Map<String, Object> paramMap) {
		return headOfficeMapper.HeadOfficeElectronicPaymentSave(paramMap);
	};
	
	// 본사 -> 전자결재 관리 -> 소모품 구매 품의서 품목 저장(구매요청 item table)
	public int HeadOfficePurchaseRequestSave(Map<String, Object> paramMap) {
		return headOfficeMapper.HeadOfficePurchaseRequestSave(paramMap);
	};
	
	// 본사 -> 전자결재 관리 -> 부서목록 조회
	public List<Map<String, Object>> HeadOfficeDepartmentList (Map<String, Object> paramMap) {
		List<Map<String, Object>> resultList = new ArrayList<>();
		resultList = headOfficeMapper.HeadOfficeDepartmentList(paramMap);
		return resultList;
	}
	
	// 본사 -> 전자결재 관리 -> 부서목록 선택 시, 부서 직원 조회
	public List<Map<String, Object>> HeadOfficeUserListByDepartment (Map<String, Object> paramMap) {
		List<Map<String, Object>> resultList = new ArrayList<>();
		resultList = headOfficeMapper.HeadOfficeUserListByDepartment(paramMap);
		return resultList;
	}
}
