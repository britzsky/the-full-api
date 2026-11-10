package com.example.demo.controller;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.example.demo.mapper.HeadOfficeMapper;

// 개인구매(FR) 월말 자동 반려 스케줄러
// - 상신한 달 말일 23:59:59까지 후첨·2차 결재가 끝나지 않은 개인구매 문서를 반려 처리한다.
@Component
public class PersonPurchaseAutoRejectScheduler {

    private static final Logger log = LoggerFactory.getLogger(PersonPurchaseAutoRejectScheduler.class);

    private final HeadOfficeMapper headOfficeMapper;

    public PersonPurchaseAutoRejectScheduler(HeadOfficeMapper headOfficeMapper) {
        this.headOfficeMapper = headOfficeMapper;
    }

    // 매월 1일 00:00 실행 (한국 시간 기준) - 지난달 상신 문서 대상
    @Scheduled(cron = "0 0 0 1 * *", zone = "Asia/Seoul")
    public void runMonthly() {
        LocalDate lastMonth = LocalDate.now(ZoneId.of("Asia/Seoul")).minusMonths(1);
        rejectNotCompleted(lastMonth.toString().substring(0, 7));
    }

    // 지정한 달(yyyy-MM)에 상신한 개인구매 중 최종 결재되지 않은 문서를 반려하는 메서드
    public int rejectNotCompleted(String regMonth) {
        try {
            Map<String, Object> param = new HashMap<>();
            param.put("reg_month", regMonth);
            param.put("comment", "[자동반려] " + regMonth + " 말일까지 구매일자·영수증 후첨 및 2차 결재가 완료되지 않았습니다.");
            int updated = headOfficeMapper.ElectronicPaymentFrMonthEndAutoReject(param);
            log.info("[PersonPurchaseAutoReject] {} 개인구매 자동 반려 {}건", regMonth, updated);
            return updated;
        } catch (Exception e) {
            log.error("[PersonPurchaseAutoReject] {} 개인구매 자동 반려 중 오류", regMonth, e);
            return 0;
        }
    }
}
