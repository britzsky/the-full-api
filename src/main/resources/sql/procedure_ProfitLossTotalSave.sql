-- 거래처별 월 손익표(인원추산·매출·매입·인건·간접·영업이익)를 계산해 tb_account_managerment_table에 저장하는 프로시저
-- 강남(20250819193630)은 인원추산을 항상 생계비(2)/일반식대(2) 기준으로 계산한다.
-- 식자재는 원가 기준이다: 웰스토리·아워홈(2026-11~)은 집계표(판가)에서 이익률 차액을 뺀다.
DROP PROCEDURE IF EXISTS `the_full`.`ProfitLossTotalSave`;


CREATE PROCEDURE `the_full`.`ProfitLossTotalSave`(
    IN p_year INT,
    IN p_month INT,
    IN p_account_id VARCHAR(30),
    OUT p_result INT
)
BEGIN
    DECLARE v_lastDay INT;
    DECLARE v_diet_price DOUBLE DEFAULT 0;
    DECLARE v_basic_price DOUBLE DEFAULT 0;
    DECLARE v_food_cost DOUBLE DEFAULT 0;
    DECLARE v_etc_cost DOUBLE DEFAULT 0;
    DECLARE v_type_food_cost DOUBLE DEFAULT 0;
	DECLARE v_type_etc_cost DOUBLE DEFAULT 0;
    DECLARE v_add_food_cost DOUBLE DEFAULT 0;
    DECLARE v_add_etc_cost DOUBLE DEFAULT 0;
	DECLARE v_payback_price DOUBLE DEFAULT 0;
	DECLARE v_dispatch_cost DOUBLE DEFAULT 0;
    DECLARE v_year VARCHAR(4);
    DECLARE v_month VARCHAR(2);
    DECLARE v_is_special_account TINYINT DEFAULT 0;
    DECLARE v_account_type TINYINT DEFAULT 0;
    -- 현장 법인카드 소모품, 식재료 금액
    DECLARE v_field_food_cost DOUBLE DEFAULT 0;
    DECLARE v_field_etc_cost DOUBLE DEFAULT 0;
    -- 현장 법인카드 소모품, 식재료 금액
    DECLARE v_purchase_food_cost DOUBLE DEFAULT 0;
    DECLARE v_purchase_etc_cost DOUBLE DEFAULT 0;
    DECLARE v_type INT;
    -- 웰스토리·아워홈 이익률 차액(판가 − 원가). 손익표는 원가 기준이라 집계표(판가) 합계에서 뺀다
    DECLARE v_margin_diff DOUBLE DEFAULT 0;
    DECLARE v_done INT DEFAULT 0;
    -- [추가] 초기투자비용 감가상각 계산용 변수
    DECLARE v_upfront_cost       DOUBLE DEFAULT 0;   -- tb_account_info.upfront_cost (초기투자비용)
    DECLARE v_contract_start     DATE   DEFAULT NULL; -- 계약 시작일
    DECLARE v_contract_end       DATE   DEFAULT NULL; -- 계약 종료일
    DECLARE v_total_months       INT    DEFAULT 0;   -- 계약 총 개월수
    DECLARE v_elapsed_months     INT    DEFAULT 0;   -- 계약 시작~처리 월까지 경과 개월수
    DECLARE v_monthly_deprec     DOUBLE DEFAULT 0;   -- 월 감가액: upfront_cost / total_months (purchase_total에 포함)
    DECLARE v_upfront_remaining  DOUBLE DEFAULT 0;   -- 잔존가치: upfront_cost - (월감가액 * 경과개월수) (컬럼 저장값)
    -- 인원추산을 생계비(2)/일반식대(2) 기준으로 계산할지 여부 (강남이면 1)
    DECLARE v_use_cost2_estimate TINYINT DEFAULT 0;

	DECLARE cur_account_type CURSOR FOR
			SELECT DISTINCT type
			FROM tb_account_mapping_v2
			WHERE account_id = p_account_id;

			DECLARE CONTINUE HANDLER FOR NOT FOUND SET v_done = 1;

    -- 예외 처리 핸들러
    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        SET p_result = 0;
        ROLLBACK;
    END;

    START TRANSACTION;

		-- ① 월의 마지막 날짜
		SET v_lastDay = DAY(LAST_DAY(CONCAT(p_year, '-', p_month, '-01')));

		-- ② 년, 월 문자열 세팅
		SET v_year = CAST(p_year AS CHAR);
		SET v_month = LPAD(CAST(p_month AS CHAR), 2, '0');

		-- 강남(20250819193630)은 인원추산을 생계비(2)/일반식대(2) 금액으로 계산
		SET v_use_cost2_estimate = (p_account_id = '20250819193630');

		-- ③ TB_ACCOUNT_INFO에서 가격 로드
		-- [수정] upfront_cost, contract_start, contract_end 추가 조회
		SELECT IFNULL(diet_price,0), IFNULL(basic_price,0),
		       IFNULL(upfront_cost,0), contract_start, contract_end
		INTO v_diet_price, v_basic_price,
		     v_upfront_cost, v_contract_start, v_contract_end
		FROM tb_account_info
		WHERE account_id = p_account_id
		LIMIT 1;

		-- [추가] 감가상각 계산
		IF v_upfront_cost > 0 AND v_contract_start IS NOT NULL AND v_contract_end IS NOT NULL THEN
		    SET v_total_months = PERIOD_DIFF(
		        DATE_FORMAT(v_contract_end,   '%Y%m'),
		        DATE_FORMAT(v_contract_start, '%Y%m')
		    ) + 1;

		    IF v_total_months > 0
		       AND (p_year * 100 + p_month) >= (YEAR(v_contract_start) * 100 + MONTH(v_contract_start))
		       AND (p_year * 100 + p_month) <= (YEAR(v_contract_end)   * 100 + MONTH(v_contract_end))
		    THEN
		        SET v_elapsed_months = PERIOD_DIFF(
		            p_year * 100 + p_month,
		            YEAR(v_contract_start) * 100 + MONTH(v_contract_start)
		        ) + 1;

		        SET v_monthly_deprec = ROUND(v_upfront_cost / v_total_months, 0);

		        -- 잔존가치: 해당 월 감가 적용 전 잔액
		        -- 1월(elapsed=1): upfront_cost - (monthly * 0) = 전액
		        -- 마지막달(elapsed=N): upfront_cost - (monthly * (N-1)) = 최종 잔액
		        SET v_upfront_remaining = v_upfront_cost - (v_monthly_deprec * (v_elapsed_months - 1));
		    END IF;
		END IF;

        -- 현장법인카드 내역에서 소모품, 식자재 구하기(식재료)
        SELECT IFNULL(SUM(T2.amount),0)
        INTO v_field_food_cost
		FROM tb_account_corporate_card_payment_list T
		JOIN tb_account_corporate_card_payment_detail_list T2
		ON T2.sale_id = T.sale_id
		WHERE T.account_id = p_account_id
		AND DATE_FORMAT(T.payment_dt, '%Y') = v_year
		AND DATE_FORMAT(T.payment_dt, '%m') = v_month
		AND T2.itemType in (1,3);

        -- 현장법인카드 내역에서 소모품, 식자재 구하기(소모품)
        SELECT IFNULL(SUM(T2.amount),0)
        INTO v_field_etc_cost
		FROM tb_account_corporate_card_payment_list T
		JOIN tb_account_corporate_card_payment_detail_list T2
		ON T2.sale_id = T.sale_id
		WHERE T.account_id = p_account_id
		AND DATE_FORMAT(T.payment_dt, '%Y') = v_year
		AND DATE_FORMAT(T.payment_dt, '%m') = v_month
		AND T2.itemType = 2;

        -- 매입내역 (개인구매)에서 소모품, 식자재 구하기(식재료)
        SELECT IFNULL(SUM(T2.amount),0)
        INTO v_purchase_food_cost
		FROM tb_account_purchase_tally T
		JOIN tb_account_purchase_tally_detail T2
		ON T2.sale_id = T.sale_id
		WHERE T.account_id = p_account_id
		AND DATE_FORMAT(T.saleDate, '%Y') = v_year
		AND DATE_FORMAT(T.saleDate, '%m') = v_month
		AND T2.itemType in (1,3)
        AND T.type = 1008;

        -- 매입내역 (개인구매)에서 내역에서 소모품, 식자재 구하기(소모품)
        SELECT IFNULL(SUM(T2.amount),0)
        INTO v_purchase_etc_cost
		FROM tb_account_purchase_tally T
		JOIN tb_account_purchase_tally_detail T2
		ON T2.sale_id = T.sale_id
		WHERE T.account_id = p_account_id
		AND DATE_FORMAT(T.saleDate, '%Y') = v_year
		AND DATE_FORMAT(T.saleDate, '%m') = v_month
		AND T2.itemType = 2
        AND T.type = 1008;

        -- account_type 구하기
        SELECT account_type
          INTO v_account_type
          FROM tb_account
		 WHERE account_id = p_account_id;

        -- [핵심] 이전 SELECT 문들로 인해 v_done이 1로 오염되었을 수 있으므로 0으로 리셋!
        SET v_done = 0;

        OPEN cur_account_type;

			type_loop: LOOP

				FETCH cur_account_type INTO v_type;

				IF v_done = 1 THEN
					LEAVE type_loop;
				END IF;

				IF v_type NOT IN (10, 42) THEN

					/* 현재 type의 식자재 금액 */
					SELECT IFNULL(
						SUM(
							COALESCE(T.day_1, 0)
							+ COALESCE(T.day_2, 0)
							+ COALESCE(T.day_3, 0)
							+ COALESCE(T.day_4, 0)
							+ COALESCE(T.day_5, 0)
							+ COALESCE(T.day_6, 0)
							+ COALESCE(T.day_7, 0)
							+ COALESCE(T.day_8, 0)
							+ COALESCE(T.day_9, 0)
							+ COALESCE(T.day_10, 0)
							+ COALESCE(T.day_11, 0)
							+ COALESCE(T.day_12, 0)
							+ COALESCE(T.day_13, 0)
							+ COALESCE(T.day_14, 0)
							+ COALESCE(T.day_15, 0)
							+ COALESCE(T.day_16, 0)
							+ COALESCE(T.day_17, 0)
							+ COALESCE(T.day_18, 0)
							+ COALESCE(T.day_19, 0)
							+ COALESCE(T.day_20, 0)
							+ COALESCE(T.day_21, 0)
							+ COALESCE(T.day_22, 0)
							+ COALESCE(T.day_23, 0)
							+ COALESCE(T.day_24, 0)
							+ COALESCE(T.day_25, 0)
							+ COALESCE(T.day_26, 0)
							+ COALESCE(T.day_27, 0)
							+ COALESCE(T.day_28, 0)
							+ COALESCE(T.day_29, 0)
							+ COALESCE(T.day_30, 0)
							+ COALESCE(T.day_31, 0)
						),
						0
					)
					INTO v_type_food_cost
					FROM tb_account_tally_sheet T
					JOIN tb_account_mapping T2
					  ON T2.type = T.type
					WHERE T.account_id = p_account_id
					  AND T.count_year = v_year
					  AND T.count_month = v_month
					  AND T.type = v_type
					  AND T2.account_type = 0;

					/* 웰스토리(1~4)·아워홈(1042)은 2026-11부터 판가로 집계표에 들어가므로,
					   그 달 이익률 차액(판가 master − 원가 master)을 빼서 원가로 계산한다 */
					SET v_margin_diff = 0;
					IF v_type IN (1, 2, 3, 4, 1042) AND (p_year * 100 + p_month) >= 202611 THEN
						SELECT IFNULL(SUM(IFNULL(P.total, 0) - IFNULL(C.total, 0)), 0)
						INTO v_margin_diff
						FROM tb_account_purchase_tally_cost C
						JOIN tb_account_purchase_tally P
						  ON P.sale_id = C.sale_id
						JOIN tb_account_mapping T2
						  ON T2.type = C.type
						WHERE C.account_id = p_account_id
						  AND C.type = v_type
						  AND C.saleDate >= STR_TO_DATE(CONCAT(v_year, '-', v_month, '-01'), '%Y-%m-%d')
						  AND C.saleDate <= LAST_DAY(STR_TO_DATE(CONCAT(v_year, '-', v_month, '-01'), '%Y-%m-%d'))
						  AND T2.account_type = 0;
					END IF;

					SET v_food_cost =
						  IFNULL(v_food_cost, 0)
						+ IFNULL(v_type_food_cost, 0)
						- IFNULL(v_margin_diff, 0);

					/* 현재 type의 소모품 금액 */
					SELECT IFNULL(
						SUM(
							COALESCE(T.day_1, 0)
							+ COALESCE(T.day_2, 0)
							+ COALESCE(T.day_3, 0)
							+ COALESCE(T.day_4, 0)
							+ COALESCE(T.day_5, 0)
							+ COALESCE(T.day_6, 0)
							+ COALESCE(T.day_7, 0)
							+ COALESCE(T.day_8, 0)
							+ COALESCE(T.day_9, 0)
							+ COALESCE(T.day_10, 0)
							+ COALESCE(T.day_11, 0)
							+ COALESCE(T.day_12, 0)
							+ COALESCE(T.day_13, 0)
							+ COALESCE(T.day_14, 0)
							+ COALESCE(T.day_15, 0)
							+ COALESCE(T.day_16, 0)
							+ COALESCE(T.day_17, 0)
							+ COALESCE(T.day_18, 0)
							+ COALESCE(T.day_19, 0)
							+ COALESCE(T.day_20, 0)
							+ COALESCE(T.day_21, 0)
							+ COALESCE(T.day_22, 0)
							+ COALESCE(T.day_23, 0)
							+ COALESCE(T.day_24, 0)
							+ COALESCE(T.day_25, 0)
							+ COALESCE(T.day_26, 0)
							+ COALESCE(T.day_27, 0)
							+ COALESCE(T.day_28, 0)
							+ COALESCE(T.day_29, 0)
							+ COALESCE(T.day_30, 0)
							+ COALESCE(T.day_31, 0)
						),
						0
					)
					INTO v_type_etc_cost
					FROM tb_account_tally_sheet T
					JOIN tb_account_mapping T2
					  ON T2.type = T.type
					WHERE T.account_id = p_account_id
					  AND T.count_year = v_year
					  AND T.count_month = v_month
					  AND T.type = v_type
					  AND T2.account_type = 1;

					SET v_etc_cost =
						  IFNULL(v_etc_cost, 0)
						+ IFNULL(v_type_etc_cost, 0);

				ELSE

				    /* type=10, 42 식재료 */
				    SELECT IFNULL(SUM(T2.amount), 0)
				    INTO v_add_food_cost
				    FROM tb_account_purchase_tally T
				    JOIN tb_account_purchase_tally_detail T2
				        ON T2.sale_id = T.sale_id
				    WHERE T.account_id = p_account_id
				      AND YEAR(T.saleDate) = v_year
				      AND MONTH(T.saleDate) = v_month
				      AND T2.itemType IN (1, 3)
				      AND T.type = v_type;

				    SET v_food_cost =
				          IFNULL(v_food_cost, 0)
				        + IFNULL(v_add_food_cost, 0);

				    /* type=10, 42 소모품 */
				    SELECT IFNULL(SUM(T2.amount), 0)
				    INTO v_add_etc_cost
				    FROM tb_account_purchase_tally T
				    JOIN tb_account_purchase_tally_detail T2
				        ON T2.sale_id = T.sale_id
				    WHERE T.account_id = p_account_id
				      AND YEAR(T.saleDate) = v_year
				      AND MONTH(T.saleDate) = v_month
				      AND T2.itemType = 2
				      AND T.type = v_type;

				    SET v_etc_cost =
				          IFNULL(v_etc_cost, 0)
				        + IFNULL(v_add_etc_cost, 0);

				END IF;

			END LOOP type_loop;

			CLOSE cur_account_type;

		-- ⑤ 파출금액
		SELECT
			IFNULL(SUM(salary),0)
		INTO v_dispatch_cost
		FROM tb_account_record_dispatch
		WHERE account_id = p_account_id
		  AND record_year = v_year
		  AND record_month = v_month
          AND pay_yn = 'Y';

		-- ⑥ 특수 거래처 (여러 ID 지정)
		SET v_is_special_account = (p_account_id IN (
			'20250819193455',
			'20250819193504',
			'20250819193603',
			'20250919162439',
            '20250819193620',
            '20250819193632',
            '20250819193615'
		));

		-- ⑦ INSERT or UPDATE
		INSERT INTO tb_account_managerment_table (account_id, year, month, update_dt)
		VALUES (p_account_id, p_year, p_month, CURDATE())
		ON DUPLICATE KEY UPDATE update_dt = CURDATE();

		/* ────────────────────────────────
		   1단계. 인원추산 계산
		   생계인원 = 생계비 / 말일 / basic_price
		   일반인원 = 일반식대 / 말일 / diet_price
		   강남(20250819193630)은 생계비(2)/일반식대(2)로 대신 계산.
		   ──────────────────────────────── */
		UPDATE tb_account_managerment_table
		SET
			living_estimate = ROUND(IFNULL(IF(v_use_cost2_estimate, living_cost2, living_cost) / NULLIF(v_lastDay, 0) / NULLIF(v_basic_price, 0), 0), 1),
			basic_estimate  = ROUND(IFNULL(IF(v_use_cost2_estimate, basic_cost2, basic_cost)   / NULLIF(v_lastDay, 0) / NULLIF(v_diet_price, 0), 0), 1),
			estimate_total  = living_estimate + basic_estimate,
			-- 개별 항목 비율은 소수점 4자리까지 정밀도를 유지한다
			-- 그래야 화면에 보이는 항목들을 그대로 더했을 때 소계와 어긋나지 않는다.
			living_estimate_ratio = ROUND(IFNULL(living_estimate / NULLIF(estimate_total, 0) * 100, 0), 4),
			basic_estimate_ratio  = ROUND(IFNULL(basic_estimate  / NULLIF(estimate_total, 0) * 100, 0), 4),
			-- 소계 비율은 2026-09-22부터 1자리 -> 2자리로 정밀도를 올렸다(최대잔여법 0.01% 단위 보정용).
			estimate_total_ratio  = ROUND(IFNULL(estimate_total / NULLIF(estimate_total, 0) * 100, 0), 2)
		WHERE account_id = p_account_id AND year = p_year AND month = p_month;

		/* ────────────────────────────────
		   2단계. 매출(sales_total) + 비율
		   ──────────────────────────────── */
		UPDATE tb_account_managerment_table
			SET
				-- 1. sales_total 계산 및 변수(@st) 할당
				sales_total = @st := (
					IFNULL(living_cost, 0)
					+ IFNULL(basic_cost, 0)
					+ IFNULL(employ_cost, 0)
                    + IFNULL(living_cost2, 0)
					+ IFNULL(basic_cost2, 0)
					+ IFNULL(employ_cost2, 0)
					+ IF(v_is_special_account, IFNULL(daycare_cost, 0), 0)
					+ IF(v_is_special_account, IFNULL(daycare_emp_cost, 0), 0)
					-- 판장금은 매출합계에 집계되지 않아야 함. + IFNULL(payback_price, 0)
                    + IFNULL(integrity_cost, 0)
                    + IFNULL(return_cost, 0)
				),

				-- 2. 각 항목별 비율 계산 (위에서 계산한 @st를 분모로 사용)
				-- 개별 항목 비율은 소수점 4자리 정밀도로 저장(표시는 화면/엑셀에서 2자리로 반올림)
                living_ratio2     = ROUND(IFNULL(living_cost2 / NULLIF(@st, 0) * 100, 0), 4),
				basic_ratio2      = ROUND(IFNULL(basic_cost2 / NULLIF(@st, 0) * 100, 0), 4),
				employ_ratio2     = ROUND(IFNULL(employ_cost2 / NULLIF(@st, 0) * 100, 0), 4),
				living_ratio      = ROUND(IFNULL(living_cost / NULLIF(@st, 0) * 100, 0), 4),
				basic_ratio       = ROUND(IFNULL(basic_cost / NULLIF(@st, 0) * 100, 0), 4),
				employ_ratio      = ROUND(IFNULL(employ_cost / NULLIF(@st, 0) * 100, 0), 4),
				daycare_ratio     = IF(v_is_special_account, ROUND(IFNULL(daycare_cost / NULLIF(@st, 0) * 100, 0), 4),0),
				daycare_emp_ratio = IF(v_is_special_account, ROUND(IFNULL(daycare_emp_cost / NULLIF(@st, 0) * 100, 0), 4),0),
				payback_ratio     = ROUND(IFNULL(payback_price / NULLIF(@st, 0) * 100, 0), 4),
                integrity_ratio   = ROUND(IFNULL(integrity_cost / NULLIF(@st, 0) * 100, 0), 4),
				return_ratio      = ROUND(IFNULL(return_cost / NULLIF(@st, 0) * 100, 0), 4),

				-- 3. 전체 비율 합산 (ROUND 처리)
				-- 판장금 비율 제외 IFNULL(payback_price / NULLIF(@st, 0) * 100, 0) +
				-- 매출소계는 판장금을 제외한 실제 매출소계 금액을 기준으로 계산한다.
				sales_total_ratio = ROUND(IFNULL(@st / NULLIF(@st, 0) * 100, 0), 2)

			WHERE account_id = p_account_id
			  AND year = p_year
			  AND month = p_month;

		/* ────────────────────────────────
		   3단계. 매입(purchase_total) + 비율
		   ──────────────────────────────── */
		UPDATE tb_account_managerment_table
		SET
			purchase_total =
				  IFNULL(food_process,0)
				+ IFNULL(dishwasher,0)
				+ IFNULL(cesco,0)
				+ IFNULL(water_puri,0)
				+ IFNULL(v_food_cost + v_field_food_cost + v_purchase_food_cost,0)
				+ IFNULL(event_cost,0)
				+ IFNULL(not_budget_cost,0)
				+ IFNULL(v_etc_cost + v_field_etc_cost + v_purchase_etc_cost,0)
				+ IFNULL(v_upfront_remaining,0),  -- [추가] 초기투자비용을 매입소계에 포함

			food_trash_ratio    = ROUND(IFNULL(food_process / NULLIF(sales_total,0) * 100, 0), 4),
			dishwasher_ratio    = ROUND(IFNULL(dishwasher   / NULLIF(sales_total,0) * 100, 0), 4),
			cesco_ratio         = ROUND(IFNULL(cesco        / NULLIF(sales_total,0) * 100, 0), 4),
			water_ratio         = ROUND(IFNULL(water_puri   / NULLIF(sales_total,0) * 100, 0), 4),
            -- 현장 법인카드 식재료, 소모품도 함께 합해야 함.
			food_ratio          = ROUND(IFNULL((v_food_cost + v_field_food_cost + v_purchase_food_cost) / NULLIF(sales_total,0) * 100, 0), 4),
			etc_ratio           = ROUND(IFNULL((v_etc_cost + v_field_etc_cost + v_purchase_etc_cost)   / NULLIF(sales_total,0) * 100, 0), 4),
            event_ratio         = ROUND(IFNULL(event_cost      / NULLIF(sales_total,0) * 100, 0), 4),
			not_budget_ratio    = ROUND(IFNULL(not_budget_cost  / NULLIF(sales_total,0) * 100, 0), 4),
            food_cost           = IFNULL(v_food_cost + v_field_food_cost + v_purchase_food_cost,0),
			etc_cost            = IFNULL(v_etc_cost + v_field_etc_cost + v_purchase_etc_cost,0),
			-- [추가] 잔존가치 저장 (초기투자비용 - 월감가액 * 경과개월수), 비율은 월 감가액 기준
			upfront_cost        = v_upfront_remaining,
			upfront_cost_ratio  = ROUND(IFNULL(v_upfront_remaining / NULLIF(sales_total, 0) * 100, 0), 4),
			purchase_total_ratio = ROUND(IFNULL(purchase_total / NULLIF(sales_total, 0) * 100, 0), 2)
		WHERE account_id = p_account_id AND year = p_year AND month = p_month;

		/* ────────────────────────────────
		   4단계. 인건(person_total) + 비율
		   ──────────────────────────────── */
		UPDATE tb_account_managerment_table
		SET
			person_total   = IFNULL(person_cost,0) + IFNULL(v_dispatch_cost,0),
			person_ratio   = ROUND(IFNULL(person_cost    / NULLIF(sales_total,0) * 100, 0), 4),
			dispatch_ratio = ROUND(IFNULL(v_dispatch_cost / NULLIF(sales_total,0) * 100, 0), 4),
			dispatch_cost  = IFNULL(v_dispatch_cost,0),
			person_total_ratio = ROUND(IFNULL(person_total / NULLIF(sales_total, 0) * 100, 0), 2)
		WHERE account_id = p_account_id AND year = p_year AND month = p_month;

		/* ────────────────────────────────
		   5단계. 간접(indirect_total) + 비율
		   ──────────────────────────────── */
		UPDATE tb_account_managerment_table
		SET
			duty_secure          = NULLIF(sales_total,0) * 0.1,
			indirect_total       = IFNULL(utility_bills,0) + IFNULL(duty_secure,0) + IFNULL(etc_indirect_cost,0),
			utility_ratio        = ROUND(IFNULL(utility_bills     / NULLIF(sales_total,0) * 100, 0), 4),
			duty_secure_ratio    = ROUND(IFNULL(duty_secure       / NULLIF(sales_total,0) * 100, 0), 4),
            etc_indirect_ratio   = ROUND(IFNULL(etc_indirect_cost / NULLIF(sales_total,0) * 100, 0), 4),
			indirect_total_ratio = ROUND(IFNULL(indirect_total / NULLIF(sales_total, 0) * 100, 0), 2)
		WHERE account_id = p_account_id AND year = p_year AND month = p_month;

		/* ────────────────────────────────
		   6단계. 영업이익 (business_profit)
		   -> 매출 - 매입 - 인건비 - 간접비
		   비율은 각 소계 비율의 차감으로 계산한다(화면 로직과 동일한 기준).
		   ──────────────────────────────── */
		UPDATE tb_account_managerment_table
		SET
			business_profit = IFNULL(sales_total,0) - IFNULL(purchase_total,0) - IFNULL(person_total,0) - IFNULL(indirect_total,0),
			business_profit_ratio = ROUND(
			    IFNULL(sales_total_ratio,0) - IFNULL(purchase_total_ratio,0) - IFNULL(person_total_ratio,0) - IFNULL(indirect_total_ratio,0), 2),
			update_dt = CURDATE()
		WHERE account_id = p_account_id AND year = p_year AND month = p_month;

		/* ────────────────────────────────
		   7단계. 총 영업이익
		   -> 매출 - 매입 - 인건비 - 간접비 + 판장금
		   비율은 매출소계비율 - 매입소계비율 - 인건소계비율 - 간접소계비율 + 판장금비율.
		   ──────────────────────────────── */
		UPDATE tb_account_managerment_table
		SET
			total_business_profit = IFNULL(business_profit,0) + IFNULL(payback_price,0),
			total_business_profit_ratio = ROUND(
			    IFNULL(sales_total_ratio,0) - IFNULL(purchase_total_ratio,0) - IFNULL(person_total_ratio,0) - IFNULL(indirect_total_ratio,0) + IFNULL(payback_ratio,0), 2),
			update_dt = CURDATE()
		WHERE account_id = p_account_id AND year = p_year AND month = p_month;

	COMMIT;
    SET p_result = 1;  -- 정상 완료 시 1 반환
END
