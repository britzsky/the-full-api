package com.example.demo.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import com.example.demo.mapper.AccountMapper;
import com.example.demo.mapper.HeadOfficeMapper;
import com.example.demo.mapper.OperateMapper;

@Service
public class AccountService {

	private static final Logger log = LoggerFactory.getLogger(AccountService.class);

	AccountMapper accountMapper;
	HeadOfficeMapper headOfficeMapper;
	OperateMapper operateMapper;
	private final S3FileStorageService fileStorageService;

	// ===================== 웰스토리 SW-FD 주문API 연동 (필드) =====================
	// WelstorySyncScheduler가 매일 17시(KST)에 WelstoryPurchaseSync()를 호출 ->
	// 제휴사 2곳의 API를 각각 호출해 오늘자 입고내역을 tb_account_purchase_tally(_detail)에 저장한다.

	// 웰스토리 API 게이트웨이 base url (토큰발급: /oauth/2.0/token, 서비스: /fdapi/service/*)
	private static final String WELSTORY_BASE_URL = "https://welgw.welstory.com";
	// 스케줄러가 자동 저장할 때 user_id/mod_id 컬럼에 남길 고정값(실제 로그인 사용자가 없으므로)
	private static final String WELSTORY_SYNC_USER_ID = "SYSTEM";
	// 웰스토리 REST 호출 전용 RestTemplate (프로젝트 내 다른 외부 API 연동도 필드로 각자 생성하는 방식과 동일)
	private final RestTemplate restTemplate = new RestTemplate();
	// guid(19자리 거래식별자) 뒤에 붙는 2자리 seq 채번용 카운터.
	// 같은 밀리초에 여러 번 호출돼도 guid가 중복되지 않도록 함(가이드 스펙: 거래마다 고유해야 함)
	private final AtomicInteger welstoryGuidSeq = new AtomicInteger(0);

	// 제휴사 1: 주식회사 더채움 (payerCode A0275453) - 관리자에게 발급받은 OAuth client_credentials
	// application-secret*.properties 에 실제 값이 들어있고, 여기 base 파일에는 빈 값(플레이스홀더)만 존재
	@Value("${welstory.client1.id:}")
	private String welstoryClient1Id;
	@Value("${welstory.client1.secret:}")
	private String welstoryClient1Secret;

	// 제휴사 2: 더채움(위탁급식) (payerCode A0195993) - client1과 별개의 OAuth 인증정보
	@Value("${welstory.client2.id:}")
	private String welstoryClient2Id;
	@Value("${welstory.client2.secret:}")
	private String welstoryClient2Secret;

	// 문자열에서 숫자만 추출(최대 길이 제한)
	private static String keepOnlyDigits(String value, int maxLen) {
		if (value == null)
			return "";
		String digits = value.replaceAll("\\D", "");
		if (maxLen > 0 && digits.length() > maxLen) {
			return digits.substring(0, maxLen);
		}
		return digits;
	}

	// 주민등록번호 형식(######-#######) 정규화
	private static String formatDispatchRrn(Object rawValue) {
		if (rawValue == null)
			return null;
		String digits = keepOnlyDigits(String.valueOf(rawValue), 13);
		if (digits.isEmpty())
			return "";
		if (digits.length() <= 6)
			return digits;
		return digits.substring(0, 6) + "-" + digits.substring(6);
	}

	// 연락처 형식(010-0000-0000) 정규화
	private static String formatDispatchPhone(Object rawValue) {
		if (rawValue == null)
			return null;
		String digits = keepOnlyDigits(String.valueOf(rawValue), 11);
		if (digits.isEmpty())
			return "";
		if (digits.length() <= 3)
			return digits;
		if (digits.length() <= 7)
			return digits.substring(0, 3) + "-" + digits.substring(3);
		return digits.substring(0, 3) + "-" + digits.substring(3, 7) + "-" + digits.substring(7);
	}

	// 파출직원 민감정보(rrn/phone) 저장 전 정규화
	private static void normalizeDispatchMemberSensitiveFields(Map<String, Object> paramMap) {
		if (paramMap == null)
			return;

		if (paramMap.containsKey("rrn")) {
			Object rrn = paramMap.get("rrn");
			if (rrn != null) {
				paramMap.put("rrn", formatDispatchRrn(rrn));
			}
		}

		if (paramMap.containsKey("phone")) {
			Object phone = paramMap.get("phone");
			if (phone != null) {
				paramMap.put("phone", formatDispatchPhone(phone));
			}
		}
	}

	// Object를 trim 문자열로 변환
	private static String asText(Object value) {
		return value == null ? "" : String.valueOf(value).trim();
	}

	// 문자열 값 존재 여부 확인
	private static boolean hasText(String value) {
		return value != null && !value.trim().isEmpty();
	}

	// 조회 파라미터의 전체 선택값("0")을 빈 문자열로 정규화
	private static void normalizeZeroToEmpty(Map<String, Object> paramMap, String... keys) {
		if (paramMap == null || keys == null) {
			return;
		}
		for (String key : keys) {
			if (key == null || !paramMap.containsKey(key)) {
				continue;
			}
			Object raw = paramMap.get(key);
			if (raw == null) {
				continue;
			}
			if ("0".equals(String.valueOf(raw).trim())) {
				paramMap.put(key, "");
			}
		}
	}

	// 프론트에서 전달한 tax/item 타입 키를 히스토리 저장용 표준 키로 정규화
	private static void normalizeHistoryTypeFields(Map<String, Object> paramMap) {
		if (paramMap == null) {
			return;
		}

		String taxType = asText(paramMap.get("taxType"));
		if (!hasText(taxType)) {
			String altTaxType = asText(paramMap.get("taxtype"));
			if (hasText(altTaxType)) {
				paramMap.put("taxType", altTaxType);
			}
		}

		String itemType = asText(paramMap.get("itemType"));
		if (!hasText(itemType)) {
			String altItemType = asText(paramMap.get("itemtype"));
			if (hasText(altItemType)) {
				paramMap.put("itemType", altItemType);
			}
		}
	}

	// 이미지 경로를 /image/... 형태로 정규화
	private static String normalizeImagePath(String rawPath) {
		if (!hasText(rawPath))
			return "";
		String normalized = rawPath.trim().replace("\\", "/");

		int imageIndex = normalized.indexOf("/image/");
		if (imageIndex > 0) {
			normalized = normalized.substring(imageIndex);
		} else if (normalized.startsWith("image/")) {
			normalized = "/" + normalized;
		}

		return normalized;
	}

	// 물리 파일 영수증 이미지 삭제
	private void deletePhysicalReceiptImage(String imagePath) {
		String normalizedPath = normalizeImagePath(imagePath);
		if (!normalizedPath.startsWith("/image/")) {
			return;
		}
		try {
			fileStorageService.delete(normalizedPath);
		} catch (RuntimeException ignore) {
		}
	}

	// 영수증 이미지 변경 시 이전 파일 삭제
	private void deleteReplacedReceiptImage(String beforePath, Object afterPathObj, boolean hasAfterKey) {
		// 저장 payload에 해당 키가 없는 경우는 기존값 유지로 간주한다.
		if (!hasAfterKey) {
			return;
		}

		String oldPath = normalizeImagePath(beforePath);
		String newPath = normalizeImagePath(asText(afterPathObj));

		if (!hasText(oldPath)) {
			return;
		}

		// 빈 문자열로 저장하면 DB는 NULL로 업데이트되므로 기존 파일을 삭제한다.
		if (!hasText(newPath)) {
			deletePhysicalReceiptImage(oldPath);
			return;
		}

		if (oldPath.equals(newPath)) {
			return;
		}

		deletePhysicalReceiptImage(oldPath);
	}

	// 회계 -> OCR 컨트롤러에서 호출: 기존 영수증 경로 조회 (public)
	public String AccountPurchaseReceiptImageBySaleId(Map<String, Object> paramMap) {
		return findAccountPurchaseReceiptImage(paramMap);
	}

	// 회계 -> OCR 컨트롤러에서 호출: 기존 영수증(1~3번) 경로 조회 (public)
	public Map<String, Object> AccountPurchaseReceiptImagesBySaleId(Map<String, Object> paramMap) {
		return findAccountPurchaseReceiptImages(paramMap);
	}

	// 회계 -> OCR 컨트롤러에서 호출: 기존 파일 삭제 (public)
	public void DeleteOldReceiptImage(String oldPath, String newPath) {
		deleteReplacedReceiptImage(oldPath, newPath, true);
	}

	// 회계 -> 매입집계 기존 영수증 경로 조회
	private String findAccountPurchaseReceiptImage(Map<String, Object> paramMap) {
		String saleId = asText(paramMap.get("sale_id"));
		if (!hasText(saleId)) {
			return "";
		}

		Map<String, Object> queryMap = new HashMap<>();
		queryMap.put("sale_id", saleId);
		queryMap.put("account_id", asText(paramMap.get("account_id")));
		return asText(accountMapper.AccountPurchaseReceiptImageBySaleId(queryMap));
	}

	// 회계 -> 매입집계 기존 영수증(1~3번) 경로 조회
	private Map<String, Object> findAccountPurchaseReceiptImages(Map<String, Object> paramMap) {
		String saleId = asText(paramMap.get("sale_id"));
		if (!hasText(saleId)) {
			return new HashMap<>();
		}

		Map<String, Object> queryMap = new HashMap<>();
		queryMap.put("sale_id", saleId);
		queryMap.put("account_id", asText(paramMap.get("account_id")));
		Map<String, Object> resultMap = accountMapper.AccountPurchaseReceiptImagesBySaleId(queryMap);
		return resultMap != null ? resultMap : new HashMap<>();
	}

	// 회계 -> 본사 법인카드 기존 영수증 경로 조회
	private String findHeadOfficeCorporateReceiptImage(Map<String, Object> paramMap) {
		String saleId = asText(paramMap.get("sale_id"));
		if (!hasText(saleId)) {
			return "";
		}

		Map<String, Object> queryMap = new HashMap<>();
		queryMap.put("sale_id", saleId);
		queryMap.put("account_id", asText(paramMap.get("account_id")));
		return asText(accountMapper.HeadOfficeCorporateCardReceiptImageBySaleId(queryMap));
	}

	// 회계 -> 현장 법인카드 기존 영수증 경로 조회
	private String findAccountCorporateReceiptImage(Map<String, Object> paramMap) {
		String saleId = asText(paramMap.get("sale_id"));
		if (!hasText(saleId)) {
			return "";
		}

		Map<String, Object> queryMap = new HashMap<>();
		queryMap.put("sale_id", saleId);
		queryMap.put("account_id", asText(paramMap.get("account_id")));
		return asText(accountMapper.AccountCorporateCardReceiptImageBySaleId(queryMap));
	}

	// 공통 -> AccountService 생성자(Mapper/업로드 경로 주입)
	public AccountService(
			AccountMapper accountMapper,
			HeadOfficeMapper headOfficeMapper,
			OperateMapper operateMapper,
			S3FileStorageService fileStorageService) {
		this.accountMapper = accountMapper;
		this.headOfficeMapper = headOfficeMapper;
		this.operateMapper = operateMapper;
		this.fileStorageService = fileStorageService;
	}

