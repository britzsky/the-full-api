package com.example.demo.mapper;

import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface HeadOfficeMapper {
	
	int WeekMenuSave(Map<String, Object> paramMap);											// 본사 -> 식단표 저장
	List<Map<String, Object>> WeekMenuList(Map<String, Object> paramMap);					// 본사 -> 식단표 조회
	int EventSave(Map<String, Object> paramMap);											// 본사 -> 행사달력 저장
	List<Map<String, Object>> EventList(Map<String, Object> paramMap);						// 본사 -> 행사달력 조회
	List<Map<String, Object>> PeopleCountingList(Map<String, Object> paramMap);				// 본사 -> 인원증감 조회
	int ProfitLossTableSave(Map<String, Object> paramMap);									// 본사 -> 손익표 저장
	List<Map<String, Object>> ProfitLossTableList(Map<String, Object> paramMap);			// 본사 -> 손익표 조회
	void ProfitLossTotalSave(Map<String, Object> paramMap);									// 손익표 계산 프로시저
	List<Map<String, Object>> AccountManagermentTableList(Map<String, Object> paramMap); 	// 본사 -> 관리표 조회
}
	