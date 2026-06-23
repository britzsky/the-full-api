package com.example.demo.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.example.demo.service.OperateService;

@Component
public class HolidayScheduler {

	private static final Logger log = LoggerFactory.getLogger(HolidayScheduler.class);

	private final OperateService operateService;

	public HolidayScheduler(OperateService operateService) {
		this.operateService = operateService;
	}

	// 매주 월요일 오전 8시 한국 공휴일 정보를 조회하여 저장하는 스케줄러
	@Scheduled(cron = "0 0 8 * * MON", zone = "Asia/Seoul")
	public void runKoreaHolidaySync() {
		try {
			log.info("[HolidayScheduler] 한국 공휴일 정보 저장 시작");

			int saveCount = operateService.KoreaHolidaySync();

			log.info("[HolidayScheduler] 한국 공휴일 정보 저장 완료: {}건", saveCount);
		} catch (Exception e) {
			log.error("[HolidayScheduler] 한국 공휴일 정보 저장 중 오류", e);
		}
	}
}