	// 공통 -> 현재 날짜 키 조회
	public String NowDateKey() {
		String accountKey = accountMapper.NowDateKey();
		return accountKey;
	}

	// 거래처 -> 거래처 목록 조회
	public List<Map<String, Object>> AccountList(Map<String, Object> paramMap) {
		List<Map<String, Object>> resultList = new ArrayList<>();
		resultList = accountMapper.AccountList(paramMap);
		return resultList;
	}

	// 거래처 -> 거래처 목록 조회(V2)
	public List<Map<String, Object>> AccountListV2(Map<String, Object> paramMap) {
		List<Map<String, Object>> resultList = new ArrayList<>();
		resultList = accountMapper.AccountListV2(paramMap);
		return resultList;
	}

	// 유틸/통합 직원 조회 (position_type: 6=유틸, 7=통합, 미지정 시 전체)
	public List<Map<String, Object>> AccountUtilMemberList(Map<String, Object> paramMap) {
		List<Map<String, Object>> resultList = new ArrayList<>();
		resultList = accountMapper.AccountUtilMemberList(paramMap);
		return resultList;
	}

	// 유틸 출근부 -> 관리표(tb_account_managerment_table)에 등록된 거래처명 참고 목록
	public List<Map<String, Object>> AccountUtilRecordAccountNameList() {
		List<Map<String, Object>> resultList = new ArrayList<>();
		resultList = accountMapper.AccountUtilRecordAccountNameList();
		return resultList;
	}

	// 유틸 직원 매핑 정보 조회
	public List<Map<String, Object>> AccountUtilMappingList(Map<String, Object> paramMap) {
		List<Map<String, Object>> resultList = new ArrayList<>();
		resultList = accountMapper.AccountUtilMappingList(paramMap);
		return resultList;
	}

	// 유틸 직원 매핑 정보 저장
	public int AccountUtilMemberMappingSave(Map<String, Object> paramMap) {
		int iResult = 0;
		iResult = accountMapper.AccountUtilMemberMappingSave(paramMap);
		return iResult;
	}

	// 유틸 출근부 -> 엑셀 업로드 등록(upsert, 같은 날 여러 거래처 동시 배정 허용)
	public int AccountUtilRecordSave(Map<String, Object> paramMap) {
		int iResult = 0;
		iResult = accountMapper.AccountUtilRecordSave(paramMap);
		return iResult;
	}

	// 유틸 출근부 -> 엑셀 재업로드 시, 해당 연/월의 기존 배정(직원 단위)을 먼저 삭제
	public int AccountUtilRecordDeleteByMonth(Map<String, Object> paramMap) {
		int iResult = 0;
		iResult = accountMapper.AccountUtilRecordDeleteByMonth(paramMap);
		return iResult;
	}

	// 통합 출근부 -> tb_account_record 기준, 특정 직원의 해당 연/월 재택근무(20) 등록된 거래처 확인용 조회
	public List<Map<String, Object>> AccountIntegrationHomeRecordMonthList(Map<String, Object> paramMap) {
		List<Map<String, Object>> resultList = new ArrayList<>();
		resultList = accountMapper.AccountIntegrationHomeRecordMonthList(paramMap);
		return resultList;
	}

	// 통합 출근부 -> 재등록 시, 해당 연/월의 기존 재택근무(type=20)만 먼저 삭제(직원 단위, 다른 근무기록은 유지)
	public int AccountRecordType20DeleteByMonth(Map<String, Object> paramMap) {
		int iResult = 0;
		iResult = accountMapper.AccountRecordType20DeleteByMonth(paramMap);
		return iResult;
	}

	// 거래처 -> 직영 거래처 목록 조회
	public List<Map<String, Object>> AccountDirectList() {
		List<Map<String, Object>> resultList = new ArrayList<>();
		resultList = accountMapper.AccountDirectList();
		return resultList;
	}

	// 거래처 -> 집계표 조회
	public List<Map<String, Object>> AccountTallySheetList(Map<String, Object> paramMap) {
		List<Map<String, Object>> resultList = new ArrayList<>();
		resultList = accountMapper.AccountTallySheetList(paramMap);
		return resultList;
	}

	// 거래처 -> 집계표 저장(예정)
	public int AccountSave(Map<String, Object> paramMap) {
		int iResult = 0;
		iResult = accountMapper.AccountSave(paramMap);
		return iResult;
	}

	// 거래처 -> 출근부 -> 파출직원 조회
	public List<Map<String, Object>> AccountRecordDispatchList(Map<String, Object> paramMap) {
		List<Map<String, Object>> resultList = new ArrayList<>();
		resultList = accountMapper.AccountRecordDispatchList(paramMap);
		return resultList;
	}

	// 거래처 -> 출근부 -> 파출등록 이력 인원 조회
	public List<Map<String, Object>> AccountDispatchMemberHistoryList(Map<String, Object> paramMap) {
		List<Map<String, Object>> resultList = new ArrayList<>();
		resultList = accountMapper.AccountDispatchMemberHistoryList(paramMap);
		return resultList;
	}

	// 거래처 -> 인사기록카드 조회
	public List<Map<String, Object>> AccountRecordMemberList(Map<String, Object> paramMap) {
		List<Map<String, Object>> resultList = new ArrayList<>();
		resultList = accountMapper.AccountRecordMemberList(paramMap);
		return resultList;
	}

	// 거래처 -> 출근부 -> 출근현황 조회
	public List<Map<String, Object>> AccountRecordSheetList(Map<String, Object> paramMap) {
		List<Map<String, Object>> resultList = new ArrayList<>();
		resultList = accountMapper.AccountRecordSheetList(paramMap);
		return resultList;
	}

	// 거래처 -> 출근부 -> 츨퇴근 시간 조회
	public List<Map<String, Object>> AccountMemberRecordTime(Map<String, Object> paramMap) {
		List<Map<String, Object>> resultList = new ArrayList<>();
		resultList = accountMapper.AccountMemberRecordTime(paramMap);
		return resultList;
	}

	// 거래처 -> 출근부 -> 상용출근 정보 저장
	public int AccountMemberRecordSave(Map<String, Object> paramMap) {
		int iResult = 0;
		iResult = accountMapper.AccountMemberRecordSave(paramMap);
		return iResult;
	}

	// 거래처 -> 출근부 -> 채용현황 출근 정보 저장
	public int AccountMemberRecRecordSave(Map<String, Object> paramMap) {
		int iResult = 0;
		iResult = accountMapper.AccountMemberRecRecordSave(paramMap);
		return iResult;
	}

	// 거래처 -> 출근부 -> 파출출근 정보 저장
	public int AccountDispatchRecordSave(Map<String, Object> paramMap) {
		int iResult = 0;
		iResult = accountMapper.AccountDispatchRecordSave(paramMap);
		return iResult;
	}

	// 거래처 -> 출근부 -> 파출직원 정보 저장
	public int AccountDispatchMemberSave(Map<String, Object> paramMap) {
		int iResult = 0;
		// ✅ 백엔드 저장 직전 연락처/주민번호 포맷을 강제 정규화
		normalizeDispatchMemberSensitiveFields(paramMap);
		iResult = accountMapper.AccountDispatchMemberSave(paramMap);
		return iResult;
	}

	// 거래처 -> 출근부 -> 연차대장 기존 데이터 삭제 (member_id + ledger_dt 기준)
	public int AccountAnnualLeaveLedgerDelete(Map<String, Object> paramMap) {
		int iResult = 0;
		iResult = accountMapper.AccountAnnualLeaveLedgerDelete(paramMap);
		return iResult;
	}

	// 거래처 -> 출근부 -> 연차대장 저장
	public int AccountAnnualLeaveLedgerSave(Map<String, Object> paramMap) {
		int iResult = 0;
		iResult = accountMapper.AccountAnnualLeaveLedgerSave(paramMap);
		return iResult;
	}

	// 거래처 -> 출근부 -> 초과대장 기존 데이터 삭제 (member_id + over_dt 기준)
	public int AccountOverTimeLedgerDelete(Map<String, Object> paramMap) {
		int iResult = 0;
		iResult = accountMapper.AccountOverTimeLedgerDelete(paramMap);
		return iResult;
	}

	// 거래처 -> 출근부 -> 초과대장 저장
	public int AccountOverTimeLedgerSave(Map<String, Object> paramMap) {
		int iResult = 0;
		iResult = accountMapper.AccountOverTimeLedgerSave(paramMap);
		return iResult;
	}

	// 거래처 -> 기물리스트 조회
	public List<Map<String, Object>> AccountPropertiesList(Map<String, Object> paramMap) {
		List<Map<String, Object>> resultList = new ArrayList<>();
		resultList = accountMapper.AccountPropertiesList(paramMap);
		return resultList;
	}

	// 거래처 -> 거래처상세 조회
	public List<Map<String, Object>> AccountInfoList(Map<String, Object> paramMap) {
		List<Map<String, Object>> resultList = new ArrayList<>();
		resultList = accountMapper.AccountInfoList(paramMap);
		return resultList;
	}

	// 거래처 -> 거래처상세 조회
	public List<Map<String, Object>> AccountInfoList_2(Map<String, Object> paramMap) {
		List<Map<String, Object>> resultList = new ArrayList<>();
		resultList = accountMapper.AccountInfoList_2(paramMap);
		return resultList;
	}

	// 거래처 -> 거래처상세 조회
	public List<Map<String, Object>> AccountInfoList_3(Map<String, Object> paramMap) {
		List<Map<String, Object>> resultList = new ArrayList<>();
		resultList = accountMapper.AccountInfoList_3(paramMap);
		return resultList;
	}

	// 거래처 -> 거래처상세 조회
	public List<Map<String, Object>> AccountInfoList_4(Map<String, Object> paramMap) {
		List<Map<String, Object>> resultList = new ArrayList<>();
		resultList = accountMapper.AccountInfoList_4(paramMap);
		return resultList;
	}

	// 거래처 -> 거래처상세 조회
	public List<Map<String, Object>> AccountInfoList_5(Map<String, Object> paramMap) {
		List<Map<String, Object>> resultList = new ArrayList<>();
		resultList = accountMapper.AccountInfoList_5(paramMap);
		return resultList;
	}

	// 거래처 -> 거래처상세 저장
	public int AccountInfoSave(Map<String, Object> paramMap) {
		int iResult = 0;
		iResult = accountMapper.AccountInfoSave(paramMap);
		return iResult;
	}

	// 거래처 -> 거래처 좌표저장
	public int AccountCoordinateSave(Map<String, Object> paramMap) {
		int iResult = 0;
		iResult = accountMapper.AccountCoordinateSave(paramMap);
		return iResult;
	}

	// 거래처 -> 거래처상세 -> 식단가 변경내역 조회
	public List<Map<String, Object>> AccountDietPriceHistoryList(Map<String, Object> paramMap) {
		List<Map<String, Object>> resultList = new ArrayList<>();
		resultList = accountMapper.AccountDietPriceHistoryList(paramMap);
		return resultList;
	}

