package com.example.demo.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.mapper.BusinessMapper;

@Service
public class BusinessService {

	BusinessMapper businessMapper;
	
	public BusinessService(BusinessMapper businessMapper) {
		this.businessMapper = businessMapper;
	}
	public String NowDateKey() {
		String accountKey = businessMapper.NowDateKey();
		return accountKey;
	}
	// 고객사 관리 -> TM관리 조회
	public List<Map<String, Object>> BusinessTeleAccountList(Map<String, Object> paramMap) {
		List<Map<String, Object>> resultList = new ArrayList<>();
		resultList = businessMapper.BusinessTeleAccountList(paramMap);
		return resultList;
	}
	// 급식사업부 -> 영업관리 -> TM업장 정보 저장
	public int BusinessTeleInfoSave (Map<String, Object> paramMap) {
		int iResult = 0;
		iResult = businessMapper.BusinessTeleInfoSave(paramMap);
		return iResult;
	}
	// 고객사 관리 -> TM 일자별 정보 저장
	public int BusinessDailySave (Map<String, Object> paramMap) {
		int iResult = 0;
		iResult = businessMapper.BusinessDailySave(paramMap);
		return iResult;
	}
	// 고객사 관리 -> TM 계약완료 거래처 저장
	public int BusinessContractSuccessSave (Map<String, Object> paramMap) {
		int iResult = 0;
		iResult = businessMapper.BusinessContractSuccessSave(paramMap);
		return iResult;
	}
	// 고객사 관리 -> TM 계약완료 거래처 저장
	public int BusinessContractSuccessSave_2 (Map<String, Object> paramMap) {
		int iResult = 0;
		iResult = businessMapper.BusinessContractSuccessSave_2(paramMap);
		return iResult;
	}
	// 고객사 정보 -> 거래처상세 이미지 업로드
	public int insertOrUpdateFile (Map<String, Object> paramMap) {
		int iResult = 0;
		iResult = businessMapper.insertOrUpdateFile(paramMap);
		return iResult;
	}
	// 고객사 정보 -> 거래처상세 이미지 조회
	public List<Map<String, Object>> AccountBusinessImgList (Map<String, Object> paramMap) {
		List<Map<String, Object>> resultList = new ArrayList<>();
		resultList = businessMapper.AccountBusinessImgList(paramMap);
		return resultList;
	}
	// 고객사 관리 -> 법인차량 조회
	public List<Map<String, Object>> CarSelectList(Map<String, Object> paramMap) {
		List<Map<String, Object>> resultList = new ArrayList<>();
		resultList = businessMapper.CarSelectList(paramMap);
		return resultList;
	}
	// 고객사 관리 -> 법인차량 조회
	public List<Map<String, Object>> CarList(Map<String, Object> paramMap) {
		List<Map<String, Object>> resultList = new ArrayList<>();
		resultList = businessMapper.CarList(paramMap);
		return resultList;
	}
	// 고객사 관리 -> 법인차량 이미지 조회
	public List<Map<String, Object>> CarFileList(Map<String, Object> paramMap) {
		List<Map<String, Object>> resultList = new ArrayList<>();
		resultList = businessMapper.CarFileList(paramMap);
		return resultList;
	}
	// 고객사 관리 -> 법인차량 저장
	public int CarSave (Map<String, Object> paramMap) {
		int iResult = 0;
		iResult = businessMapper.CarSave(paramMap);
		return iResult;
	}
	// 고객사 관리 -> 법인차량 이미지 삭제
	public int CarFileDelete (Map<String, Object> paramMap) {
		int iResult = 0;
		iResult = businessMapper.CarFileDelete(paramMap);
		return iResult;
	}
	// 고객사 관리 -> 법인차량 이미지 저장
	public void SaveCarFile(Map<String, Object> param) {
	    businessMapper.SaveCarFile(param);
	}
	// 고객사 관리 -> 법인차량 신규 저장
	public int CarNewSave (Map<String, Object> paramMap) {
		int iResult = 0;
		iResult = businessMapper.CarNewSave(paramMap);
		return iResult;
	}
	// 고객사 관리 -> 조리복 재고 조회
	public List<Map<String, Object>> CookWearList(Map<String, Object> paramMap) {
		List<Map<String, Object>> resultList = new ArrayList<>();
		resultList = businessMapper.CookWearList(paramMap);
		return resultList;
	}
	// 고객사 관리 -> 조리복 분출내역 조회
	public List<Map<String, Object>> CookWearOutList(Map<String, Object> paramMap) {
		List<Map<String, Object>> resultList = new ArrayList<>();
		resultList = businessMapper.CookWearOutList(paramMap);
		return resultList;
	}
	// 고객사 관리 -> 조리복 신규주문 내역 조회
	public List<Map<String, Object>> CookWearNewList(Map<String, Object> paramMap) {
		List<Map<String, Object>> resultList = new ArrayList<>();
		resultList = businessMapper.CookWearNewList(paramMap);
		return resultList;
	}
	// 고객사 관리 -> 조리복 재고 저장
	public int CookWearSave (Map<String, Object> paramMap) {
		int iResult = 0;
		iResult = businessMapper.CookWearSave(paramMap);
		return iResult;
	}
	// 고객사 관리 -> 조리복 분출내역 저장
	public int CookWearOutSave (Map<String, Object> paramMap) {
		int iResult = 0;
		iResult = businessMapper.CookWearOutSave(paramMap);
		return iResult;
	}	
	// 고객사 관리 -> 조리복 주문내역 저장
	public int CookWearNewSave (Map<String, Object> paramMap) {
		int iResult = 0;
		iResult = businessMapper.CookWearNewSave(paramMap);
		return iResult;
	}
	// 고객사 관리 -> 고객사 행사관리 조회
	public List<Map<String, Object>> AccountEventList(Map<String, Object> paramMap) {
		List<Map<String, Object>> resultList = new ArrayList<>();
		resultList = businessMapper.AccountEventList(paramMap);
		return resultList;
	}
	// 고객사 관리 -> 고객사 행사관리 이미지 조회
	public List<Map<String, Object>> EventFileList(Object event_id) {
		List<Map<String, Object>> files = new ArrayList<>();
		files = businessMapper.EventFileList(event_id);
		return files;
	}
	// 고객사 관리 -> 고객사 행사관리 이미지 삭제
	public int AccountEventFileDelete (Map<String, Object> paramMap) {
		int iResult = 0;
		iResult = businessMapper.AccountEventFileDelete(paramMap);
		return iResult;
	}
	// 고객사 관리 -> 고객사 행사관리 저장
	public int EventSave (Map<String, Object> paramMap) {
		int iResult = 0;
		iResult = businessMapper.EventSave(paramMap);
		return iResult;
	}
	// 고객사 관리 -> 고객사 행사관리 수정
	public int EventUpdate (Map<String, Object> paramMap) {
		int iResult = 0;
		iResult = businessMapper.EventUpdate(paramMap);
		return iResult;
	}
	// 고객사 관리 -> 고객사 행사관리 이미지 순번 최대값 조회
	public Integer GetNextImageOrder(int eventId) {
	    Integer maxOrder = businessMapper.GetMaxImageOrder(eventId);
	    return (maxOrder == null ? 1 : maxOrder + 1);
	}
	// 고객사 관리 -> 고객사 행사관리 이미지 저장
	public void SaveEventFile(Map<String, Object> param) {
	    businessMapper.SaveEventFile(param);
	}
	// 고객사 상세 관리 -> 추가 식단가 조회
	public List<Map<String, Object>> AccountEctDietList(Map<String, Object> paramMap) {
		List<Map<String, Object>> resultList = new ArrayList<>();
		resultList = businessMapper.AccountEctDietList(paramMap);
		return resultList;
	}
	// 고객사 상세 관리 -> 추가 식단가 저장
	@Transactional(rollbackFor = Exception.class)  // ✅ 전체 작업 트랜잭션
	public int AccountEctDietSave (Map<String, Object> paramMap) {
		int iResult = 0;
		iResult = businessMapper.AccountEctDietSave(paramMap);
		
//		if(iResult > 0) {
//			// 고객사 관리 -> 식수현황 -> 추가 식단가 수정
//			iResult += businessMapper.DinerNumberUpdate(paramMap);
//		}
		
		return iResult;
	}
}
