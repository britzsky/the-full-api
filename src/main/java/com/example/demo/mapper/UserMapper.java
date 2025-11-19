package com.example.demo.mapper;

import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserMapper {
	
	Map<String, Object> Login(Map<String, Object> paramMap);
	int UserRgt(Map<String, Object> paramMap);
	List<Map<String, Object>> UserRecordSheetList(Map<String, Object> paramMap);
	List<Map<String, Object>> UserMemberList(Map<String, Object> paramMap);
}
	