	// 거래처 -> 거래처상세 -> 식단가 변경내역 저장
	public int AccountDietPriceHistorySave(Map<String, Object> paramMap) {
		int iResult = 0;
		iResult = accountMapper.AccountDietPriceHistorySave(paramMap);
		return iResult;
	}

	// 거래처 -> 초기투자비용 현재값 조회
	public List<Map<String, Object>> AccountUpfrontHistoryList(Map<String, Object> paramMap) {
		List<Map<String, Object>> resultList = new ArrayList<>();
		resultList = accountMapper.AccountUpfrontHistoryList(paramMap);
		return resultList;
	}

	// 거래처 -> 초기투자비용 변경이력 저장
	public int AccountUpfrontHistorySave(Map<String, Object> paramMap) {
		int iResult = 0;
		iResult = accountMapper.AccountUpfrontHistorySave(paramMap);
		return iResult;
	}

	// 거래처 -> 거래처상세 이미지 업로드
	public int insertOrUpdateFile(Map<String, Object> paramMap) {
		int iResult = 0;
		iResult = accountMapper.insertOrUpdateFile(paramMap);
		return iResult;
	}

	// 거래처 -> 거래처상세 이미지 조회
	public List<Map<String, Object>> AccountBusinessImgList(Map<String, Object> paramMap) {
		List<Map<String, Object>> resultList = new ArrayList<>();
		resultList = accountMapper.AccountBusinessImgList(paramMap);
		return resultList;
	}

	// 회계 -> 매출마감/미수잔액 조회
	public List<Map<String, Object>> AccountDeadlineBalanceList(Map<String, Object> paramMap) {
		List<Map<String, Object>> resultList = new ArrayList<>();
		resultList = accountMapper.AccountDeadlineBalanceList(paramMap);
		return resultList;
	}

	// 회계 -> 매출마감/미수잔액 입금내역 조회
	public List<Map<String, Object>> AccountDepositHistoryList(Map<String, Object> paramMap) {
		List<Map<String, Object>> resultList = new ArrayList<>();
		resultList = accountMapper.AccountDepositHistoryList(paramMap);
		return resultList;
	}

	// 회계 -> 매출마감/미수잔액 저장
	public int AccountDeadlineBalanceSave(Map<String, Object> paramMap) {
		int iResult = 0;
		iResult = accountMapper.AccountDeadlineBalanceSave(paramMap);
		return iResult;
	}

	// 회계 -> 매출마감/미수잔액 월 보전금액 조회
	public int AccountDeadlineBalanceIntegrityCost(Map<String, Object> paramMap) {
		int iResult = 0;
		iResult = accountMapper.AccountDeadlineBalanceIntegrityCost(paramMap);
		return iResult;
	}

	// 회계 -> 매출마감/미수잔액 총 미수금액 저장
	public int AccountBalancePriceSave(Map<String, Object> paramMap) {
		int iResult = 0;
		iResult = accountMapper.AccountBalancePriceSave(paramMap);
		return iResult;
	}

	// 회계 -> 매출마감/미수잔액 입금내역 저장시, 월미수금액 저장
	public int AccountDeadlineMonthBalanceUpdate(Map<String, Object> paramMap) {
		int iResult = 0;
		iResult = accountMapper.AccountDeadlineMonthBalanceUpdate(paramMap);
		return iResult;
	}

	// 회계 -> 매출마감/미수잔액 입금내역 저장
	public int AccountDepositHistorySave(Map<String, Object> paramMap) {
		int iResult = 0;
		iResult = accountMapper.AccountDepositHistorySave(paramMap);
		return iResult;
	}

	// 회계 -> 미납 품목 전용 마감잔액 범위 조회
	public List<Map<String, Object>> AccountDeadlineBalanceListBulk(Map<String, Object> paramMap) {
		return accountMapper.AccountDeadlineBalanceListBulk(paramMap);
	}

	// 회계 -> 미납 품목 전용 입금내역 범위 조회
	public List<Map<String, Object>> AccountDepositHistoryListBulk(Map<String, Object> paramMap) {
		return accountMapper.AccountDepositHistoryListBulk(paramMap);
	}

	// 회계 -> 매출마감/미수잔액 입금내역 수정(입금일자, 비고)
	public int AccountDepositHistoryUpdate(Map<String, Object> paramMap) {
		int iResult = 0;
		iResult = accountMapper.AccountDepositHistoryUpdate(paramMap);
		return iResult;
	}

	// 회계 -> 회계 -> 타입별 차액 조회
	public List<Map<String, Object>> AccountDeadlineDifferencePriceSearch(Map<String, Object> paramMap) {
		List<Map<String, Object>> resultList = new ArrayList<>();
		resultList = accountMapper.AccountDeadlineDifferencePriceSearch(paramMap);
		return resultList;
	}

	// 회계 -> 마감자료 조회
	public List<Map<String, Object>> AccountDeadlineFilesList(Map<String, Object> paramMap) {
		List<Map<String, Object>> resultList = new ArrayList<>();
		resultList = accountMapper.AccountDeadlineFilesList(paramMap);
		return resultList;
	}

	// 회계 -> 마감자료 저장
	public int AccountDeadlineFilesSave(Map<String, Object> paramMap) {
		int iResult = 0;
		iResult = accountMapper.AccountDeadlineFilesSave(paramMap);
		return iResult;
	}

	// 운영,회계 -> 거래처 이슈 저장
	public int AccountIssueSave(Map<String, Object> paramMap) {
		int iResult = 0;
		iResult = accountMapper.AccountIssueSave(paramMap);
		return iResult;
	}

	// 운영,회계 -> 거래처 이슈 조회
	public List<Map<String, Object>> AccountIssueList(Map<String, Object> paramMap) {
		List<Map<String, Object>> resultList = new ArrayList<>();
		resultList = accountMapper.AccountIssueList(paramMap);
		return resultList;
	}

	// 운영,영업 -> 구분 조회
	public List<Map<String, Object>> AccountCommunicationMappingList(Map<String, Object> paramMap) {
		List<Map<String, Object>> resultList = new ArrayList<>();
		resultList = accountMapper.AccountCommunicationMappingList(paramMap);
		return resultList;
	}

	// 운영,영업 -> 구분 저장
	public int AccountCommunicationMappingSave(Map<String, Object> paramMap) {
		int iResult = 0;
		iResult = accountMapper.AccountCommunicationMappingSave(paramMap);
		return iResult;
	}

	// 운영,영업 -> 구분 삭제
	public int AccountCommunicationMappingDelete(Map<String, Object> paramMap) {
		int iResult = 0;
		iResult = accountMapper.AccountCommunicationMappingDelete(paramMap);
		return iResult;
	}

	// 운영,영업 -> 마감이슈, 고객사이슈 조회
	public List<Map<String, Object>> AccountCommunicationList(Map<String, Object> paramMap) {
		List<Map<String, Object>> resultList = new ArrayList<>();
		resultList = accountMapper.AccountCommunicationList(paramMap);
		return resultList;
	}

	// 운영,영업 -> 마감이슈, 고객사이슈 저장
	public int AccountCommunicationInsert(Map<String, Object> paramMap) {
		int iResult = 0;
		iResult = accountMapper.AccountCommunicationInsert(paramMap);
		return iResult;
	}

	// 운영,영업 -> 마감이슈, 고객사이슈 업데이트
	public int AccountCommunicationUpdate(Map<String, Object> paramMap) {
		int iResult = 0;
		iResult = accountMapper.AccountCommunicationUpdate(paramMap);
		return iResult;
	}

	/*
	 * 배치성 데이터 처리
	 */
	// 본사 -> 관리표 -> 손익표 (판장금)
	public List<Map<String, Object>> BatchForPayBack(Map<String, Object> paramMap) {
		List<Map<String, Object>> resultList = new ArrayList<>();
		resultList = accountMapper.BatchForPayBack(paramMap);
		return resultList;
	}

	// 배치 -> 마감잔액/손익표/예산 일괄 반영
	@Transactional(rollbackFor = Exception.class) // ✅ 전체 작업 트랜잭션
	public int processProfitLoss(Map<String, Object> param) {

		int result = 0;

		// ① 계좌 마감 잔액 저장
		if (accountMapper.AccountDeadlineBalanceSave(param) <= 0) {
			throw new RuntimeException("❌ AccountDeadlineBalanceSave 실패");
		}

		// ② 손익표 저장
		if (headOfficeMapper.ProfitLossTableSave(param) <= 0) {
			throw new RuntimeException("❌ ProfitLossTableSave 실패");
		}

		// ③ 손익표 합계 + 비율 저장 프로시저 호출
		param.put("result", 0); // OUT 값 초기화
		headOfficeMapper.ProfitLossTotalSave(param);

		// OUT 값 확인
		result = (int) param.get("result");
		if (result != 1) {
			throw new RuntimeException("❌ ProfitLossTotalSave 프로시저 실패");
		}

		// 예산 저장 프로시저 호출
		param.put("result", 0); // OUT 값 초기화
		operateMapper.BudgetTotalSave(param);

		// OUT 값 확인
		result = (int) param.get("result");
		if (result != 1) {
			throw new RuntimeException("❌ BudgetTotalSave 프로시저 실패");
		}

		// 소모품 예산 누계 저장 (ProfitLossTotalSave로 etc_cost 확정 후 실행)
		param.put("result", 0);
		operateMapper.SuppliesBudgetSave(param);
		result = (int) param.get("result");
		if (result != 1) {
			throw new RuntimeException("❌ SuppliesBudgetSave 프로시저 실패");
		}

		return 1; // ✅ 전체 성공
	}

	// 배치 -> 손익표/예산 재계산(record_year, record_month 기준)
	@Transactional(rollbackFor = Exception.class) // ✅ 전체 작업 트랜잭션
	public int processProfitLossV2(Map<String, Object> param) {

		param.put("month", param.get("record_month"));
		param.put("year", param.get("record_year"));

		int result = 0;

		// ③ 손익표 합계 + 비율 저장 프로시저 호출
		param.put("result", 0); // OUT 값 초기화
		headOfficeMapper.ProfitLossTotalSave(param);

		// OUT 값 확인
		result = (int) param.get("result");
		if (result != 1) {
			throw new RuntimeException("❌ ProfitLossTotalSave 프로시저 실패");
		}

		// 예산 저장 프로시저 호출
		param.put("result", 0); // OUT 값 초기화
		operateMapper.BudgetTotalSave(param);

		// OUT 값 확인
		result = (int) param.get("result");
		if (result != 1) {
			throw new RuntimeException("❌ BudgetTotalSave 프로시저 실패");
		}

		// 소모품 예산 누계 저장 (ProfitLossTotalSave로 etc_cost 확정 후 실행)
		param.put("result", 0);
		operateMapper.SuppliesBudgetSave(param);
		result = (int) param.get("result");
		if (result != 1) {
			throw new RuntimeException("❌ SuppliesBudgetSave 프로시저 실패");
		}

		return 1; // ✅ 전체 성공
	}

