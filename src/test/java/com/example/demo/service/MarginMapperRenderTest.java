package com.example.demo.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

// 이익률 2벌 관리로 바뀐 매퍼 쿼리가 실제 MyBatis에서 오류 없이 SQL로 만들어지는지 확인 (DB 접속 없음)
class MarginMapperRenderTest {

	private static Configuration configuration;

	// 매퍼 XML 3개를 MyBatis로 읽어 둔다 (XML·동적 태그 문법 오류가 있으면 여기서 실패)
	@BeforeAll
	static void loadMappers() throws Exception {
		configuration = new Configuration();
		for (String name : new String[] { "AccountMapper.xml", "OperateMapper.xml", "HeadOfficeMapper.xml" }) {
			Path path = Paths.get("src/main/resources/mybatis-mapper", name);
			try (InputStream in = Files.newInputStream(path)) {
				new XMLMapperBuilder(in, configuration, path.toString(), configuration.getSqlFragments()).parse();
			}
		}
	}

	// 파라미터로 SQL을 만들어 돌려주는 함수
	private String sql(String id, Map<String, Object> param) {
		return configuration.getMappedStatement(id).getBoundSql(param).getSql();
	}

	private Map<String, Object> params(String... kv) {
		Map<String, Object> map = new HashMap<>();
		for (int i = 0; i < kv.length; i += 2)
			map.put(kv[i], kv[i + 1]);
		return map;
	}

	// 회계 조회: cost_yn=Y면 원가 테이블, 없으면 판가 테이블
	@Test
	void 회계_조회는_cost_yn에_따라_테이블을_고른다() {
		String ns = "com.example.demo.mapper.AccountMapper.";
		for (String id : new String[] { "AccountPurchaseTallyList", "AccountPurchaseTallyForTallyTab",
				"AccountPurchaseDetailList_tmp" }) {
			String cost = sql(ns + id, params("cost_yn", "Y", "sale_id", "1", "account_id", "1"));
			String main = sql(ns + id, params("sale_id", "1", "account_id", "1"));
			assertTrue(cost.contains("tb_account_purchase_tally_cost"), id + " 원가");
			assertFalse(main.contains("_cost"), id + " 판가");
		}
	}

	// 집계표·예산: cost_yn=Y일 때만 원가 테이블을 붙인다
	@Test
	void 집계표_예산은_cost_yn일_때만_원가를_본다() {
		String ns = "com.example.demo.mapper.OperateMapper.";
		for (String id : new String[] { "TallySheetList", "TallySheetAllList", "BudgetManageMentList" }) {
			String cost = sql(ns + id, params("cost_yn", "Y", "year", "2026", "month", "11", "account_id", "1"));
			String main = sql(ns + id, params("year", "2026", "month", "11", "account_id", "1"));
			assertTrue(cost.contains("tb_account_purchase_tally_cost"), id + " 원가");
			assertFalse(main.contains("tb_account_purchase_tally_cost"), id + " 판가");
		}
	}

	// 손익표: sale_yn=Y일 때만 이익률 차액을 붙인다 (전체·단일 거래처, 엑셀 포함)
	@Test
	void 손익표는_sale_yn일_때만_차액을_붙인다() {
		String ns = "com.example.demo.mapper.HeadOfficeMapper.";
		for (String id : new String[] { "ProfitLossTableList", "ExcelDownMonthProfitLossTableList",
				"ExcelDownProfitLossTableList" }) {
			for (String accountId : new String[] { "ALL", "20250819193620" }) {
				for (String month : new String[] { "0", "11" }) {
					String sale = sql(ns + id,
							params("sale_yn", "Y", "year", "2026", "month", month, "account_id", accountId));
					String base = sql(ns + id, params("year", "2026", "month", month, "account_id", accountId));
					assertTrue(sale.contains("tb_account_purchase_tally_cost"), id + " 판가 보기 " + accountId);
					assertFalse(base.contains("tb_account_purchase_tally_cost"), id + " 기본 " + accountId);
				}
			}
		}
	}
}
