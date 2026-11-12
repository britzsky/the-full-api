package com.example.demo.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.example.demo.service.AccountService;

// 아워홈 TOS API 주문 입고내역 동기화 스케줄러.
// 실제 처리 로직(API호출/DB저장)은 AccountService의 Ourhome* 메서드에 있고,
// 이 클래스는 WelstorySyncScheduler와 동일하게 "언제 실행할지 + 예외/로그 처리"만 담당한다.
@Component
public class OurhomeSyncScheduler {

	private static final Logger log = LoggerFactory.getLogger(OurhomeSyncScheduler.class);

	private final AccountService accountService;

	public OurhomeSyncScheduler(AccountService accountService) {
		this.accountService = accountService;
	}

	// 매일 20시 10분(KST) 아워홈 입고내역(당일/전일/전전일/전전전일)을 조회하여 tb_account_purchase_tally(_detail)에 저장.
	// 웰스토리 동기화(20시)와 시간이 겹치지 않게 10분 뒤에 돈다. 로컬은 ourhome.sync.cron=- 로 끈다.
	@Scheduled(cron = "${ourhome.sync.cron:0 10 20 * * *}", zone = "Asia/Seoul")
	public void runOurhomePurchaseSync() {
		try {
			log.info("[OurhomeSyncScheduler] 아워홈 입고내역 동기화 시작");

			int saveCount = accountService.OurhomePurchaseSync();

			log.info("[OurhomeSyncScheduler] 아워홈 입고내역 동기화 완료: {}건", saveCount);
		} catch (Exception e) {
			log.error("[OurhomeSyncScheduler] 아워홈 입고내역 동기화 중 오류", e);
		}
	}
}