	// 배치 -> 손익표 저장 + 합계/비율 반영
	@Transactional(rollbackFor = Exception.class) // ✅ 전체 작업 트랜잭션
	public int processProfitLossV3(Map<String, Object> param) {

		int result = 0;

		// ② 손익표 저장
		if (headOfficeMapper.ProfitLossTableSave(param) <= 0) {
			throw new RuntimeException("❌ ProfitLossTableSave 실패");
		}

		// ③ 손익표 합계 + 비율 저장 프로시저 호출
		param.put("result", 0); // OUT 값 초기화
		headOfficeMapper.ProfitLossTotalSave(param);

		// OUT 값 확인
		result = (int) param.get("result");
		if (result != 1) {
			throw new RuntimeException("❌ ProfitLossTotalSave 프로시저 실패");
		}

		// 예산 저장 프로시저 호출
		param.put("result", 0);
		operateMapper.BudgetTotalSave(param);
		result = (int) param.get("result");
		if (result != 1) {
			throw new RuntimeException("❌ BudgetTotalSave 프로시저 실패");
		}

		// 소모품 예산 누계 저장 (ProfitLossTotalSave로 etc_cost 확정 후 실행)
		param.put("result", 0);
		operateMapper.SuppliesBudgetSave(param);
		result = (int) param.get("result");
		if (result != 1) {
			throw new RuntimeException("❌ SuppliesBudgetSave 프로시저 실패");
		}

		return 1; // ✅ 전체 성공
	}

	// ProfitLossTotalSave + 소모품 예산 누계 저장 (etc_cost 확정 후 순서대로 실행)
	public void callProfitLossTotalSave(Map<String, Object> param) {
		param.put("result", 0);
		headOfficeMapper.ProfitLossTotalSave(param);
		int result = (int) param.get("result");
		if (result != 1) {
			throw new RuntimeException("❌ ProfitLossTotalSave 프로시저 실패");
		}

		// 소모품 예산 누계 갱신 (ProfitLossTotalSave로 etc_cost 확정 후 실행)
		param.put("result", 0);
		operateMapper.SuppliesBudgetSave(param);
		result = (int) param.get("result");
		if (result != 1) {
			throw new RuntimeException("❌ SuppliesBudgetSave 프로시저 실패");
		}
	}

	// 현장 -> 집계표 -> 영수증 매장 확인 조회
	public List<Map<String, Object>> AccountMappingList(String account_id) {
		List<Map<String, Object>> resultList = new ArrayList<>();
		resultList = accountMapper.AccountMappingList(account_id);
		return resultList;
	}

	// 현장 -> 집계표 -> 매입집계 저장
	public int AccountPurchaseSave(Map<String, Object> paramMap) {
		int iResult = 0;
		Map<String, Object> oldReceiptImages = findAccountPurchaseReceiptImages(paramMap);
		iResult = accountMapper.AccountPurchaseSave(paramMap);
		if (iResult > 0) {
			deleteReplacedReceiptImage(
					asText(oldReceiptImages.get("receipt_image")),
					paramMap.get("receipt_image"),
					paramMap.containsKey("receipt_image"));
			deleteReplacedReceiptImage(
					asText(oldReceiptImages.get("receipt_image2")),
					paramMap.get("receipt_image2"),
					paramMap.containsKey("receipt_image2"));
			deleteReplacedReceiptImage(
					asText(oldReceiptImages.get("receipt_image3")),
					paramMap.get("receipt_image3"),
					paramMap.containsKey("receipt_image3"));
			try {
				Map<String, Object> historyParam = new HashMap<>(paramMap);
				// 상단 저장 이력은 detail 없이도 남기되, tax/item 타입은 제외한다.
				historyParam.remove("taxType");
				historyParam.remove("itemType");
				historyParam.remove("taxtype");
				historyParam.remove("itemtype");
				historyParam.put("savetype", 1);
				accountMapper.AccountPurchaseHistorySave(historyParam);
			} catch (Exception ignored) {
			}
		}
		return iResult;
	}

	// 현장 -> 집계표 -> 매입집계 상세 저장
	public int AccountPurchaseDetailSave(Map<String, Object> paramMap) {
		int iResult = 0;
		iResult = accountMapper.AccountPurchaseDetailSave(paramMap);
		if (iResult > 0) {
			try {
				// detail paramMap에 total/saleDate가 없을 수 있으므로 DB에서 조회해 보완
				Map<String, Object> historyParam = new java.util.HashMap<>(paramMap);
				if (historyParam.get("total") == null || historyParam.get("saleDate") == null) {
					Map<String, Object> master = accountMapper.AccountPurchaseTallyTotalBySaleId(paramMap);
					if (master != null) {
						if (historyParam.get("total") == null)
							historyParam.put("total", master.get("total"));
						if (historyParam.get("saleDate") == null)
							historyParam.put("saleDate", master.get("saleDate"));
						if (historyParam.get("account_id") == null)
							historyParam.put("account_id", master.get("account_id"));
					}
				}
				normalizeHistoryTypeFields(historyParam);
				historyParam.put("savetype", 2);
				accountMapper.AccountPurchaseHistorySave(historyParam);
			} catch (Exception ignored) {
			}
		}
		return iResult;
	}

	// 회계 -> 거래처 자료 입력 삭제
	public int AccountPurchaseTallyDelete(Map<String, Object> paramMap) {

		int result = 0;

		paramMap.put("result", 0); // OUT 값 초기화
		accountMapper.AccountPurchaseTallyDelete(paramMap);
		// OUT 값 확인
		result = (int) paramMap.get("result");
		if (result != 1) {
			throw new RuntimeException("❌ sp_sync_corp_card_to_tally_sheet_one_day_v2 프로시저 실패");
		}

		return result;
	}

	// 회계 -> 거래처 자료 입력 상세 삭제
	public int AccountPurchaseTallyDetailDelete(Map<String, Object> paramMap) {
		return accountMapper.AccountPurchaseTallyDetailDelete(paramMap);
	}

	// 회계 -> 매입마감 sale_id 기준 기존 결제일자 조회
	public Map<String, Object> AccountPurchaseTallyTotalBySaleId(Map<String, Object> paramMap) {
		return accountMapper.AccountPurchaseTallyTotalBySaleId(paramMap);
	}

	// 회계 -> 본사 법인카드 sale_id 기준 기존 결제일자 조회
	public String HeadOfficeCorporateCardPaymentDtBySaleId(Map<String, Object> paramMap) {
		return accountMapper.HeadOfficeCorporateCardPaymentDtBySaleId(paramMap);
	}

	// 회계 -> 매입 -> 매입마감 조회
	public List<Map<String, Object>> AccountPurchaseTallyList(Map<String, Object> paramMap) {
		List<Map<String, Object>> resultList = new ArrayList<>();
		
		normalizeZeroToEmpty(paramMap, "account_id", "type", "year", "month", "payType", "start_dt", "end_dt");
		resultList = accountMapper.AccountPurchaseTallyList(paramMap);
		return resultList;
	}

	// 회계 -> 매입집계(TallyTab) 조회
	public List<Map<String, Object>> AccountPurchaseTallyForTallyTab(Map<String, Object> paramMap) {
		List<Map<String, Object>> resultList = new ArrayList<>();
		normalizeZeroToEmpty(paramMap, "account_id", "type", "year", "month", "payType");
		resultList = accountMapper.AccountPurchaseTallyForTallyTab(paramMap);
		return resultList;
	}

	// 회계 -> 매입 -> 매입집계(임시) 조회
	public List<Map<String, Object>> AccountPurchaseDetailList_tmp(Map<String, Object> paramMap) {
		List<Map<String, Object>> resultList = new ArrayList<>();
		resultList = accountMapper.AccountPurchaseDetailList_tmp(paramMap);
		return resultList;
	}

	// 회계 -> 개인구매 관리 -> 개인구매 조회
	public List<Map<String, Object>> AccountPersonPurchaseTallyList(Map<String, Object> paramMap) {
		List<Map<String, Object>> resultList = new ArrayList<>();
		resultList = accountMapper.AccountPersonPurchaseTallyList(paramMap);
		return resultList;
	}

	// 회계 -> 개인구매 관리 -> 개인구매 상세 조회
	public List<Map<String, Object>> AccountPersonPurchaseDetailList(Map<String, Object> paramMap) {
		List<Map<String, Object>> resultList = new ArrayList<>();
		resultList = accountMapper.AccountPersonPurchaseDetailList(paramMap);
		return resultList;
	}

	// 회계 -> 개인구매 영수증 마감 자료 전체 조회 (account_id 무관)
	public List<Map<String, Object>> AccountPersonPurchaseTallyListAll(Map<String, Object> paramMap) {
		List<Map<String, Object>> resultList = new ArrayList<>();
		resultList = accountMapper.AccountPersonPurchaseTallyListAll(paramMap);
		return resultList;
	}

	// 집계표 -> 결제 리스트 조회
	public List<Map<String, Object>> AccountPurchaseTallyPaymentList(Map<String, Object> paramMap) {
		List<Map<String, Object>> resultList = new ArrayList<>();
		resultList = accountMapper.AccountPurchaseTallyPaymentList(paramMap);
		return resultList;
	}

	// 회계 -> 매입 -> 매입집계 조회
	public List<Map<String, Object>> AccountPurchaseDetailList(Map<String, Object> paramMap) {
		List<Map<String, Object>> resultList = new ArrayList<>();
		resultList = accountMapper.AccountPurchaseDetailList(paramMap);
		return resultList;
	}

	// 회계 -> 본사 법인카드 목록 조회
	public List<Map<String, Object>> HeadOfficeCorporateCardList(Map<String, Object> paramMap) {
		List<Map<String, Object>> resultList = new ArrayList<>();
		resultList = accountMapper.HeadOfficeCorporateCardList(paramMap);
		return resultList;
	}

	// 회계 -> 본사 법인카드 결제내역 조회
	public List<Map<String, Object>> HeadOfficeCorporateCardPaymentList(Map<String, Object> paramMap) {
		List<Map<String, Object>> resultList = new ArrayList<>();
		resultList = accountMapper.HeadOfficeCorporateCardPaymentList(paramMap);
		return resultList;
	}

	// 회계 -> 본사 법인카드 결제내역 전체 조회 (account_id 무관)
	public List<Map<String, Object>> HeadOfficeCorporateCardPaymentListAll(Map<String, Object> paramMap) {
		List<Map<String, Object>> resultList = new ArrayList<>();
		resultList = accountMapper.HeadOfficeCorporateCardPaymentListAll(paramMap);
		return resultList;
	}

	// 회계 -> 본사 법인카드 결제 상세내역 조회
	public List<Map<String, Object>> HeadOfficeCorporateCardPaymentDetailList(Map<String, Object> paramMap) {
		List<Map<String, Object>> resultList = new ArrayList<>();
		resultList = accountMapper.HeadOfficeCorporateCardPaymentDetailList(paramMap);
		return resultList;
	}

	// 회계 -> 본사 법인카드 저장
	public int HeadOfficeCorporateCardSave(Map<String, Object> paramMap) {
		int iResult = 0;
		iResult = accountMapper.HeadOfficeCorporateCardSave(paramMap);
		return iResult;
	}

