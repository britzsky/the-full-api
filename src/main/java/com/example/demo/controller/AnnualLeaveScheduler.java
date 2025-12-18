// 예: src/main/java/com/example/demo/scheduler/AnnualLeaveScheduler.java

package com.example.demo.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class AnnualLeaveScheduler {
	
	private static final Logger log = LoggerFactory.getLogger(AnnualLeaveScheduler.class);
	
    private final JdbcTemplate jdbcTemplate;

    public AnnualLeaveScheduler(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 매일 오전 8시(한국 시간 기준) 연차 자동 처리
     * cron 형식: 초 분 시 일 월 요일
     * 0 0 8 * * *  → 매일 08:00:00
     */
    @Scheduled(cron = "0 0 8 * * *", zone = "Asia/Seoul")
    public void runAnnualLeaveJob() {
        try {
            log.info("[AnnualLeaveScheduler] 연차 자동 처리 프로시저 시작");

            // OUT 파라미터가 필요없으면 단순 update 로 호출
            jdbcTemplate.update("CALL AnnualLeaveDailyJob()");

            log.info("[AnnualLeaveScheduler] 연차 자동 처리 프로시저 완료");
        } catch (Exception e) {
            log.error("[AnnualLeaveScheduler] 연차 자동 처리 중 오류", e);
        }
    }
}
