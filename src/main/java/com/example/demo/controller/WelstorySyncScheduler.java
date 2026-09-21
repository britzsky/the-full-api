package com.example.demo.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.example.demo.service.AccountService;

// 웰스토리 SW-FD 주문API 입고내역 동기화 스케줄러.
// 실제 처리 로직(토큰발급/API호출/DB저장)은 전부 AccountService의 Welstory* 메서드에 있고,
// 이 클래스는 다른 스케줄러들(HolidayScheduler 등)과 동일하게 "언제 실행할지 + 예외/로그 처리"만 담당한다.
@Component
public class WelstorySyncScheduler {

	private static final Logger log = LoggerFactory.getLogger(WelstorySyncScheduler.class);

	private final AccountService accountService;

	public WelstorySyncScheduler(AccountService accountService) {
		this.accountService = accountService;
	}

	// 매일 18시(KST) 웰스토리 입고내역을 조회하여 tb_account_purchase_tally(_detail)에 저장.
	// (당일 저녁에 그날치를 바로 받고, 늦게 확정되는 전일/전전일분도 같이 재조회해서 덮어쓴다)
	@Scheduled(cron = "${welstory.sync.cron:0 0 18 * * *}", zone = "Asia/Seoul")
	public void runWelstoryPurchaseSync() {
		try {
			log.info("[WelstorySyncScheduler] 웰스토리 입고내역 동기화 시작");

			int saveCount = accountService.WelstoryPurchaseSync();

			log.info("[WelstorySyncScheduler] 웰스토리 입고내역 동기화 완료: {}건", saveCount);
		} catch (Exception e) {
			log.error("[WelstorySyncScheduler] 웰스토리 입고내역 동기화 중 오류", e);
		}
	}
}