	// 회계 -> 본사 법인카드 결제내역 저장
	public int HeadOfficeCorporateCardPaymentSave(Map<String, Object> paramMap) {
		int iResult = 0;
		String oldReceiptImage = findHeadOfficeCorporateReceiptImage(paramMap);
		iResult = accountMapper.HeadOfficeCorporateCardPaymentSave(paramMap);
		if (iResult > 0) {
			deleteReplacedReceiptImage(oldReceiptImage, paramMap.get("receipt_image"), paramMap.containsKey("receipt_image"));
		}
		return iResult;
	}

	// 회계 -> 현장 법인카드 결제내역 삭제
	public int HeadOfficeCorporateCardPaymentDelete(Map<String, Object> paramMap) {
		
		int result = 0;

		paramMap.put("result", 0); // OUT 값 초기화
		accountMapper.HeadOfficeCorporateCardPaymentDelete(paramMap);
		// OUT 값 확인
		result = (int) paramMap.get("result");
		if (result != 1) {
			throw new RuntimeException("❌ sp_sync_headoffice_corp_card_payment_delete 프로시저 실패");
		}
		
		return result;
	}
	
	// 회계 -> 본사 법인카드 결제내역 상세 삭제
	public int HeadOfficeCorporateCardPaymentDetailDelete(Map<String, Object> paramMap) {
		return accountMapper.HeadOfficeCorporateCardPaymentDetailDelete(paramMap);
	}
	
	// 회계 -> 본사 법인카드 상세내역 저장
	public int HeadOfficeCorporateCardPaymentDetailLSave(Map<String, Object> paramMap) {
		int iResult = 0;
		iResult = accountMapper.HeadOfficeCorporateCardPaymentDetailLSave(paramMap);
		return iResult;
	}

	// 회계 -> 현장 법인카드 목록 조회
	public List<Map<String, Object>> AccountCorporateCardList(Map<String, Object> paramMap) {
		List<Map<String, Object>> resultList = new ArrayList<>();
		resultList = accountMapper.AccountCorporateCardList(paramMap);
		return resultList;
	}

	// 회계 -> 현장 법인카드 결제내역 조회
	public List<Map<String, Object>> AccountCorporateCardPaymentList(Map<String, Object> paramMap) {
		List<Map<String, Object>> resultList = new ArrayList<>();
		resultList = accountMapper.AccountCorporateCardPaymentList(paramMap);
		return resultList;
	}

	// 회계 -> 현장 법인카드 결제내역 전체 조회 (account_id 무관)
	public List<Map<String, Object>> AccountCorporateCardPaymentListAll(Map<String, Object> paramMap) {
		List<Map<String, Object>> resultList = new ArrayList<>();
		resultList = accountMapper.AccountCorporateCardPaymentListAll(paramMap);
		return resultList;
	}

	// 회계 -> 현장 법인카드 결제 상세내역 조회
	public List<Map<String, Object>> AccountCorporateCardPaymentDetailList(Map<String, Object> paramMap) {
		List<Map<String, Object>> resultList = new ArrayList<>();
		resultList = accountMapper.AccountCorporateCardPaymentDetailList(paramMap);
		return resultList;
	}

	// 회계 -> 현장 법인카드 저장
	public int AccountCorporateCardSave(Map<String, Object> paramMap) {
		int iResult = 0;
		iResult = accountMapper.AccountCorporateCardSave(paramMap);
		return iResult;
	}

	// 회계 -> 현장 법인카드 결제내역 저장
	public int AccountCorporateCardPaymentSave(Map<String, Object> paramMap) {
		int iResult = 0;
		String oldReceiptImage = findAccountCorporateReceiptImage(paramMap);
		iResult = accountMapper.AccountCorporateCardPaymentSave(paramMap);
		if (iResult > 0) {
			deleteReplacedReceiptImage(oldReceiptImage, paramMap.get("receipt_image"), true);
		}
		return iResult;
	}
	
	// 회계 -> 현장 법인카드 결제내역 삭제
	public int AccountCorporateCardPaymentDelete(Map<String, Object> paramMap) {
		
		int result = 0;

		paramMap.put("result", 0); // OUT 값 초기화
		accountMapper.AccountCorporateCardPaymentDelete(paramMap);
		// OUT 값 확인
		result = (int) paramMap.get("result");
		if (result != 1) {
			throw new RuntimeException("❌ sp_sync_account_corp_card_payment_delete 프로시저 실패");
		}
		
		return result;
	}
	
	// 회계 -> 현장 법인카드 결제내역 상세 삭제
	public int AccountCorporateCardPaymentDetailDelete(Map<String, Object> paramMap) {
		return accountMapper.AccountCorporateCardPaymentDetailDelete(paramMap);
	}

	// 회계 -> 본사 법인카드 상세내역 저장
	public int AccountCorporateCardPaymentDetailLSave(Map<String, Object> paramMap) {
		int iResult = 0;
		iResult = accountMapper.AccountCorporateCardPaymentDetailLSave(paramMap);
		return iResult;
	}

	// 회계 -> 현장 법인카드 결제내역을 매입집계(type=1000)로 동기화
	public int AccountCorporateCardPaymentToPurchaseTallySave(Map<String, Object> paramMap) {
		String saleId = paramMap.get("sale_id") == null ? "" : String.valueOf(paramMap.get("sale_id")).trim();
		String accountId = paramMap.get("account_id") == null ? "" : String.valueOf(paramMap.get("account_id")).trim();
		String paymentDt = paramMap.get("payment_dt") == null ? "" : String.valueOf(paramMap.get("payment_dt")).trim();

		if (saleId.isEmpty() || accountId.isEmpty() || paymentDt.isEmpty() || "null".equalsIgnoreCase(paymentDt)) {
			return 0;
		}

		return accountMapper.AccountCorporateCardPaymentToPurchaseTallySave(paramMap);
	}

	// 회계 -> 현장 법인카드 집계표 적용, 손익표, 예산도 함께 적용해야 함.
	@Transactional(rollbackFor = Exception.class) // ✅ 전체 작업 트랜잭션
	public int TallySheetCorporateCardPaymentSave(Map<String, Object> paramMap) {

		int result = 0;

		paramMap.put("result", 0); // OUT 값 초기화
		accountMapper.TallySheetCorporateCardPaymentSave(paramMap);
		// OUT 값 확인
		result = (int) paramMap.get("result");
		if (result != 1) {
			throw new RuntimeException("❌ sp_sync_corp_card_to_tally_sheet_one_day 프로시저 실패");
		}

		// ③ 손익표 합계 + 비율 저장 프로시저 호출
		paramMap.put("result", 0); // OUT 값 초기화
		headOfficeMapper.ProfitLossTotalSave(paramMap);

		// OUT 값 확인
		result = (int) paramMap.get("result");
		if (result != 1) {
			throw new RuntimeException("❌ ProfitLossTotalSave 프로시저 실패");
		}

		// 예산 저장 프로시저 호출
		paramMap.put("result", 0); // OUT 값 초기화
		operateMapper.BudgetTotalSave(paramMap);

		// OUT 값 확인
		result = (int) paramMap.get("result");
		if (result != 1) {
			throw new RuntimeException("❌ BudgetTotalSave 프로시저 실패");
		}

		// 소모품 예산 누계 저장 (ProfitLossTotalSave로 etc_cost 확정 후 실행)
		paramMap.put("result", 0);
		operateMapper.SuppliesBudgetSave(paramMap);
		result = (int) paramMap.get("result");
		if (result != 1) {
			throw new RuntimeException("❌ SuppliesBudgetSave 프로시저 실패");
		}

		return result;
	}

	// 회계 -> 본사 법인카드 집계표 적용, 손익표, 예산도 함께 적용해야 함.
	@Transactional(rollbackFor = Exception.class) // ✅ 전체 작업 트랜잭션
	public int TallySheetCorporateCardPaymentSaveV2(Map<String, Object> paramMap) {

		int result = 0;

		paramMap.put("result", 0); // OUT 값 초기화
		accountMapper.TallySheetCorporateCardPaymentSaveV2(paramMap);
		// OUT 값 확인
		result = (int) paramMap.get("result");
		if (result != 1) {
			throw new RuntimeException("❌ sp_sync_corp_card_to_tally_sheet_one_day_v2 프로시저 실패");
		}

		// ③ 손익표 합계 + 비율 저장 프로시저 호출
		paramMap.put("result", 0); // OUT 값 초기화
		headOfficeMapper.ProfitLossTotalSave(paramMap);

		// OUT 값 확인
		result = (int) paramMap.get("result");
		if (result != 1) {
			throw new RuntimeException("❌ ProfitLossTotalSave 프로시저 실패");
		}

		// 예산 저장 프로시저 호출
		paramMap.put("result", 0); // OUT 값 초기화
		operateMapper.BudgetTotalSave(paramMap);

		// OUT 값 확인
		result = (int) paramMap.get("result");
		if (result != 1) {
			throw new RuntimeException("❌ BudgetTotalSave 프로시저 실패");
		}

		// 소모품 예산 누계 저장 (ProfitLossTotalSave로 etc_cost 확정 후 실행)
		paramMap.put("result", 0);
		operateMapper.SuppliesBudgetSave(paramMap);
		result = (int) paramMap.get("result");
		if (result != 1) {
			throw new RuntimeException("❌ SuppliesBudgetSave 프로시저 실패");
		}

		return result;
	}

	// 집계표 -> 집계표 적용, 손익표, 예산도 함께 적용해야 함.
	@Transactional(rollbackFor = Exception.class) // ✅ 전체 작업 트랜잭션
	public int TallySheetPaymentSave(Map<String, Object> paramMap) {

		int result = 0;

		// saleDate가 없으면 프로시저가 실패하므로 조기 리턴
		Object saleDateObj = paramMap.get("saleDate");
		String saleDate = saleDateObj != null ? String.valueOf(saleDateObj).trim() : "";
		if (saleDate.isEmpty() || "null".equalsIgnoreCase(saleDate)) {
			System.err.println("[TallySheetPaymentSave] saleDate가 없어 스킵: " + paramMap.get("sale_id"));
			return 0;
		}
		
		paramMap.put("result", 0); // OUT 값 초기화
		System.out.println("TallySheetPaymentSave + " + paramMap);
		accountMapper.TallySheetPaymentSave(paramMap);
		// OUT 값 확인
		result = (int) paramMap.get("result");
		if (result != 1) {
			throw new RuntimeException("❌ sp_sync_tally_sheet_one_day 프로시저 실패");
		}

		if (result > 0) {
			// ③ 손익표 합계 + 비율 저장 프로시저 호출
			paramMap.put("result", 0); // OUT 값 초기화
			System.out.println("ProfitLossTotalSave + " + paramMap);
			headOfficeMapper.ProfitLossTotalSave(paramMap);
		}

		// OUT 값 확인
		result = (int) paramMap.get("result");
		if (result != 1) {
			throw new RuntimeException("❌ ProfitLossTotalSave 프로시저 실패");
		}

		if (result > 0) {
			// 예산 저장 프로시저 호출
			paramMap.put("result", 0); // OUT 값 초기화
			System.out.println("BudgetTotalSave + " + paramMap);
			operateMapper.BudgetTotalSave(paramMap);
		}

		// OUT 값 확인
		result = (int) paramMap.get("result");
		if (result != 1) {
			throw new RuntimeException("❌ BudgetTotalSave 프로시저 실패");
		}

		// 소모품 예산 누계 저장 (ProfitLossTotalSave로 etc_cost 확정 후 실행)
		paramMap.put("result", 0);
		operateMapper.SuppliesBudgetSave(paramMap);
		result = (int) paramMap.get("result");
		if (result != 1) {
			throw new RuntimeException("❌ SuppliesBudgetSave 프로시저 실패");
		}

		return result;
	}

