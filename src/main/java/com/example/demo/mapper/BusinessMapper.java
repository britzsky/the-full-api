package com.example.demo.mapper;

import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface BusinessMapper {
	
	List<Map<String, Object>> BusinessTeleAccountList(Map<String, Object> paramMap);// 급식사업부 -> 영업관리 -> TM기록 조회
	
	String NowDateKey();
	int BusinessTeleInfoSave(Map<String, Object> paramMap); 						// 고객사 관리 -> TM업장 정보 저장
	int BusinessDailySave(Map<String, Object> paramMap); 							// 고객사 관리 -> TM 일자별 정보 저장
	int BusinessContractSuccessSave(Map<String, Object> paramMap); 					// 고객사 관리 -> TM 계약완료 거래처 저장
	int BusinessContractSuccessSave_2(Map<String, Object> paramMap); 				// 고객사 관리 -> TM 계약완료 거래처 정보 저장
	List<Map<String, Object>> AccountBusinessImgList(Map<String, Object> paramMap); // 고객사 관리 -> 이미지 조회
	int insertOrUpdateFile(Map<String, Object> paramMap); 							// 고객사 관리 -> 이미지 저장
	List<Map<String, Object>> CarSelectList(Map<String, Object> paramMap);			// 고객사 관리 -> 법인차량 Select box 조회
	List<Map<String, Object>> CarList(Map<String, Object> paramMap);				// 고객사 관리 -> 법인차량 조회
	int CarSave(Map<String, Object> paramMap);										// 고객사 관리 -> 법인차량 저장
	int CarNewSave(Map<String, Object> paramMap);									// 고객사 관리 -> 법인차량 신규 저장
	List<Map<String, Object>> CookWearList(Map<String, Object> paramMap);			// 고객사 관리 -> 조리복 재고 조회
	List<Map<String, Object>> CookWearOutList(Map<String, Object> paramMap);		// 고객사 관리 -> 조리복 분출내역 조회
	List<Map<String, Object>> CookWearNewList(Map<String, Object> paramMap);		// 고객사 관리 -> 조리복 신규주문 내역 조회
	int CookWearSave(Map<String, Object> paramMap);									// 고객사 관리 -> 조리복 재고내역 저장
	int CookWearOutSave(Map<String, Object> paramMap);								// 고객사 관리 -> 조리복 분출내역 저장
	int CookWearNewSave(Map<String, Object> paramMap);								// 고객사 관리 -> 조리복 주문내역 저장
	List<Map<String, Object>> AccountEventList(Map<String, Object> paramMap);		// 고객사 관리 -> 고객사 행사관리 조회
	List<Map<String, Object>> EventFileList(Object event_id);						// 고객사 관리 -> 고객사 행사관리 이미지 조회
	int AccountEventFileDelete(Map<String, Object> paramMap);						// 고객사 관리 -> 고객사 행사관리 이미지 삭제
	int EventSave(Map<String, Object> paramMap);									// 고객사 관리 -> 고객사 행사관리 저장
	int EventUpdate(Map<String, Object> paramMap);									// 고객사 관리 -> 고객사 행사관리 수정
	Integer GetMaxImageOrder(int eventId);											// 고객사 관리 -> 고객사 행사관리 이미지 순번 최대값 조회
	void SaveEventFile(Map<String, Object> param);									// 고객사 관리 -> 고객사 행사관리 이미지 저장
}
