package com.example.demo.service;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

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
	// 클래스 내부 self-invocation(this.method())은 스프링 AOP 프록시를 안 거쳐서 @Transactional이
	// 적용 안 되므로, 트랜잭션이 꼭 걸려야 하는 메서드를 내부에서 호출할 땐 이 프록시 참조(self)를 통해 호출한다.
	private final AccountService self;

	// ===================== 웰스토리 SW-FD 주문API 연동 (필드) =====================
	// WelstorySyncScheduler가 매일 18시(KST)에 WelstoryPurchaseSync()를 호출 ->
	// 제휴사 2곳의 API를 각각 호출해 당일/전일/전전일(3일치) 입고내역을 tb_account_purchase_tally(_detail)에 저장한다.
	// (당일 저녁 기준으로 아직 늦게 확정되는 전일/전전일분이 있을 수 있어 3일치를 매번 같이 재조회한다)

	// 웰스토리 API 게이트웨이 base url (토큰발급: /oauth/2.0/token, 서비스: /fdapi/service/*)
	// 운영은 기본값(welgw.welstory.com), 로컬은 application-secret-local.properties에서 품질서버(scp-stg-welgw)로 덮어쓴다
	@Value("${welstory.api.base-url:https://welgw.welstory.com}")
	private String WELSTORY_BASE_URL;
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
			S3FileStorageService fileStorageService,
			@Lazy AccountService self) {
		this.accountMapper = accountMapper;
		this.headOfficeMapper = headOfficeMapper;
		this.operateMapper = operateMapper;
		this.fileStorageService = fileStorageService;
		this.self = self;
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

	// 거래처 -> 출근부 -> 저장 시 변경이력 기록 (호출부에서 실패를 무시하도록 감싸서 사용)
	public int AccountRecordHistorySave(Map<String, Object> paramMap) {
		int iResult = 0;
		iResult = accountMapper.AccountRecordHistorySave(paramMap);
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
			deleteReplacedReceiptImage(oldReceiptImage, paramMap.get("receipt_image"),
					paramMap.containsKey("receipt_image"));
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

	// 출근부 -> 거래처/연/월 기준 잠금 기간 목록 조회
	public List<Map<String, Object>> AccountRecordLockList(Map<String, Object> paramMap) {
		return accountMapper.AccountRecordLockList(paramMap);
	}

	// 출근부 -> 주간/월간 잠금 및 해제 저장
	public int AccountRecordLockSave(Map<String, Object> paramMap) {
		int iResult = accountMapper.AccountRecordLockSave(paramMap);
		// 월 잠금 해제는 전체 거래처 기준이므로 거래처별로 남아 있던 월 잠금도 함께 해제
		if ("M".equals(String.valueOf(paramMap.get("lock_type"))) && "N".equals(String.valueOf(paramMap.get("lock_yn")))) {
			accountMapper.AccountRecordMonthLockClear(paramMap);
		}
		return iResult;
	}

	// 출근부 -> 월 단위 일괄 등록(유틸/통합) 대상 거래처 중 해당 월 잠금이 있는지 조회
	public Map<String, Object> AccountRecordMonthLockCheck(Map<String, Object> paramMap) {
		return accountMapper.AccountRecordMonthLockCheck(paramMap);
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
			if (row == null)
				continue;
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

	// 출, 퇴근 기록 -> 이름 + 휴대폰 뒷자리 + 기기 토큰으로 승인된 근무지 목록 조회
	public List<Map<String, Object>> CommuteRegisteredAccountList(Map<String, Object> paramMap) {
		return accountMapper.CommuteRegisteredAccountList(paramMap);
	}

	// ✅ 출, 퇴근 기록 -> 등록기기 등록/변경 요청 - tb_member_device에 PK(유니크 제약)가 없으므로
	// 기존 행이 있는지 먼저 조회해서 있으면 UPDATE, 없으면 INSERT 한다.
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

	// 출, 퇴근 기록 -> 앱 로그인 사용자(본사/영양사) 확인용 사용자 정보 조회
	public Map<String, Object> CommuteLoginUserInfo(Map<String, Object> paramMap) {
		return accountMapper.CommuteLoginUserInfo(paramMap);
	}

	// 출, 퇴근 기록 -> 대리출근 방지: 오늘 이 기기로 이미 찍힌 다른 사람(user_name+phone_last4) 이력 조회
	public List<Map<String, Object>> SelectCommuteIdentitiesByDeviceToken(Map<String, Object> paramMap) {
		return accountMapper.SelectCommuteIdentitiesByDeviceToken(paramMap);
	}

	// 출, 퇴근 기록 -> 이 device_token이 이미 다른 사람에게 승인돼 있는지 조회 (대리출근 의심 탐지)
	public Map<String, Object> SelectApprovedDeviceOwner(Map<String, Object> paramMap) {
		return accountMapper.SelectApprovedDeviceOwner(paramMap);
	}

	// 출, 퇴근 기록 -> 같은 사람(account_id+user_name)이 이미 다른 phone_last4로 승인받은 행 조회 (이중
	// 승인/동명이인 확인용)
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

	// 출, 퇴근 기록 -> 출근부 인원 휴대폰 뒷자리 조회 (출퇴근 기록과 이름+뒷자리 매칭용)
	public List<Map<String, Object>> CommuteMemberPhoneList(Map<String, Object> paramMap) {
		return accountMapper.CommuteMemberPhoneList(paramMap);
	}

	// 출퇴근 앱 -> 플랫폼별 최신 요구 버전 조회
	public Map<String, Object> CommuteAppVersionInfo(Map<String, Object> paramMap) {
		return accountMapper.CommuteAppVersionInfo(paramMap);
	}

	// 출퇴근 앱 -> 기기별 개인정보 수집 동의 여부 조회
	public Map<String, Object> CommutePrivacyConsentStatus(Map<String, Object> paramMap) {
		return accountMapper.CommutePrivacyConsentStatus(paramMap);
	}

	// 출퇴근 앱 -> 개인정보 수집 동의 저장. 기존 동의 이력이 있으면 갱신하고, 없으면 새로 만든다.
	public int CommutePrivacyConsentAgree(Map<String, Object> paramMap) {
		Map<String, Object> existing = accountMapper.CommutePrivacyConsentStatus(paramMap);
		if (existing == null) {
			return accountMapper.CommutePrivacyConsentInsert(paramMap);
		}
		return accountMapper.CommutePrivacyConsentUpdate(paramMap);
	}

	// ===================== 웰스토리 SW-FD 주문API 연동 (로직) =====================
	//
	// 전체 흐름 요약
	// WelstorySyncScheduler(매일 18시 KST)
	// -> WelstoryPurchaseSync() 당일/전일/전전일 3일치를 각각 제휴사 2곳 순회
	// -> welstorySyncOneClient(client) client 하나: 토큰발급 -> 사업장(soldTo) 목록조회
	// -> welstorySyncReceiveDetail(soldTo 하나) 해당 날짜 입고내역 전체 조회
	// -> AccountPurchaseSave(master 1행) "하루당 1 master" = tb_account_purchase_tally
	// 1 row
	// -> AccountPurchaseDetailSave(detail N행) 해당 날짜 전체 품목(주문 여러 건 섞여있어도) =
	// tb_account_purchase_tally_detail N rows
	//
	// account_id는 tb_account.welstory_soldto 컬럼(soldTo 코드 매핑, 수동 등록해둔 값)으로 조회하며,
	// 매핑이 없는 soldTo(신규 사업장 등)는 저장하지 않고 경고 로그만 남기고 건너뛴다.

	// 진입점. 스케줄러가 이 메서드 하나만 호출한다.
	// client1(주식회사 더채움), client2(더채움 위탁급식) 순서로 각각 동기화하고, 저장(=신규/갱신)된
	// master(soldTo/날짜) 건수 합계를 반환
	// 당일 저녁(18시)에 도는 구조라 대상일은 "오늘"이지만, 늦게 확정되는 주문을 놓치지 않도록
	// 전일/전전일도 매번 같이 재조회해서 최신 상태로 덮어쓴다 (오늘/어제/그제 총 3일치).
	// LocalDate.now()가 아니라 Asia/Seoul 기준으로 날짜를 뽑는다 — 서버(UTC)의 LocalDate.now()를 쓰면
	// 자정 근처(KST 00~09시, UTC로는 전날)에 날짜가 하루 어긋날 수 있다 (guid와 동일한 이유).
	public int WelstoryPurchaseSync() {
		LocalDate today = LocalDate.now(java.time.ZoneId.of("Asia/Seoul"));
		int total = 0;
		for (int daysBack = 0; daysBack <= 2; daysBack++) {
			String targetDate = today.minusDays(daysBack).format(DateTimeFormatter.ofPattern("yyyyMMdd"));
			total += WelstoryPurchaseSync(targetDate);
		}
		return total;
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
	// 1) 토큰 발급
	// 2) payer-rep-soldto로 이 client 소속 사업장(soldTo) 전체 목록을 받아옴
	// -> 사업장 목록을 하드코딩하지 않고 매번 API로 받아오므로, 웰스토리 쪽에 사업장이 추가/변경돼도 코드 수정 불필요
	// 3) 사업장마다 tb_account 매핑을 확인해서 있는 것만, reqDeliveryDate 기준 입고내역 동기화
	private int welstorySyncOneClient(String clientId, String clientSecret, String reqDeliveryDate) {
		// application-secret*.properties에서 client_id/secret을 못 읽어온 경우.
		// (0건만 찍히고 다른 로그가 전혀 없다면 대부분 이 케이스 — 이 서버에서 실제 로딩되는 secret 파일에
		// welstory.client1.id/secret, welstory.client2.id/secret 값이 비어있다는 뜻)
		if (clientId == null || clientId.isBlank() || clientSecret == null || clientSecret.isBlank()) {
			log.warn("[WelstorySync] welstory.clientN.id/secret 프로퍼티가 비어있어 스킵함 (clientId blank={})",
					clientId == null || clientId.isBlank());
			return 0;
		}
		int saved = 0;
		try {
			// 토큰은 이 client 동기화 1회 실행 동안만 재사용(30일 유효하지만 매번 재발급해도 문제는 없음, 단순화를 위해 매 실행마다 새로
			// 받음)
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
		List<Map<String, Object>> lines = welstoryReceiveDetail(accessToken, soldTo, accountId, reqDeliveryDate);
		if (lines.isEmpty()) {
			return 0;
		}

		// ---- master(하루 단위) 합계 계산 ----
		// 기존 프론트(AccountPurchaseDeadlineTab.js)의 과세/면세 집계 로직과 동일한 기준을 따름:
		// total = 모든 품목의 totAmt(품목 합계금액) 합
		// vat = 모든 품목의 vat(부가세) 합
		// tax = 과세 품목(taxCode가 'Full TAX' 또는 'CONS')의 (totAmt - vat) = 공급가액 합
		// taxFree = 면세 품목(taxCode가 'No TAX')의 totAmt 합
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
		// 스케줄러가 재실행돼도(같은 날짜) 항상 같은 sale_id라 ON DUPLICATE KEY UPDATE로 덮어쓰기만 되고 중복 row가 안
		// 생긴다.
		String saleId = accountId + "_" + reqDeliveryDate;

		Map<String, Object> master = new HashMap<>();
		master.put("account_id", accountId);
		master.put("sale_id", saleId);
		master.put("type", "1"); // 삼성웰스토리 계열 고정 type (기존 "거래처 마감 자료" 화면의 type 1~4 중 1번 사용, 사용자 확정값)
		master.put("saleDate", welstoryToIsoDate(String.valueOf(lines.get(0).get("billDate")))); // 입고일(YYYYMMDD) ->
																									// "YYYY-MM-DD"
		master.put("total", total);
		master.put("discount", 0); // 웰스토리 입고내역엔 할인 개념이 없어서 항상 0
		master.put("vat", vat);
		master.put("taxFree", taxFree);
		master.put("tax", tax);
		master.put("use_name", "삼성웰스토리(주)"); // 고정값(사용자 확정) — 사업장명(soldToNm)이 아니라 공급처명 고정 표기
		master.put("buyer", payerNm); // 구매자(제휴사)명: "주식회사 더채움" 또는 "더채움(위탁급식)"
		// 나중에 이 row가 어느 soldTo에서 왔는지 추적할 수 있도록 note에 남겨둠
		master.put("note", "웰스토리 API 연동 (soldTo=" + soldTo + ")");
		master.put("user_id", WELSTORY_SYNC_USER_ID);
		AccountPurchaseSave(master);

		// ---- detail row 저장: tb_account_purchase_tally_detail (PK = item_id + sale_id)
		// ----
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

			// 프론트(AccountPurchaseDeadlineTab, accountPurchaseDeadlineDetailData.js
			// normalizeDetailAmounts)가
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

		// tb_account_purchase_tally(_detail)에만 저장하고 끝나면 집계표(tb_account_tally_sheet)에 반영되지 않는다.
		// 프론트(AccountPurchaseDeadlineTab)와 동일하게 "상세 저장 -> 마스터 저장" 순서를 지킨 뒤,
		// AccountController의 일반 저장 경로와 같이 sp_sync_tally_sheet_one_day를 태워 집계표에도 보이게 한다.
		LocalDate saleLocalDate = LocalDate.parse(String.valueOf(master.get("saleDate")));
		master.put("year", saleLocalDate.getYear());
		master.put("month", saleLocalDate.getMonthValue());
		// self(프록시 경유)로 호출해야 @Transactional이 실제로 걸려서, 4단계 중 하나라도 실패하면 전부 롤백된다.
		self.TallySheetPaymentSave(master);

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
	// 가이드 스펙: "거래일시(17자리, yyyyMMddHHmmssSSS) + seq(2자리)" = 총 19자리이며, 모든 거래를 통틀어
	// 중복되면 안 됨.
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
	// 이 client(제휴사)에 등록된 사업장(soldTo) 전체 목록을 반환한다(사업장이 많지 않아 페이징 없이 pageRow=100,
	// contYn="N" 고정 한 번만 호출).
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
			// receiveDetail과 동일하게, 정상(S0000)이 아니면 반드시 로그를 남겨서 원인(토큰만료/IP차단/파라미터오류 등)을 알 수
			// 있게 함
			log.warn("[WelstorySync] payer-rep-soldto 오류 resCd={} resMsg={}", db.get("resCd"), db.get("resMsg"));
			return List.of();
		}
		Object data = db.get("data");
		return data instanceof List ? (List<Map<String, Object>>) data : List.of();
	}

	// [제휴사 사업장 입고내역 조회] API(payer-receive-detail) 호출.
	// soldTo(사업장) + reqDeliveryDate(입고일, YYYYMMDD) 1건 기준으로 그날 입고된 품목 라인 목록을 반환한다.
	@SuppressWarnings("unchecked")
	private List<Map<String, Object>> welstoryReceiveDetail(String accessToken, String soldTo, String accountId,
			String reqDeliveryDate) {
		Map<String, Object> dataHeader = new HashMap<>();
		dataHeader.put("soldTo", soldTo);
		dataHeader.put("reqDeliveryDate", reqDeliveryDate);
		Map<String, Object> body = welstoryCallApi("payer-receive-detail", accessToken, dataHeader, accountId);

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
	// Authorization: Bearer {accessToken} + guid 헤더를 싣고, {"dataHeader":...,
	// "dataBody":{}} 형태의 JSON body로 POST.
	// (dataBody는 요청에서는 항상 빈 객체 — 모든 조건값은 dataHeader에 들어간다, 웰스토리 API 공통 스펙)
	// accountId는 입고내역 조회일 때 전문 로그에 남길 거래처 ID (사업장 조회는 null)
	private Map<String, Object> welstoryCallApi(String path, String accessToken, Map<String, Object> dataHeader) {
		return welstoryCallApi(path, accessToken, dataHeader, null);
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> welstoryCallApi(String path, String accessToken, Map<String, Object> dataHeader,
			String accountId) {
		String guid = welstoryNextGuid();
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		headers.setBearerAuth(accessToken);
		headers.set("guid", guid);

		Map<String, Object> payload = new HashMap<>();
		payload.put("dataHeader", dataHeader);
		payload.put("dataBody", new HashMap<>());

		Map<String, Object> res = null;
		String errorBody = null;
		try {
			ResponseEntity<Map<String, Object>> resp = restTemplate.exchange(WELSTORY_BASE_URL + "/fdapi/service/" + path,
					HttpMethod.POST, new HttpEntity<>(payload, headers),
					new ParameterizedTypeReference<Map<String, Object>>() {
					});
			res = resp.getBody();
		} catch (HttpStatusCodeException e) {
			errorBody = e.getResponseBodyAsString(StandardCharsets.UTF_8);
			throw e;
		} finally {
			welstoryPurchaseApiLog(path, guid, payload, res, errorBody, accountId);
		}
		return res != null ? res : Map.of();
	}

	// 입고 동기화(사업장 조회·입고내역 조회) 호출 전문을 tb_supplier_api_log(type=1)에 저장하는 메서드.
	// 요청 전문은 guid를 앞에 붙여 항상 저장하고, 응답 전문은 사업장 조회와 실패 응답만 저장한다.
	// 기록 저장이 실패해도 입고 동기화는 계속 진행되도록 예외를 삼킨다.
	@SuppressWarnings("unchecked")
	private void welstoryPurchaseApiLog(String path, String guid, Map<String, Object> payload,
			Map<String, Object> res, String errorBody, String accountId) {
		try {
			String apiType = "payer-rep-soldto".equals(path) ? "SITE" : "ORDER_LIST";
			Object dataBody = res == null ? null : res.get("dataBody");
			Map<String, Object> resBody = dataBody instanceof Map ? (Map<String, Object>) dataBody : Map.of();
			boolean success = "S0000".equals(resBody.get("resCd"));
			Object data = resBody.get("data");

			Map<String, Object> requestLog = new LinkedHashMap<>();
			requestLog.put("guid", guid);
			requestLog.put("dataHeader", payload.get("dataHeader"));
			requestLog.put("dataBody", payload.get("dataBody"));

			Map<String, Object> logRow = new HashMap<>();
			logRow.put("type", WELSTORY_TYPE);
			logRow.put("api_type", apiType);
			logRow.put("account_id", accountId);
			logRow.put("request_json", supplierJson.writerWithDefaultPrettyPrinter().writeValueAsString(requestLog));
			logRow.put("response_json", success && !"SITE".equals(apiType) ? null
					: (res != null ? supplierJson.writeValueAsString(res) : errorBody));
			logRow.put("row_count", data instanceof List ? ((List<?>) data).size() : null);
			logRow.put("success_yn", success ? "Y" : "N");
			accountMapper.SupplierApiLogSave(logRow);
		} catch (Exception e) {
			log.warn("[WelstorySync] 전문 로그 저장 실패: {} {}", path, e.toString());
		}
	}

	// ===================== 웰스토리 사업장·판가 전문 기록 =====================
	//
	// 전체 흐름 요약
	// WelstoryPriceSync(순기년도, 순기, soldTo)
	// -> 제휴사(client1, client2)마다 토큰 발급 -> 사업장 조회(payer-rep-soldto)
	// -> 사업장(soldTo)마다 해당 순기 전체 판가 조회(payer-allitem-price, 100건씩 페이지)
	// -> 호출마다 요청 전문(guid+dataHeader+dataBody)과 성공여부·건수를 tb_supplier_api_log(type=1)에 저장 (welstoryLoggedCall)
	//    응답 원문은 저장하지 않고 실패 응답만 남긴다. 토큰은 Authorization 헤더로만 보내므로 전문에 남지 않는다.

	// tb_account_mapping.type 웰스토리 구분값
	private static final int WELSTORY_TYPE = 1;

	// 웰스토리 판가 전문 수집 진입점.
	// year(순기년도 YYYY) + period(순기 2자리)의 전체 판가를 사업장마다 조회해 전문을 저장한다.
	// soldTo를 넘기면 그 사업장만 조회한다(테스트·재수집용).
	public Map<String, Object> WelstoryPriceSync(String year, String period, String soldTo) {
		int success = 0, empty = 0, failed = 0;
		List<String> failedSites = new ArrayList<>();
		List<String[]> clients = List.of(new String[] { welstoryClient1Id, welstoryClient1Secret },
				new String[] { welstoryClient2Id, welstoryClient2Secret });
		for (String[] client : clients) {
			if (client[0] == null || client[0].isBlank() || client[1] == null || client[1].isBlank())
				continue;
			try {
				String accessToken = welstoryIssueToken(client[0], client[1]);
				Map<String, Object> siteHeader = new LinkedHashMap<>();
				siteHeader.put("pageRow", 100);
				siteHeader.put("contYn", "N");
				siteHeader.put("nextKey", "");
				JsonNode sites = welstoryLoggedCall("SITE", "payer-rep-soldto", accessToken, siteHeader,
						new LinkedHashMap<>(), null);
				if (!"S0000".equals(sites.path("dataBody").path("resCd").asText()))
					throw new IllegalStateException("웰스토리 사업장 조회 실패");

				// 판가는 대표 soldTo(repSoldtoYn=Y)로만 조회되고, 같은 단가코드(pricingCode) 사업장은 대표 판가를 같이 쓴다.
				// soldTo를 지정했으면 그 사업장의 단가코드를 찾아 해당 대표 soldTo만 조회한다.
				String targetPricingCode = null;
				if (soldTo != null && !soldTo.isBlank()) {
					for (JsonNode site : sites.path("dataBody").path("data"))
						if (soldTo.equals(site.path("soldTo").asText()))
							targetPricingCode = site.path("pricingCode").asText("");
					if (targetPricingCode == null)
						continue;
				}
				for (JsonNode site : sites.path("dataBody").path("data")) {
					String code = site.path("soldTo").asText("");
					if (code.isBlank() || !"Y".equals(site.path("repSoldtoYn").asText()))
						continue;
					if (targetPricingCode != null && !targetPricingCode.equals(site.path("pricingCode").asText()))
						continue;
					try {
						if (welstoryPriceOneSite(accessToken, code, year, period) > 0)
							success++;
						else
							empty++;
					} catch (Exception e) {
						// 사업장 1곳 실패가 나머지 사업장 수집을 막지 않도록 여기서 끊는다 (전문은 welstoryLoggedCall에서 이미 저장됨)
						failed++;
						failedSites.add(code);
						log.error("[WelstoryPriceSync] 판가 수집 실패 soldTo={} {}-{} : {}", code, year, period,
								e.getMessage());
					}
				}
			} catch (Exception e) {
				// 제휴사 1곳이 실패해도(토큰 오류 등) 다른 제휴사 처리는 계속한다
				log.error("[WelstoryPriceSync] client 처리 실패: {} : {}", client[0], e.getMessage());
			}
		}

		Map<String, Object> result = new LinkedHashMap<>();
		result.put("success", success);
		result.put("empty", empty);
		result.put("failed", failed);
		result.put("failedSites", String.join(",", failedSites));
		return result;
	}

	// 사업장(soldTo) 1곳의 순기 전체 판가를 페이지 끝까지 조회하고, 받은 품목 수를 반환한다.
	// 첫 요청이 '조회 데이터 없음'(E1103)이면 contYn=N으로 한 번 더 묻고(order-api와 동일), 그래도 없으면 0건.
	private int welstoryPriceOneSite(String accessToken, String soldTo, String year, String period) {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("soldTo", soldTo);
		body.put("periodGroupYear", year);
		body.put("periodGroup", period);
		String contYn = "Y", nextKey = "";
		boolean retried = false;
		int rows = 0;
		for (int page = 0; page < 1000; page++) {
			Map<String, Object> header = new LinkedHashMap<>();
			header.put("pageRow", 100);
			header.put("contYn", contYn);
			header.put("nextKey", nextKey);
			JsonNode res = welstoryLoggedCall("PRICE", "payer-allitem-price", accessToken, header, body, soldTo);
			String code = res.path("dataBody").path("resCd").asText("");
			if (!"S0000".equals(code)) {
				boolean noData = "E1103".equals(code) || "EE120".equals(code);
				if (noData && nextKey.isBlank() && !retried) {
					contYn = "N";
					retried = true;
					continue;
				}
				if (noData && nextKey.isBlank())
					return rows;
				throw new IllegalStateException("웰스토리 판가 조회 오류 resCd=" + code);
			}
			rows += res.path("dataBody").path("data").size();
			contYn = res.path("dataHeader").path("contYn").asText("N");
			nextKey = res.path("dataHeader").path("nextKey").asText("");
			if (!"Y".equals(contYn) || nextKey.isBlank())
				return rows;
		}
		throw new IllegalStateException("웰스토리 판가 페이지 수 초과");
	}

	// 웰스토리 fdapi/service/* 호출 + 전문 기록.
	// 결과코드와 관계없이 tb_supplier_api_log에 저장하고 응답 JSON을 그대로 돌려준다(성공 판정은 호출자가 resCd로 한다).
	// 판가는 순기 단위라 deposit_dt에는 응답의 순기 시작일(periodFromDt)을 넣는다.
	private JsonNode welstoryLoggedCall(String apiType, String path, String accessToken, Map<String, Object> dataHeader,
			Map<String, Object> dataBody, String soldTo) {
		String guid = welstoryNextGuid();
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("dataHeader", dataHeader);
		payload.put("dataBody", dataBody);

		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		headers.setBearerAuth(accessToken);
		headers.set("guid", guid);

		Integer httpStatus = null;
		String raw = null;
		String errorMsg = null;
		long started = System.currentTimeMillis();
		try {
			ResponseEntity<byte[]> resp = restTemplate.exchange(WELSTORY_BASE_URL + "/fdapi/service/" + path,
					HttpMethod.POST, new HttpEntity<>(payload, headers), byte[].class);
			httpStatus = resp.getStatusCode().value();
			raw = resp.getBody() == null ? null : new String(resp.getBody(), StandardCharsets.UTF_8);
		} catch (HttpStatusCodeException e) {
			httpStatus = e.getStatusCode().value();
			raw = new String(e.getResponseBodyAsByteArray(), StandardCharsets.UTF_8);
		} catch (Exception e) {
			errorMsg = "응답 수신 실패: " + e.getClass().getSimpleName();
		}
		long elapsed = System.currentTimeMillis() - started;
		if (raw != null)
			raw = raw.replace(accessToken, "***");

		JsonNode res = supplierJson.createObjectNode();
		try {
			if (raw != null)
				res = supplierJson.readTree(raw);
		} catch (Exception e) {
			errorMsg = "응답 JSON 형식 아님";
		}
		JsonNode resBody = res.path("dataBody");
		String resCd = resBody.path("resCd").isMissingNode() ? null : resBody.path("resCd").asText();
		String resMsg = errorMsg != null ? errorMsg : resBody.path("resMsg").asText(null);
		boolean success = httpStatus != null && httpStatus == 200 && "S0000".equals(resCd);
		JsonNode data = resBody.path("data");
		String periodFrom = data.path(0).path("periodFromDt").asText("");

		Map<String, Object> logRow = new HashMap<>();
		logRow.put("type", WELSTORY_TYPE);
		logRow.put("api_type", apiType);
		logRow.put("api_path", "/fdapi/service/" + path);
		logRow.put("account_id", null);
		logRow.put("site_code", soldTo);
		logRow.put("deposit_dt", periodFrom.matches("\\d{8}") ? periodFrom : null);
		logRow.put("guid", guid);
		// 웰스토리 guid는 HTTP 헤더로 보내므로 저장용 요청 전문 맨 앞에 붙인다 (실제 전송 본문은 dataHeader·dataBody만)
		Map<String, Object> requestLog = new LinkedHashMap<>();
		requestLog.put("guid", guid);
		requestLog.putAll(payload);
		try {
			logRow.put("request_json", supplierJson.writerWithDefaultPrettyPrinter().writeValueAsString(requestLog));
		} catch (Exception e) {
			logRow.put("request_json", String.valueOf(requestLog));
		}
		// 응답 원문은 사업장 조회(soldTo·대표·단가코드 연결 정보, 수 KB)와 실패 응답만 저장한다
		logRow.put("response_json", success && !"SITE".equals(apiType) ? null : raw);
		logRow.put("row_count", data.isArray() ? data.size() : null);
		logRow.put("http_status", httpStatus);
		logRow.put("rtn_cd", resCd);
		logRow.put("rtn_msg", resMsg != null && resMsg.length() > 500 ? resMsg.substring(0, 500) : resMsg);
		logRow.put("success_yn", success ? "Y" : "N");
		logRow.put("elapsed_ms", elapsed);
		accountMapper.SupplierApiLogSave(logRow);

		if (httpStatus == null || raw == null)
			throw new IllegalStateException("웰스토리 " + apiType + " " + resMsg);
		return res;
	}

	// ===================== 아워홈 TOS API 연동 =====================
	//
	// 전체 흐름 요약
	// OurhomePriceSync(납품일, 사업장코드)
	// -> 사업장 조회(getBusiInfo): 더채움 본부 소속 아워홈 사업장 전체 + 사업장별 발주 가능일
	// -> 납품일이 발주 가능일인 사업장마다 판가 전체 조회(getGoodList)
	// -> 호출마다 요청 전문(tokenInfo+params, 토큰은 가림)과 성공여부·건수를 tb_supplier_api_log에 저장 (ourhomeCall)
	//    응답 원문(판가는 사업장 1곳 약 10MB)은 저장하지 않고, 실패 응답만 원인 확인용으로 남긴다.

	// tb_account_mapping.type 아워홈 구분값
	private static final int OURHOME_TYPE = 1042;
	// 판가 한 번에 받는 최대 건수(아워홈 스펙상 최대 10000)
	private static final int OURHOME_PAGE_ROW = 10000;
	// 공급사(웰스토리·아워홈) 응답 파싱·요청 전문 직렬화용
	private final ObjectMapper supplierJson = new ObjectMapper();
	// guid(yyyyMMddHHmmssSSS + 2자리 seq) 뒤 2자리 채번용 카운터
	private final AtomicInteger ourhomeGuidSeq = new AtomicInteger(0);

	// 아워홈 API 주소·본부 정보 (application.properties)
	@Value("${ourhome.api.base-url:}")
	private String ourhomeBaseUrl;
	@Value("${ourhome.api.tax-no:}")
	private String ourhomeTaxNo;
	@Value("${ourhome.api.head-group-code:}")
	private String ourhomeHeadGroupCode;
	// 아워홈 인증값 (application-secret*.properties)
	@Value("${ourhome.api.service-id:}")
	private String ourhomeServiceId;
	@Value("${ourhome.api.client-token:}")
	private String ourhomeClientToken;
	@Value("${ourhome.api.encrypt-seed:}")
	private String ourhomeEncryptSeed;

	// 사업자번호(ourhome.api.tax-no, 쉼표 구분)마다 사업장 조회(getBusiInfo)를 호출해 사업장 목록을 합친다.
	// 법인(본사·통합·도소매)과 개인(위탁)이 사업자번호가 달라 각각 조회해야 전체 사업장이 나온다.
	private List<JsonNode> ourhomeSites() {
		List<JsonNode> sites = new ArrayList<>();
		for (String taxNo : ourhomeTaxNo.split(",")) {
			if (taxNo.isBlank())
				continue;
			Map<String, Object> siteParams = new LinkedHashMap<>();
			siteParams.put("taxNo", taxNo.trim());
			siteParams.put("headGrpCd", ourhomeHeadGroupCode);
			siteParams.put("busiplcd", "");
			ourhomeCall("SITE", "/api/chaeum/v3/getBusiInfo", siteParams, null, null).path("result").path("data")
					.forEach(sites::add);
		}
		return sites;
	}

	// 아워홈 판가 전문 수집 진입점.
	// depositDt(납품일, YYYYMMDD)가 발주 가능일인 사업장마다 판가 전체를 조회해 전문을 저장한다.
	// siteCode를 넘기면 그 사업장만 조회한다(테스트·재수집용).
	public Map<String, Object> OurhomePriceSync(String depositDt, String siteCode) {
		int success = 0, skipped = 0, failed = 0;
		List<String> failedSites = new ArrayList<>();
		for (JsonNode site : ourhomeSites()) {
			String code = site.path("busiplCd").asText("");
			if (siteCode != null && !siteCode.isBlank() && !siteCode.equals(code))
				continue;
			// 발주 가능일이 아닌 납품일은 아워홈이 판가를 주지 않으므로 호출하지 않는다
			if (!Arrays.asList(site.path("depositDt").asText("").split(",")).contains(depositDt)) {
				skipped++;
				continue;
			}
			try {
				ourhomePriceOneSite(code, site.path("headGrpCd").asText(ourhomeHeadGroupCode), depositDt);
				success++;
			} catch (Exception e) {
				// 사업장 1곳 실패가 나머지 사업장 수집을 막지 않도록 여기서 끊는다 (전문은 ourhomeCall에서 이미 저장됨)
				failed++;
				failedSites.add(code);
				log.error("[OurhomePriceSync] 판가 수집 실패 site={} date={} : {}", code, depositDt, e.getMessage());
			}
		}

		Map<String, Object> result = new LinkedHashMap<>();
		result.put("success", success);
		result.put("skipped", skipped);
		result.put("failed", failed);
		result.put("failedSites", String.join(",", failedSites));
		return result;
	}

	// 사업장 1곳의 판가 전체를 조회한다. 건수가 10000을 넘으면 nextKey로 다음 페이지를 이어서 받는다.
	private void ourhomePriceOneSite(String siteCode, String headGroupCode, String depositDt) {
		String nextKey = "";
		for (int page = 0; page < 100; page++) {
			Map<String, Object> params = new LinkedHashMap<>();
			params.put("headGrpCd", headGroupCode);
			params.put("busiplcd", siteCode);
			params.put("depositDt", depositDt);
			params.put("keyWord", "");
			params.put("searchType", "A");
			params.put("onePageRow", OURHOME_PAGE_ROW);
			params.put("nextYn", nextKey.isEmpty() ? "N" : "Y");
			params.put("nextKey", nextKey);
			params.put("estYear", "");
			params.put("estIndex", "");
			params.put("queryType", "A");
			JsonNode result = ourhomeCall("PRICE", "/api/chaeum/v2/getGoodList", params, siteCode, depositDt)
					.path("result");
			if (!"Y".equals(result.path("nextYn").asText()))
				return;
			String key = result.path("nextKey").asText("");
			if (key.isBlank() || key.equals(nextKey))
				throw new IllegalStateException("아워홈 판가 다음 페이지 키 이상");
			nextKey = key;
		}
		throw new IllegalStateException("아워홈 판가 페이지 수 초과");
	}

	// ===================== 아워홈 입고내역 -> 매입집계 저장 =====================
	//
	// 전체 흐름 요약 (웰스토리 WelstoryPurchaseSync와 같은 구조)
	// OurhomeSyncScheduler(매일 20시 10분 KST)
	// -> OurhomePurchaseSync() 당일/전일/전전일 3일치
	// -> tb_account.ourhome_busiplcd로 매핑된 거래처만 주문 입고내역(getOrderList) 조회 (사업장 조회 생략)
	// -> 거래처·하루 1 master(tb_account_purchase_tally, type=1042) + 품목 N detail로 저장
	// -> 집계표·손익표·예산 반영(TallySheetPaymentSave)
	//
	// 출고완료(prcsStatYn=Y)·출고수량 > 0인 품목만 매입으로 본다. 입고분이 없으면 기존 API 매입을 지운다.
	// 현장에서 직접 입력한 아워홈(1042) 매입과 관계없이 API 매입은 별도 sale_id(_OH)로 저장한다(수기 데이터는 건드리지 않음).

	// 스케줄러가 자동 저장할 때 user_id/mod_id 컬럼에 남길 고정값
	private static final String OURHOME_SYNC_USER_ID = "SYSTEM";

	// 진입점. 오늘/어제/그제(KST) 3일치를 각각 동기화하고 저장된 master 건수 합계를 반환한다.
	public int OurhomePurchaseSync() {
		LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
		int total = 0;
		for (int daysBack = 0; daysBack <= 2; daysBack++)
			total += OurhomePurchaseSync(today.minusDays(daysBack).format(DateTimeFormatter.BASIC_ISO_DATE));
		return total;
	}

	// 특정 입고일(YYYYMMDD) 동기화. 수동 재수집(백필)에도 그대로 쓴다.
	public int OurhomePurchaseSync(String yyyyMMdd) {
		int saved = 0;
		// 사업장 조회 없이 tb_account.ourhome_busiplcd에 매핑된 사업장만 바로 입고내역을 조회한다
		for (Map<String, Object> account : accountMapper.OurhomeMappedAccountList()) {
			String siteCode = String.valueOf(account.get("ourhome_busiplcd")).trim();
			String accountId = String.valueOf(account.get("account_id"));
			try {
				saved += ourhomeSyncReceiveDetail(siteCode, accountId, yyyyMMdd);
			} catch (Exception e) {
				// 사업장 1곳 실패(연결 끊김 등)가 나머지를 막지 않도록 끊는다. 다음 날 "전일"로 다시 조회된다.
				log.error("[OurhomePurchaseSync] 입고내역 동기화 실패 site={} account={} date={} : {}", siteCode, accountId,
						yyyyMMdd, e.getMessage());
			}
			try {
				// 연달아 새 연결을 몰아서 맺으면 아워홈이 연결을 끊은 적이 있어 사업장 사이에 간격을 둔다
				Thread.sleep(300);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				break;
			}
		}
		return saved;
	}

	// 사업장 1곳의 입고일 주문 입고내역을 조회해 매입집계 master 1행 + detail N행으로 저장한다.
	private int ourhomeSyncReceiveDetail(String siteCode, String accountId, String yyyyMMdd) {
		Map<String, Object> params = new LinkedHashMap<>();
		params.put("headGrpCd", ourhomeHeadGroupCode);
		params.put("busiplcd", siteCode);
		params.put("depositDt", yyyyMMdd);
		params.put("goodcd", "");
		JsonNode result = ourhomeCall("ORDER_LIST", "/api/chaeum/v1/getOrderList", params, siteCode, yyyyMMdd,
				accountId).path("result");

		// 실제 입고된 품목만 사용: 삭제 품목 제외, 출고완료(prcsStatYn=Y)이고 출고수량(clhaqty)이 0보다 큰 줄만
		List<JsonNode> lines = new ArrayList<>();
		for (JsonNode line : result.path("data"))
			if (!"Y".equals(line.path("delYn").asText()) && "Y".equals(line.path("prcsStatYn").asText())
					&& line.path("clhaqty").asDouble() > 0)
				lines.add(line);

		// sale_id = 거래처ID_입고일_OH (웰스토리 거래처ID_입고일과 겹치지 않게 접미사로 구분, 재실행 시 같은 행 갱신)
		String saleId = accountId + "_" + yyyyMMdd + "_OH";
		String saleDate = welstoryToIsoDate(yyyyMMdd);

		Map<String, Object> dup = new HashMap<>();
		dup.put("account_id", accountId);
		dup.put("saleDate", saleDate);
		dup.put("sale_id", saleId);

		LocalDate saleLocalDate = LocalDate.parse(saleDate);
		if (lines.isEmpty()) {
			// 입고분이 없으면 이전 동기화로 저장된 API 매입(수량 0·미출고 품목 등)을 지우고 집계표를 다시 맞춘다
			if (accountMapper.AccountPurchaseTallyTotalBySaleId(dup) != null) {
				Map<String, Object> master = new HashMap<>();
				master.put("account_id", accountId);
				master.put("sale_id", saleId);
				master.put("type", "1042");
				master.put("saleDate", saleDate);
				master.put("year", saleLocalDate.getYear());
				master.put("month", saleLocalDate.getMonthValue());
				self.OurhomePurchaseDeleteOneDay(master, List.of(saleId));
			}
			return 0;
		}

		// ---- master(하루 단위) 합계: 아워홈이 품목별로 준 과세공급가·부가세·면세공급가를 그대로 합산 ----
		// total = 총매출액(부가세 포함) 합, tax = 과세공급가 합, vat = 부가세 합, taxFree = 면세공급가 합
		long total = 0, vat = 0, tax = 0, taxFree = 0;
		for (JsonNode line : lines) {
			total += line.path("salsAmt").asLong();
			vat += line.path("txtnTxamt").asLong();
			tax += line.path("txnSplamt").asLong();
			taxFree += line.path("dfSplamt").asLong();
		}

		Map<String, Object> master = new HashMap<>();
		master.put("account_id", accountId);
		master.put("sale_id", saleId);
		master.put("type", "1042"); // tb_account_mapping.type 아워홈
		master.put("saleDate", saleDate);
		master.put("total", total);
		master.put("discount", 0);
		master.put("vat", vat);
		master.put("taxFree", taxFree);
		master.put("tax", tax);
		master.put("use_name", "아워홈");
		master.put("buyer", "(주)더채움"); // 본부사업장명(GA709)
		master.put("note", "아워홈 API 연동 (busiplcd=" + siteCode + ")");
		master.put("user_id", OURHOME_SYNC_USER_ID);

		// ---- detail: 품목 한 줄씩. 금액 계산 방식은 웰스토리와 동일하게 qty=1, unitPrice=amount ----
		List<Map<String, Object>> details = new ArrayList<>();
		for (JsonNode line : lines) {
			long amount = line.path("salsAmt").asLong();
			long lineVat = line.path("txtnTxamt").asLong();
			long lineTax = line.path("txnSplamt").asLong();
			boolean taxable = lineTax > 0 || lineVat > 0;
			// 아워홈 대분류 '생활용품류'(세제·장갑 등)는 소모품, 나머지는 식재료
			boolean isCons = "생활용품류".equals(line.path("goodnmClass2").asText());
			String ordKey = line.path("ordKeyVal").asText();

			Map<String, Object> detail = new HashMap<>();
			// item_id: 아워홈 주문키+순번으로 만든 하루 내 고유 정수(재실행해도 같은 값)
			detail.put("item_id", Math.floorMod(
					(ordKey + "|" + line.path("ordSeq").asText() + "|" + line.path("foSeq").asText()).hashCode(),
					1_000_000_000));
			detail.put("sale_id", saleId);
			detail.put("name", line.path("goodnm").asText("").strip());
			detail.put("qty", "1");
			detail.put("amount", amount);
			detail.put("unitPrice", amount);
			detail.put("vat", lineVat);
			detail.put("tax", lineTax); // 과세공급가(면세면 0)
			detail.put("taxType", taxable ? "1" : "2"); // 1=과세, 2=면세
			detail.put("itemType", isCons ? "2" : "1"); // 1=식재료, 2=소모품
			// 규격 + 실제 입고수량/단가 + 아워홈 주문키 (note 컬럼 100자 제한)
			String note = line.path("goodsz").asText("") + " [수량:" + line.path("clhaqty").asText() + " 단가:"
					+ line.path("salsUcost").asText() + "] (" + ordKey + ")";
			detail.put("note", note.length() > 100 ? note.substring(0, 100) : note);
			detail.put("user_id", OURHOME_SYNC_USER_ID);
			details.add(detail);
		}

		master.put("year", saleLocalDate.getYear());
		master.put("month", saleLocalDate.getMonthValue());
		// self(프록시 경유)로 호출해야 @Transactional이 걸려 상세 교체~집계표 반영이 한 번에 롤백된다
		self.OurhomePurchaseSaveOneDay(master, details);
		return 1;
	}

	// 아워홈 하루치 매입 저장: master 저장 -> 그날 상세 전체 교체(주문 수정·삭제 반영) -> 집계표·손익표·예산 반영
	@Transactional(rollbackFor = Exception.class)
	public void OurhomePurchaseSaveOneDay(Map<String, Object> master, List<Map<String, Object>> details) {
		AccountPurchaseSave(master);
		accountMapper.PurchaseTallyDetailDeleteBySaleId(master);
		for (Map<String, Object> detail : details)
			AccountPurchaseDetailSave(detail);
		TallySheetPaymentSave(master);
	}

	// 아워홈 하루치 매입 삭제: sale_id별 master·detail 삭제(sp_sync_account_purchase_delete) -> 집계표·손익표·예산 재반영
	@Transactional(rollbackFor = Exception.class)
	public void OurhomePurchaseDeleteOneDay(Map<String, Object> master, List<String> saleIds) {
		for (String saleId : saleIds)
			deleteOurhomePurchase(saleId);
		TallySheetPaymentSave(master);
	}

	// 아워홈(1042) 매입 1건을 master·detail 모두 삭제한다
	private void deleteOurhomePurchase(String saleId) {
		Map<String, Object> param = new HashMap<>();
		param.put("sale_id", saleId);
		param.put("type", 1042);
		AccountPurchaseTallyDelete(param);
	}

	// 아워홈 API 공통 호출부.
	// tokenInfo(인증) + params(guid 포함)를 POST하고, 성공·실패와 관계없이 전문을 tb_supplier_api_log에 저장한다.
	// rtnCd가 0이 아니거나 응답을 못 받으면 예외를 던진다. 정상이면 응답 전체 JSON을 반환한다.
	private JsonNode ourhomeCall(String apiType, String path, Map<String, Object> params, String siteCode,
			String depositDt) {
		return ourhomeCall(apiType, path, params, siteCode, depositDt, null);
	}

	// accountId: 거래처 단위 호출(입고내역)일 때 로그에 남길 거래처 ID
	private JsonNode ourhomeCall(String apiType, String path, Map<String, Object> params, String siteCode,
			String depositDt, String accountId) {
		if (!ourhomeBaseUrl.startsWith("https://") || ourhomeTaxNo.isBlank() || ourhomeHeadGroupCode.isBlank()
				|| ourhomeClientToken.isBlank() || !ourhomeServiceId.matches("\\d+") || !ourhomeEncryptSeed.matches("-?\\d+"))
			throw new IllegalStateException("ourhome.api.* 설정이 비어있음 (application-secret*.properties 확인)");

		// guid·senddttm은 아워홈 기준(KST)으로 만든다 (서버가 UTC여도 validCd 검증이 맞도록)
		ZonedDateTime now = ZonedDateTime.now(ZoneId.of("Asia/Seoul"));
		String senddttm = now.format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
		String guid = now.format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"))
				+ String.format("%02d", Math.floorMod(ourhomeGuidSeq.getAndIncrement(), 100));
		Map<String, Object> requestParams = new LinkedHashMap<>(params);
		requestParams.put("guid", guid);

		Map<String, Object> tokenInfo = new LinkedHashMap<>();
		tokenInfo.put("extnSvcId", ourhomeServiceId);
		tokenInfo.put("senddttm", senddttm);
		tokenInfo.put("clientToken", ourhomeClientToken);
		tokenInfo.put("validCd", ourhomeValidCode(senddttm));
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("tokenInfo", tokenInfo);
		payload.put("params", requestParams);

		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);

		Integer httpStatus = null;
		String raw = null;
		String errorMsg = null;
		long started = System.currentTimeMillis();
		try {
			ResponseEntity<byte[]> resp = restTemplate.exchange(ourhomeBaseUrl + path, HttpMethod.POST,
					new HttpEntity<>(payload, headers), byte[].class);
			httpStatus = resp.getStatusCode().value();
			raw = resp.getBody() == null ? null : new String(resp.getBody(), StandardCharsets.UTF_8);
		} catch (HttpStatusCodeException e) {
			// 아워홈은 업무 오류(rtnCd<0)를 4xx 본문으로 돌려주므로 본문을 그대로 받아 사유를 남긴다
			httpStatus = e.getStatusCode().value();
			raw = new String(e.getResponseBodyAsByteArray(), StandardCharsets.UTF_8);
		} catch (Exception e) {
			errorMsg = "응답 수신 실패: " + e.getClass().getSimpleName();
		}
		long elapsed = System.currentTimeMillis() - started;
		// 오류 응답이 tokenInfo를 echo할 수 있어 저장 전에 클라이언트 토큰을 가린다
		if (raw != null)
			raw = raw.replace(ourhomeClientToken, "***");

		JsonNode res = null;
		try {
			res = raw == null ? null : supplierJson.readTree(raw);
		} catch (Exception e) {
			errorMsg = "응답 JSON 형식 아님";
		}
		String rtnCd = res == null || res.path("rtnCd").isMissingNode() ? null : res.path("rtnCd").asText();
		String rtnMsg = errorMsg != null ? errorMsg : res == null ? null : res.path("rtnMsg").asText(null);
		boolean success = httpStatus != null && httpStatus == 200 && "0".equals(rtnCd);
		JsonNode data = res == null ? null : res.path("result").path("data");

		// 저장용 요청 전문: 실제 보낸 tokenInfo + params 구조 그대로, 클라이언트 토큰만 가린다
		Map<String, Object> tokenLog = new LinkedHashMap<>(tokenInfo);
		tokenLog.put("clientToken", "***");
		Map<String, Object> requestLog = new LinkedHashMap<>();
		requestLog.put("tokenInfo", tokenLog);
		requestLog.put("params", requestParams);

		Map<String, Object> logRow = new HashMap<>();
		logRow.put("type", OURHOME_TYPE);
		logRow.put("api_type", apiType);
		logRow.put("api_path", path);
		logRow.put("account_id", accountId);
		logRow.put("site_code", siteCode);
		logRow.put("deposit_dt", depositDt);
		logRow.put("guid", guid);
		try {
			logRow.put("request_json", supplierJson.writerWithDefaultPrettyPrinter().writeValueAsString(requestLog));
		} catch (Exception e) {
			logRow.put("request_json", String.valueOf(requestLog));
		}
		// 응답 원문은 저장하지 않고 결과코드·건수만 남긴다. 실패 응답은 원인 확인용으로 원문을 남긴다(작음)
		// 응답 원문은 사업장 조회(soldTo·대표·단가코드 연결 정보, 수 KB)와 실패 응답만 저장한다
		logRow.put("response_json", success && !"SITE".equals(apiType) ? null : raw);
		logRow.put("row_count", data != null && data.isArray() ? data.size() : null);
		logRow.put("http_status", httpStatus);
		logRow.put("rtn_cd", rtnCd);
		logRow.put("rtn_msg", rtnMsg != null && rtnMsg.length() > 500 ? rtnMsg.substring(0, 500) : rtnMsg);
		logRow.put("success_yn", success ? "Y" : "N");
		logRow.put("elapsed_ms", elapsed);
		accountMapper.SupplierApiLogSave(logRow);

		if (!success)
			throw new IllegalStateException("아워홈 " + apiType + " 실패 http=" + httpStatus + " rtnCd=" + rtnCd
					+ " msg=" + rtnMsg);
		return res;
	}

	// 아워홈 검증코드(validCd).
	// ((100-년앞2) + (100-년뒤2) + (100-월) + (100-일)) * (초뒤1||분뒤1||시뒤1) + 외부서비스ID + 암호화시드
	String ourhomeValidCode(String senddttm) {
		long sum = (100 - Integer.parseInt(senddttm.substring(0, 2)))
				+ (100 - Integer.parseInt(senddttm.substring(2, 4)))
				+ (100 - Integer.parseInt(senddttm.substring(4, 6)))
				+ (100 - Integer.parseInt(senddttm.substring(6, 8)));
		long factor = Long.parseLong("" + senddttm.charAt(13) + senddttm.charAt(11) + senddttm.charAt(9));
		return String.valueOf(sum * factor + Long.parseLong(ourhomeServiceId) + Long.parseLong(ourhomeEncryptSeed));
	}
}