	// 집계표 -> 집계표 내역 삭제.
	public int TallySheetPaymentDelete(Map<String, Object> paramMap) {
		int iResult = 0;
		iResult = accountMapper.TallySheetPaymentDelete(paramMap);
		return iResult;
	}

	// 인사 -> 직원파출 매핑 수정 시 기존 출근기록 수정
	public int AccountMemberRecordUpdateByOldKey(Map<String, Object> paramMap) {
		int iResult = 0;
		iResult = accountMapper.AccountMemberRecordUpdateByOldKey(paramMap);
		return iResult;
	}

	// 인사 -> 직원파출 매핑 저장
	public int AccountMemberDispatchMappingSave(Map<String, Object> paramMap) {
		int iResult = 0;
		iResult = accountMapper.AccountMemberDispatchMappingSave(paramMap);
		return iResult;
	}

	// 인사 -> 직원파출 매핑 단건 조회
	public Map<String, Object> AccountMemberDispatchMappingOne(Map<String, Object> paramMap) {
		Map<String, Object> result = null;
		result = accountMapper.AccountMemberDispatchMappingOne(paramMap);
		return result;
	}

	// 인사 -> 직원파출 매핑 조회
	public List<Map<String, Object>> AccountMemberDispatchMappingList(Map<String, Object> paramMap) {
		List<Map<String, Object>> resultList = new ArrayList<>();
		resultList = accountMapper.AccountMemberDispatchMappingList(paramMap);
		return resultList;
	}

	// 영업 -> 매출 -> 매출마감/미수잔액 -> 입금내역 수정
	public int AccountDepositHistoryRecalc(Map<String, Object> paramMap) {
		int iResult = 0;
		iResult = accountMapper.AccountDepositHistoryRecalc(paramMap);
		return iResult;
	}

	// 영업 -> 매출 -> 매출마감/미수잔액 -> 입금내역 조회
	public int AccountDepositEmptyUse(Map<String, Object> paramMap) {
		int iResult = 0;
		iResult = accountMapper.AccountDepositEmptyUse(paramMap);
		return iResult;
	}

	// 회계 -> 매입(본사용) 조회
	public List<Map<String, Object>> AccountPurchaseTallyV2List(Map<String, Object> paramMap) {
		List<Map<String, Object>> resultList = new ArrayList<>();
		normalizeZeroToEmpty(paramMap, "account_id", "type", "year", "month", "payType");
		resultList = accountMapper.AccountPurchaseTallyV2List(paramMap);
		return resultList;
	}

	// 회계 -> 매입(본사용) 저장
	public int AccountPurchaseTallyV2Save(Map<String, Object> paramMap) {
		int iResult = 0;
		iResult = accountMapper.AccountPurchaseTallyV2Save(paramMap);
		return iResult;
	}

	// 긴급인력 파출 회원 정보 삭제
	public int AccountDispatchMemberDelete(Map<String, Object> paramMap) {
		int iResult = 0;
		iResult = accountMapper.AccountDispatchMemberDelete(paramMap);
		return iResult;
	}

	// 긴급인력 파출 출근기록 삭제
	public int AccountDispatchRecordDelete(Map<String, Object> paramMap) {
		int iResult = 0;
		iResult = accountMapper.AccountDispatchRecordDelete(paramMap);
		return iResult;
	}

	// 회계 -> 매입집계 삭제
	public int AccountPurchaseTallyV2Delete(Map<String, Object> paramMap) {
		int iResult = 0;
		iResult = accountMapper.AccountPurchaseTallyV2Delete(paramMap);
		return iResult;
	}

	// 회계 -> 월 마감 수정권한 조회
	public Map<String, Object> MonthLockOverrideGet(Map<String, Object> paramMap) {
		return accountMapper.MonthLockOverrideGet(paramMap);
	}

	// 회계 -> 월 마감 수정권한 저장/수정
	public int MonthLockOverrideSave(Map<String, Object> paramMap) {
		return accountMapper.MonthLockOverrideSave(paramMap);
	}

	// 현장 -> 구입요청 -> 사용자 정보 조회 (거래처명 + 1차결재자)
	public Map<String, Object> PurchaseRequestUserInfo(Map<String, Object> paramMap) {
		return accountMapper.PurchaseRequestUserInfo(paramMap);
	}

	// 현장 -> 구입 업장관리 -> 관리자 목록
	public List<Map<String, Object>> PurchaseManagerList(Map<String, Object> paramMap) {
		return accountMapper.PurchaseManagerList(paramMap);
	}

	// 현장 -> 구입 업장관리 -> 전체 거래처 목록
	public List<Map<String, Object>> PurchaseAccountList(Map<String, Object> paramMap) {
		return accountMapper.PurchaseAccountList(paramMap);
	}

	// 현장 -> 구입 업장관리 -> 관리자별 매핑 거래처 목록
	public List<Map<String, Object>> PurchaseManagerAccountMapList(Map<String, Object> paramMap) {
		return accountMapper.PurchaseManagerAccountMapList(paramMap);
	}

	// 현장 -> 구입 업장관리 -> 매핑 저장 (기존 삭제 후 재등록)
	@Transactional
	public int PurchaseManagerAccountMapSave(String managerUserId, List<Map<String, Object>> list) {
		Map<String, Object> delParam = new HashMap<>();
		delParam.put("user_id", managerUserId);
		accountMapper.PurchaseManagerAccountMapDelete(delParam);

		int count = 0;
		for (Map<String, Object> row : list) {
			if (row == null) continue;
			row.put("user_id", managerUserId);
			count += accountMapper.PurchaseManagerAccountMapSave(row);
		}
		return count;
	}

	// ===================== 출, 퇴근 기록 =====================

	// 출, 퇴근 기록 -> 사업장 기준 좌표 조회
	public Map<String, Object> AccountCoordinateInfo(Map<String, Object> paramMap) {
		return accountMapper.AccountCoordinateInfo(paramMap);
	}

	// 출, 퇴근 기록 -> 등록기기 정보 조회
	public Map<String, Object> CommuteDeviceInfo(Map<String, Object> paramMap) {
		return accountMapper.CommuteDeviceInfo(paramMap);
	}

	// ✅ 출, 퇴근 기록 -> 등록기기 등록/변경 요청 - tb_member_device에 PK(유니크 제약)가 없으므로
	//    기존 행이 있는지 먼저 조회해서 있으면 UPDATE, 없으면 INSERT 한다.
	public int CommuteDeviceRequest(Map<String, Object> paramMap) {
		Map<String, Object> existing = accountMapper.CommuteDeviceInfo(paramMap);
		if (existing == null) {
			return accountMapper.CommuteDeviceRequestInsert(paramMap);
		}
		return accountMapper.CommuteDeviceRequestUpdatePending(paramMap);
	}

	// 출, 퇴근 기록 -> 등록기기 요청 승인
	public int CommuteDeviceApprove(Map<String, Object> paramMap) {
		return accountMapper.CommuteDeviceApprove(paramMap);
	}

	// 출, 퇴근 기록 -> 등록기기 요청 반려
	public int CommuteDeviceReject(Map<String, Object> paramMap) {
		return accountMapper.CommuteDeviceReject(paramMap);
	}

	// 출, 퇴근 기록 -> 승인 대기중인 등록기기 요청 목록
	public List<Map<String, Object>> CommuteDeviceRequestList(Map<String, Object> paramMap) {
		return accountMapper.CommuteDeviceRequestList(paramMap);
	}

	// 출, 퇴근 기록 -> 승인 완료된 등록기기(사람+기기) 전체 목록
	public List<Map<String, Object>> CommuteDeviceList(Map<String, Object> paramMap) {
		return accountMapper.CommuteDeviceList(paramMap);
	}

	// 출, 퇴근 기록 -> 출퇴근 기록 저장(upsert)
	public int CommuteSave(Map<String, Object> paramMap) {
		return accountMapper.CommuteSave(paramMap);
	}

	// 출, 퇴근 기록 -> 대리출근 방지: 오늘 이 기기로 이미 찍힌 다른 사람(user_name+phone_last4) 이력 조회
	public List<Map<String, Object>> SelectCommuteIdentitiesByDeviceToken(Map<String, Object> paramMap) {
		return accountMapper.SelectCommuteIdentitiesByDeviceToken(paramMap);
	}

	// 출, 퇴근 기록 -> 이 device_token이 이미 다른 사람에게 승인돼 있는지 조회 (대리출근 의심 탐지)
	public Map<String, Object> SelectApprovedDeviceOwner(Map<String, Object> paramMap) {
		return accountMapper.SelectApprovedDeviceOwner(paramMap);
	}

	// 출, 퇴근 기록 -> 같은 사람(account_id+user_name)이 이미 다른 phone_last4로 승인받은 행 조회 (이중 승인/동명이인 확인용)
	public Map<String, Object> SelectApprovedDeviceBySameName(Map<String, Object> paramMap) {
		return accountMapper.SelectApprovedDeviceBySameName(paramMap);
	}

	// 출, 퇴근 기록 -> 위에서 찾은 예전 행의 승인 해제 (이중 승인 정리)
	public int ClearDeviceApproval(Map<String, Object> paramMap) {
		return accountMapper.ClearDeviceApproval(paramMap);
	}

	// 출, 퇴근 기록 -> 기기 중복 사용(대리출근 의심) 이력 저장
	public int InsertMemberDeviceConflictLog(Map<String, Object> paramMap) {
		return accountMapper.InsertMemberDeviceConflictLog(paramMap);
	}

	// 출, 퇴근 기록 -> 오늘 출퇴근 진행상태 조회
	public Map<String, Object> CommuteTodayStatus(Map<String, Object> paramMap) {
		return accountMapper.CommuteTodayStatus(paramMap);
	}

	// 출, 퇴근 기록 -> 출퇴근 기록 목록 조회
	public List<Map<String, Object>> CommuteRecordList(Map<String, Object> paramMap) {
		return accountMapper.CommuteRecordList(paramMap);
	}

