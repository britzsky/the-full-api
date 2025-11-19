package com.example.demo.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.example.demo.WebConfig;
import com.example.demo.config.GlobalExceptionHandler;
import com.example.demo.service.AccountService;
import com.example.demo.service.HeadOfficeService;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class MonthEndScheduler {
	
	private static final Logger log = LoggerFactory.getLogger(MonthEndScheduler.class);
	
	private AccountService accountService;
	private HeadOfficeService headOfficeService;

	@Autowired
    public MonthEndScheduler(AccountService accountService, HeadOfficeService headOfficeService, WebConfig webConfig) {
    	this.accountService = accountService;
    	this.headOfficeService = headOfficeService;
    }
	
	@PostConstruct
    public void runOnceOnStartup() {
        System.out.println("🚀 서버 시작 시 스케줄러 테스트 실행");
        runOnLastDayOfMonth(); // ✅ 실제 스케줄 메서드 호출
    }
	
	@Scheduled(cron = "0 0 17 * * *") // 매일저녁 5시
	public void runOnLastDayOfMonth() {
	    LocalDate today = LocalDate.now();

	    int year = today.getYear();          // ✅ 현재 연도
	    int month = today.getMonthValue();   // ✅ 현재 월 (1~12)
	    int day = today.getDayOfMonth();     // ✅ 현재 일
	    int lastDay = today.lengthOfMonth(); // ✅ 이 달의 마지막 날
	    String monthTwoDigit = String.format("%02d", month);  // ✅ 1 → 01, 9 → 09, 10 → 10
	    
	    Map<String, Object>paramMap = new HashMap<String, Object>();
	    List<Map<String, Object>> resultList = new ArrayList<>();
	    int iResult = 0;
	    
	    paramMap.put("count_year", year);
	    //paramMap.put("count_month", monthTwoDigit);
	    paramMap.put("count_month", "10");
	    
	    System.out.println("📅 오늘 날짜: " + year + "년 " + monthTwoDigit + "월 " + day + "일");
	    
	    try {
        	// 판장금 조회.
	        resultList = accountService.BatchForPayBack(paramMap);
	        // 손익표 저장
	        for (Map<String, Object> map : resultList) {
	        	iResult += headOfficeService.ProfitLossTableSave(map);
	        }
	        
		} catch (Exception e) {
			// TODO: handle exception
			log.error("❌ 스케줄러에서 오류 발생: {}", e.getMessage(), e);
		}
	}
}
