package com.example.demo.controller;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.example.demo.mapper.OperateMapper;

@Component
public class BudgetNoteCarryOverScheduler {

    private static final Logger log = LoggerFactory.getLogger(BudgetNoteCarryOverScheduler.class);

    private final OperateMapper operateMapper;

    public BudgetNoteCarryOverScheduler(OperateMapper operateMapper) {
        this.operateMapper = operateMapper;
    }

    // 매월 1일 00:10 실행 (한국 시간 기준)
    @Scheduled(cron = "0 10 0 1 * *", zone = "Asia/Seoul")
    public void runMonthly() {
        // ⚠️ LocalDate.now()는 서버 OS 기본 타임존(UTC) 기준이라 크론 발동 시점(KST 00:10)에
        //    아직 전날 날짜로 계산되는 버그가 있었음. 반드시 Asia/Seoul로 명시.
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
        carryOver(today.getYear(), today.getMonthValue());
    }

    public int carryOver(int year, int month) {
        try {
            log.info("[BudgetNoteCarryOver] 비고 이월 시작 - {}년 {}월", year, month);
            Map<String, Object> param = new HashMap<>();
            param.put("year", year);
            param.put("month", month);
            int updated = operateMapper.BudgetNoteCarryOver(param);
            log.info("[BudgetNoteCarryOver] 비고 이월 완료 - {}건 업데이트", updated);
            return updated;
        } catch (Exception e) {
            log.error("[BudgetNoteCarryOver] 비고 이월 중 오류", e);
            return 0;
        }
    }
}