	// ===================== 웰스토리 SW-FD 주문API 연동 (로직) =====================
	//
	// 전체 흐름 요약
	//   WelstorySyncScheduler(매일 17시 KST)
	//     -> WelstoryPurchaseSync()                         제휴사 2곳 순회
	//       -> welstorySyncOneClient(client)                 client 하나: 토큰발급 -> 사업장(soldTo) 목록조회
	//         -> welstorySyncReceiveDetail(soldTo 하나)       그날 입고내역 전체 조회
	//           -> AccountPurchaseSave(master 1행)            "하루당 1 master" = tb_account_purchase_tally 1 row
	//           -> AccountPurchaseDetailSave(detail N행)      그날 전체 품목(주문 여러 건 섞여있어도) = tb_account_purchase_tally_detail N rows
	//
	// account_id는 tb_account.welstory_soldto 컬럼(soldTo 코드 매핑, 수동 등록해둔 값)으로 조회하며,
	// 매핑이 없는 soldTo(신규 사업장 등)는 저장하지 않고 경고 로그만 남기고 건너뛴다.

	// 진입점. 스케줄러가 이 메서드 하나만 호출한다.
	// client1(주식회사 더채움), client2(더채움 위탁급식) 순서로 각각 동기화하고, 저장(=신규/갱신)된 master(soldTo/날짜) 건수 합계를 반환
	// 오늘자 입고내역 동기화 (스케줄러가 매일 부르는 기본 진입점)
	// LocalDate.now()가 아니라 Asia/Seoul 기준으로 날짜를 뽑는다 — 서버(UTC)의 LocalDate.now()를 쓰면
	// 자정 근처(KST 00~09시, UTC로는 전날)에 날짜가 하루 어긋날 수 있다 (guid와 동일한 이유).
	public int WelstoryPurchaseSync() {
		String today = LocalDate.now(java.time.ZoneId.of("Asia/Seoul")).format(DateTimeFormatter.ofPattern("yyyyMMdd"));
		return WelstoryPurchaseSync(today);
	}

	// 특정 날짜(reqDeliveryDate, "YYYYMMDD") 입고내역 동기화.
	// 스케줄러가 하루 놓쳤거나(서버 재시작 등) 과거 특정일 데이터를 다시 받아야 할 때
	// 이 오버로드를 날짜만 바꿔서 그대로 재사용하면 된다 (수동 백필용).
	public int WelstoryPurchaseSync(String yyyyMMdd) {
		int total = 0;
		total += welstorySyncOneClient(welstoryClient1Id, welstoryClient1Secret, yyyyMMdd);
		total += welstorySyncOneClient(welstoryClient2Id, welstoryClient2Secret, yyyyMMdd);
		return total;
	}

	// 제휴사(client) 1곳 처리:
	//   1) 토큰 발급
	//   2) payer-rep-soldto로 이 client 소속 사업장(soldTo) 전체 목록을 받아옴
	//      -> 사업장 목록을 하드코딩하지 않고 매번 API로 받아오므로, 웰스토리 쪽에 사업장이 추가/변경돼도 코드 수정 불필요
	//   3) 사업장마다 tb_account 매핑을 확인해서 있는 것만, reqDeliveryDate 기준 입고내역 동기화
	private int welstorySyncOneClient(String clientId, String clientSecret, String reqDeliveryDate) {
		// application-secret*.properties에서 client_id/secret을 못 읽어온 경우.
		// (0건만 찍히고 다른 로그가 전혀 없다면 대부분 이 케이스 — 이 서버에서 실제 로딩되는 secret 파일에
		//  welstory.client1.id/secret, welstory.client2.id/secret 값이 비어있다는 뜻)
		if (clientId == null || clientId.isBlank() || clientSecret == null || clientSecret.isBlank()) {
			log.warn("[WelstorySync] welstory.clientN.id/secret 프로퍼티가 비어있어 스킵함 (clientId blank={})",
					clientId == null || clientId.isBlank());
			return 0;
		}
		int saved = 0;
		try {
			// 토큰은 이 client 동기화 1회 실행 동안만 재사용(30일 유효하지만 매번 재발급해도 문제는 없음, 단순화를 위해 매 실행마다 새로 받음)
			String accessToken = welstoryIssueToken(clientId, clientSecret);

			for (Map<String, Object> soldToRow : welstoryListSoldTo(accessToken)) {
				String soldTo = String.valueOf(soldToRow.get("soldTo"));
				// tb_account.welstory_soldto = soldTo 로 등록해둔 거래처(account_id)를 찾는다.
				// (welstory_soldto_mapping.sql로 미리 채워둔 매핑 — 신규 사업장은 아직 매핑이 없어서 여기서 걸러짐)
				String accountId = accountMapper.AccountIdByWelstorySoldTo(soldTo);
				if (accountId == null || accountId.isBlank()) {
					log.warn("[WelstorySync] tb_account 매핑 없는 soldTo 스킵: {}", soldTo);
					continue;
				}
				// buyer(구매자명) 컬럼에 쓸 payerNm은 payer-receive-detail 응답엔 없고
				// payer-rep-soldto 응답(soldToRow)에만 있어서 여기서 미리 꺼내 넘겨준다.
				String payerNm = String.valueOf(soldToRow.get("payerNm"));
				saved += welstorySyncReceiveDetail(accessToken, soldTo, accountId, payerNm, reqDeliveryDate);
			}
		} catch (Exception e) {
			// 제휴사 1곳이 실패해도(토큰 만료/네트워크 오류 등) 다른 제휴사 처리는 계속되도록 여기서 막는다
			log.error("[WelstorySync] client 동기화 실패: {}", clientId, e);
		}
		return saved;
	}

	// 사업장(soldTo) 1곳의 오늘자 입고내역을 조회해서 저장한다.
	// [사용자 확정] "하루당 한 마스터"로 저장한다: 그날 이 soldTo(=account_id)에 여러 주문(clientOrd)이 있어도
	// clientOrd별로 쪼개지 않고 전부 하나의 master 1행(tb_account_purchase_tally) 밑에
	// detail N행(tb_account_purchase_tally_detail, 그날의 전체 품목 라인)으로 저장한다.
	private int welstorySyncReceiveDetail(String accessToken, String soldTo, String accountId, String payerNm,
			String reqDeliveryDate) {
		List<Map<String, Object>> lines = welstoryReceiveDetail(accessToken, soldTo, reqDeliveryDate);
		if (lines.isEmpty()) {
			return 0;
		}

		// ---- master(하루 단위) 합계 계산 ----
		// 기존 프론트(AccountPurchaseDeadlineTab.js)의 과세/면세 집계 로직과 동일한 기준을 따름:
		//   total   = 모든 품목의 totAmt(품목 합계금액) 합
		//   vat     = 모든 품목의 vat(부가세) 합
		//   tax     = 과세 품목(taxCode가 'Full TAX' 또는 'CONS')의 (totAmt - vat) = 공급가액 합
		//   taxFree = 면세 품목(taxCode가 'No TAX')의 totAmt 합
		long total = 0, vat = 0, tax = 0, taxFree = 0;
		for (Map<String, Object> line : lines) {
			long amount = welstoryAsLong(line.get("totAmt"));
			long lineVat = welstoryAsLong(line.get("vat"));
			total += amount;
			vat += lineVat;
			if (welstoryIsTaxable(String.valueOf(line.get("taxCode")))) {
				tax += (amount - lineVat);
			} else {
				taxFree += amount;
			}
		}

		// ---- master row 저장: tb_account_purchase_tally (PK = sale_id) ----
		// sale_id = account_id + "_" + 입고일 -> 이 사업장의 이 날짜를 가리키는 고유키.
		// 스케줄러가 재실행돼도(같은 날짜) 항상 같은 sale_id라 ON DUPLICATE KEY UPDATE로 덮어쓰기만 되고 중복 row가 안 생긴다.
		String saleId = accountId + "_" + reqDeliveryDate;

		Map<String, Object> master = new HashMap<>();
		master.put("account_id", accountId);
		master.put("sale_id", saleId);
		master.put("type", "1"); // 삼성웰스토리 계열 고정 type (기존 "거래처 마감 자료" 화면의 type 1~4 중 1번 사용, 사용자 확정값)
		master.put("saleDate", welstoryToIsoDate(String.valueOf(lines.get(0).get("billDate")))); // 입고일(YYYYMMDD) -> "YYYY-MM-DD"
		master.put("total", total);
		master.put("discount", 0); // 웰스토리 입고내역엔 할인 개념이 없어서 항상 0
		master.put("vat", vat);
		master.put("taxFree", taxFree);
		master.put("tax", tax);
		master.put("use_name", "삼성웰스토리(주)"); // 고정값(사용자 확정) — 사업장명(soldToNm)이 아니라 공급처명 고정 표기
		master.put("buyer", payerNm); // 구매자(제휴사)명: "주식회사 더채움" 또는 "더채움(위탁급식)"
		// 나중에 이 row가 어느 soldTo에서 왔는지 추적할 수 있도록 note에 남겨둠 (별도 컬럼이 없어서)
		master.put("note", "웰스토리 API 자동연동 (soldTo=" + soldTo + ")");
		master.put("user_id", WELSTORY_SYNC_USER_ID);
		AccountPurchaseSave(master);

		// ---- detail row 저장: tb_account_purchase_tally_detail (PK = item_id + sale_id) ----
		// 품목 한 줄(clientOrd + clientOrdItem)마다 한 행씩 저장. 하루에 주문(clientOrd)이 여러 건 있을 수 있고
		// clientOrdItem 번호는 주문 안에서만 유일(주문마다 1번부터 다시 시작)하므로, item_id는 clientOrdItem을
		// 그대로 쓰지 않고 clientOrd까지 합쳐서 하루 전체에서 유일한 값이 되도록 만든다.
		for (Map<String, Object> line : lines) {
			boolean taxable = welstoryIsTaxable(String.valueOf(line.get("taxCode")));
			// taxCode='CONS'는 가이드상 "소모품"을 의미 -> itemType(상품구분)을 소모품(2)으로 매핑,
			// 그 외(Full TAX/No TAX)는 식재료(1)로 간주 (웰스토리는 식자재 발주 API라 '경관식'은 별도 신호가 없어 사용하지 않음)
			boolean isCons = "CONS".equalsIgnoreCase(String.valueOf(line.get("taxCode")));
			long amount = welstoryAsLong(line.get("totAmt"));
			long lineVat = welstoryAsLong(line.get("vat"));
			String clientOrd = String.valueOf(line.get("clientOrd"));

			// 프론트(AccountPurchaseDeadlineTab, accountPurchaseDeadlineDetailData.js normalizeDetailAmounts)가
			// 화면/상단합계용 "금액"을 amount 그대로 쓰지 않고 매번 qty*unitPrice로 재계산한다.
			// unitPrice 컬럼은 int(정수)라서, qty가 소수(예: "2.500")인 품목은 amount/qty를 반올림하는 순간
			// qty*unitPrice가 원래 amount와 1원 단위로 어긋날 수 있고, 그 오차 때문에 화면이 "값이 다르다"며
			// 상단 행을 __dirty(빨간색)로 잘못 표시하는 문제가 있었다(qty가 딱 나눠떨어지는 품목만 우연히 안 걸림).
			// -> qty*unitPrice가 항상 amount와 정확히 같아지도록(오차 발생이 원천적으로 불가능하게)
			// qty=1, unitPrice=amount로 저장한다. 실제 입고수량/단가는 note에 참고용으로 남겨둔다.
			Map<String, Object> detail = new HashMap<>();
			detail.put("item_id", welstoryDailyItemId(clientOrd, line.get("clientOrdItem")));
			detail.put("sale_id", saleId); // master와 연결되는 FK
			detail.put("name", line.get("itemName"));
			detail.put("qty", "1");
			detail.put("amount", amount); // 품목 합계금액(totAmt, 부가세 포함)
			detail.put("unitPrice", amount); // qty=1과 곱해 항상 amount와 정확히 일치하도록
			detail.put("vat", lineVat);
			detail.put("tax", taxable ? (amount - lineVat) : 0); // 공급가액(과세일 때만). 면세면 0
			detail.put("taxType", taxable ? "1" : "2"); // 기존 화면 코드값: 1=과세, 2=면세
			detail.put("itemType", isCons ? "2" : "1"); // 기존 화면 코드값: 1=식재료, 2=소모품
			// 규격(standard) + 실제 입고수량/단가 + 주문번호(clientOrd)를 참고용으로 note에 저장
			// (전용 컬럼이 없고, 여러 주문이 한 master로 합쳐져서 어느 주문 소속 품목인지 구분할 단서도 필요해서 반드시 남겨둔다)
			detail.put("note", line.get("standard") + " [수량:" + line.get("billQty") + " 단가:" + line.get("unitPrice")
					+ "] (" + clientOrd + ")");
			detail.put("user_id", WELSTORY_SYNC_USER_ID);
			AccountPurchaseDetailSave(detail);
		}
		return 1; // 이 soldTo/날짜에 대해 master 1건 저장(=처리 성공)
	}

