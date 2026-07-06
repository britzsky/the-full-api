package com.example.demo.controller;

import java.time.LocalDate;
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
        LocalDate today = LocalDate.now();
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
