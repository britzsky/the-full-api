package com.example.demo.service;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

// 웰스토리 FAQ "이익률 계산법" 판가 계산 검증 (공급사 화면 대조값 사용)
class MarginPriceTest {

	private static final BigDecimal R20 = BigDecimal.valueOf(20);

	// 아워홈은 추가 이익률에서 제외하고 웰스토리의 적용 시작일은 유지한다.
	@Test
	void 공급사별_추가_이익률_적용_대상() throws Exception {
		AccountService service = org.mockito.Mockito.mock(AccountService.class, org.mockito.Mockito.CALLS_REAL_METHODS);
		java.lang.reflect.Method method = AccountService.class.getDeclaredMethod("isMarginTarget", Object.class, Object.class);
		method.setAccessible(true);
		assertFalse((Boolean) method.invoke(service, "1042", "2026-11-12"));
		for (String type : new String[] { "1", "2", "3", "4" }) {
			assertTrue((Boolean) method.invoke(service, type, "2026-11-01"));
			assertFalse((Boolean) method.invoke(service, type, "2026-10-31"));
		}
	}

	// 원가 단가 110원 이상: 10원 단위 올림
	@Test
	void 단가_110원_이상은_10원_단위로_올린다() {
		assertEquals(6810, AccountService.marginSaleUnitPrice(5920, BigDecimal.valueOf(15))); // FAQ 예시 6,808 → 6,810
		assertEquals(10070, AccountService.marginSaleUnitPrice(8390, R20)); // 카레(분말)
		assertEquals(10680, AccountService.marginSaleUnitPrice(8900, R20)); // (W)돈육후지
		assertEquals(9980, AccountService.marginSaleUnitPrice(8310, R20)); // [GS]두부스틱
		assertEquals(3120, AccountService.marginSaleUnitPrice(2600, R20)); // 마가렛트
		assertEquals(21360, AccountService.marginSaleUnitPrice(17800, R20)); // [G]피양파
		assertEquals(4670, AccountService.marginSaleUnitPrice(3890, R20)); // 아워홈 단호박
		assertEquals(3770, AccountService.marginSaleUnitPrice(3140, R20)); // 아워홈 실곤약
	}

	// 원가 단가 110원 미만: 0.1원 자리 반올림, 1원 자리 유지
	@Test
	void 단가_110원_미만은_원_단위로_반올림한다() {
		assertEquals(120, AccountService.marginSaleUnitPrice(100, R20));
		assertEquals(109, AccountService.marginSaleUnitPrice(95, BigDecimal.valueOf(15))); // 109.25 → 109
		assertEquals(131, AccountService.marginSaleUnitPrice(109, R20)); // 130.8 → 131
	}

	// 판가 단가 × 수량 → 공급가, 과세면 부가세 10%
	@Test
	void 공급가와_부가세() {
		assertArrayEquals(new long[] { 39920, 3992 }, AccountService.marginSaleLine(9980, 4, true)); // 두부스틱 ×4
		assertArrayEquals(new long[] { 28080, 2808 }, AccountService.marginSaleLine(3120, 9, true)); // 마가렛트 ×9
		assertArrayEquals(new long[] { 10680, 0 }, AccountService.marginSaleLine(10680, 1, false)); // 면세
		assertArrayEquals(new long[] { 7540, 754 }, AccountService.marginSaleLine(3770, 2, true)); // 실곤약 ×2
	}

	// 정정 주문 취소 줄(음수 수량)은 재출고 줄과 정확히 상쇄된다
	@Test
	void 음수_수량은_부호만_바뀐다() {
		assertArrayEquals(new long[] { -7540, -754 }, AccountService.marginSaleLine(3770, -2, true));
		assertEquals(-10070, AccountService.marginSaleUnitPrice(-8390, R20));
	}
}