	// detail.item_id(int) 생성: clientOrdItem은 "그 주문 안에서만" 유일해서(주문마다 1번부터 다시 시작),
	// 하루에 여러 주문(clientOrd)이 섞이면 그대로 쓸 수 없다.
	// clientOrd의 해시값을 상위 자릿수로, clientOrdItem을 하위 2자리로 붙여서 "이 날짜 전체에서 유일한" 정수를 만든다.
	// clientOrd 문자열 자체의 해시라서 몇 번을 재실행해도, 다른 주문이 추가/삭제돼도 항상 같은 값이 나옴(순서에 의존하지 않음)
	// -> ON DUPLICATE KEY UPDATE가 매번 같은 행을 정확히 다시 찾아서 안전하게 재실행(idempotent)된다.
	private int welstoryDailyItemId(String clientOrd, Object clientOrdItem) {
		int clientOrdHash = Math.abs(clientOrd.hashCode()) % 1_000_000;
		int itemNo = (int) (welstoryAsLong(clientOrdItem) % 100);
		return clientOrdHash * 100 + itemNo;
	}

	// 웰스토리 taxCode 문자열을 과세/면세 boolean으로 변환.
	// 가이드 기준: 'Full TAX'(과세), 'CONS'(소모품, 과세) -> true / 'No TAX'(면세) -> false
	private boolean welstoryIsTaxable(String taxCode) {
		return !"No TAX".equalsIgnoreCase(taxCode);
	}

	// 웰스토리 응답 필드는 전부 문자열(JSON string)로 오므로, 금액 합산 계산을 위해 안전하게 long으로 변환.
	// 파싱 실패(null, 빈 문자열, 숫자 아닌 값 등)해도 예외를 던지지 않고 0으로 처리해 전체 동기화가 죽지 않게 함.
	private long welstoryAsLong(Object v) {
		if (v == null)
			return 0L;
		try {
			return Long.parseLong(String.valueOf(v).trim());
		} catch (NumberFormatException e) {
			return 0L;
		}
	}


	// 웰스토리의 billDate("YYYYMMDD", 8자리)를 tb_account_purchase_tally.saleDate에 넣을 수 있는
	// "YYYY-MM-DD" 형식으로 변환. 길이가 8이 아니면(형식이 이상하면) 원본을 그대로 반환해서 값이 유실되지 않게 함.
	private String welstoryToIsoDate(String yyyymmdd) {
		if (yyyymmdd == null || yyyymmdd.length() != 8)
			return yyyymmdd;
		return yyyymmdd.substring(0, 4) + "-" + yyyymmdd.substring(4, 6) + "-" + yyyymmdd.substring(6, 8);
	}

	// 웰스토리 API 호출 시 http header에 실어 보내는 guid(거래 식별자) 생성.
	// 가이드 스펙: "거래일시(17자리, yyyyMMddHHmmssSSS) + seq(2자리)" = 총 19자리이며, 모든 거래를 통틀어 중복되면 안 됨.
	// 같은 밀리초 안에 연속 호출돼 타임스탬프가 겹치는 경우를 대비해 seq를 1~99 사이에서 순환시켜 뒤에 붙인다.
	//
	// LocalDateTime.now()는 서버(JVM) 로컬 시간대를 쓰는데, 배포 서버가 UTC라서 이걸 그대로 쓰면
	// guid에 KST가 아니라 UTC 시각이 박혀 웰스토리 쪽에서 "GUID가 거래시간과 오차가 큽니다(E1007)"로 거절한다.
	// 그래서 반드시 Asia/Seoul로 고정해서 시각을 뽑아야 한다.
	private String welstoryNextGuid() {
		String ts = LocalDateTime.now(java.time.ZoneId.of("Asia/Seoul"))
				.format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"));
		int seq = (welstoryGuidSeq.incrementAndGet() % 99) + 1;
		return ts + String.format("%02d", seq);
	}

	// OAuth 접근토큰 발급. "Token 발급 요청" 시트 스펙상 client_id/secret/scope/grant_type을
	// (http body가 아니라) query parameter로 실어 POST 호출해야 한다.
	// 토큰은 30일간 유효(expires_in=2592000)하지만 여기서는 매 동기화 실행마다 새로 발급받아 단순하게 처리한다.
	private String welstoryIssueToken(String clientId, String clientSecret) {
		String url = UriComponentsBuilder.fromHttpUrl(WELSTORY_BASE_URL + "/oauth/2.0/token")
				.queryParam("client_id", clientId)
				.queryParam("client_secret", clientSecret)
				.queryParam("scope", "oob") // 가이드 고정값
				.queryParam("grant_type", "client_credentials") // 가이드 고정값
				.build(false)
				.toUriString();

		ResponseEntity<Map<String, Object>> resp = restTemplate.exchange(url, HttpMethod.POST,
				new HttpEntity<>(new HttpHeaders()), new ParameterizedTypeReference<Map<String, Object>>() {
				});
		Map<String, Object> res = resp.getBody();
		if (res == null || res.get("access_token") == null) {
			// client_id/secret이 잘못됐거나(오류코드 9202 등), IP가 화이트리스트에 없으면 여기서 걸림
			throw new IllegalStateException("웰스토리 토큰 발급 실패: " + res);
		}
		return String.valueOf(res.get("access_token"));
	}

	// [제휴사별 사업장 조회] API(payer-rep-soldto) 호출.
	// 이 client(제휴사)에 등록된 사업장(soldTo) 전체 목록을 반환한다(사업장이 많지 않아 페이징 없이 pageRow=100, contYn="N" 고정 한 번만 호출).
	@SuppressWarnings("unchecked")
	private List<Map<String, Object>> welstoryListSoldTo(String accessToken) {
		Map<String, Object> dataHeader = new HashMap<>();
		dataHeader.put("pageRow", 100);
		dataHeader.put("contYn", "N"); // 최초 조회이므로 "다음 페이지 없음" 기본값
		dataHeader.put("nextKey", "");
		Map<String, Object> body = welstoryCallApi("payer-rep-soldto", accessToken, dataHeader);

		Object dataBody = body.get("dataBody");
		if (!(dataBody instanceof Map)) {
			// dataBody 자체가 없으면 응답 구조가 예상과 다르다는 뜻 -> 원인 파악을 위해 응답 전체를 남긴다
			log.warn("[WelstorySync] payer-rep-soldto 응답 형식 이상, body={}", body);
			return List.of();
		}
		Map<String, Object> db = (Map<String, Object>) dataBody;
		if (!"S0000".equals(db.get("resCd"))) {
			// receiveDetail과 동일하게, 정상(S0000)이 아니면 반드시 로그를 남겨서 원인(토큰만료/IP차단/파라미터오류 등)을 알 수 있게 함
			log.warn("[WelstorySync] payer-rep-soldto 오류 resCd={} resMsg={}", db.get("resCd"), db.get("resMsg"));
			return List.of();
		}
		Object data = db.get("data");
		return data instanceof List ? (List<Map<String, Object>>) data : List.of();
	}

	// [제휴사 사업장 입고내역 조회] API(payer-receive-detail) 호출.
	// soldTo(사업장) + reqDeliveryDate(입고일, YYYYMMDD) 1건 기준으로 그날 입고된 품목 라인 목록을 반환한다.
	@SuppressWarnings("unchecked")
	private List<Map<String, Object>> welstoryReceiveDetail(String accessToken, String soldTo,
			String reqDeliveryDate) {
		Map<String, Object> dataHeader = new HashMap<>();
		dataHeader.put("soldTo", soldTo);
		dataHeader.put("reqDeliveryDate", reqDeliveryDate);
		Map<String, Object> body = welstoryCallApi("payer-receive-detail", accessToken, dataHeader);

		Object dataBody = body.get("dataBody");
		if (!(dataBody instanceof Map))
			return List.of();
		Map<String, Object> db = (Map<String, Object>) dataBody;
		// resCd가 S0000(정상)이 아니면(예: 다른 제휴사 소속 soldTo를 잘못 조회한 경우 E2103) 데이터 없이 경고만 남기고 스킵
		if (!"S0000".equals(db.get("resCd"))) {
			log.warn("[WelstorySync] payer-receive-detail 오류 soldTo={} resCd={} resMsg={}", soldTo, db.get("resCd"),
					db.get("resMsg"));
			return List.of();
		}
		Object data = db.get("data");
		return data instanceof List ? (List<Map<String, Object>>) data : List.of();
	}

	// 웰스토리 fdapi/service/* 공통 호출부.
	// Authorization: Bearer {accessToken} + guid 헤더를 싣고, {"dataHeader":..., "dataBody":{}} 형태의 JSON body로 POST.
	// (dataBody는 요청에서는 항상 빈 객체 — 모든 조건값은 dataHeader에 들어간다, 웰스토리 API 공통 스펙)
	private Map<String, Object> welstoryCallApi(String path, String accessToken, Map<String, Object> dataHeader) {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		headers.setBearerAuth(accessToken);
		headers.set("guid", welstoryNextGuid());

		Map<String, Object> payload = new HashMap<>();
		payload.put("dataHeader", dataHeader);
		payload.put("dataBody", new HashMap<>());

		ResponseEntity<Map<String, Object>> resp = restTemplate.exchange(WELSTORY_BASE_URL + "/fdapi/service/" + path,
				HttpMethod.POST, new HttpEntity<>(payload, headers),
				new ParameterizedTypeReference<Map<String, Object>>() {
				});
		Map<String, Object> res = resp.getBody();
		return res != null ? res : Map.of();
	}
}
