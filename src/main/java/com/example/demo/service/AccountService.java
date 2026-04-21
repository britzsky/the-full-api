package com.example.demo.service;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.mapper.AccountMapper;
import com.example.demo.mapper.HeadOfficeMapper;
import com.example.demo.mapper.OperateMapper;

@Service
public class AccountService {

	AccountMapper accountMapper;
	HeadOfficeMapper headOfficeMapper;
	OperateMapper operateMapper;
	private final String uploadDir;

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

		String relativePath = normalizedPath.substring("/image/".length());
		if (!hasText(relativePath)) {
			return;
		}

		Path rootPath = Paths.get(uploadDir).toAbsolutePath().normalize();
		Path targetPath = rootPath.resolve(relativePath).normalize();
		if (!targetPath.startsWith(rootPath)) {
			return;
		}

		try {
			Files.deleteIfExists(targetPath);
		} catch (IOException ignore) {
		}

		// 파일이 이미 없더라도, 해당 파일이 있던 폴더가 비었으면 1단계만 정리한다.
		deleteEmptyParentDirectories(targetPath.getParent(), rootPath);
	}

	// 비어있는 부모 폴더 1단계 정리
	private void deleteEmptyParentDirectories(Path startPath, Path rootPath) {
		if (startPath == null) {
			return;
		}

		Path currentPath = startPath.toAbsolutePath().normalize();
		// 파일이 들어있던 바로 그 폴더(1단계)만 빈 폴더 정리한다.
		if (!currentPath.startsWith(rootPath) || currentPath.equals(rootPath)) {
			return;
		}

		boolean isEmpty = false;
		try (DirectoryStream<Path> stream = Files.newDirectoryStream(currentPath)) {
			isEmpty = !stream.iterator().hasNext();
		} catch (IOException e) {
			return;
		}

		if (!isEmpty) {
			return;
		}

		try {
			Files.deleteIfExists(currentPath);
		} catch (IOException e) {
			return;
		}
	}

	// 영수증 이미지 변경 시 이전 파일 삭제
	private void deleteReplacedReceiptImage(String beforePath, Object afterPathObj) {
		String oldPath = normalizeImagePath(beforePath);
		String newPath = normalizeImagePath(asText(afterPathObj));

		// 새 파일 경로가 들어온 경우에만 교체 삭제를 수행한다.
		if (!hasText(oldPath) || !hasText(newPath) || oldPath.equals(newPath)) {
			return;
		}
		deletePhysicalReceiptImage(oldPath);
	}

	// 회계 -> OCR 컨트롤러에서 호출: 기존 영수증 경로 조회 (public)
	public String AccountPurchaseReceiptImageBySaleId(Map<String, Object> paramMap) {
		return findAccountPurchaseReceiptImage(paramMap);
	}

	// 회계 -> OCR 컨트롤러에서 호출: 기존 파일 삭제 (public)
	public void DeleteOldReceiptImage(String oldPath, String newPath) {
		deleteReplacedReceiptImage(oldPath, newPath);
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
			@Value("${file.upload-dir}") String uploadDir) {
		this.accountMapper = accountMapper;
		this.headOfficeMapper = headOfficeMapper;
		this.operateMapper = operateMapper;
		this.uploadDir = uploadDir;
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
	public List<Map<String, Object>> AccountListV2(int accountType) {
		List<Map<String, Object>> resultList = new ArrayList<>();
		resultList = accountMapper.AccountListV2(accountType);
		return resultList;
	}

	// 유틸 직원 조회
	public List<Map<String, Object>> AccountUtilMemberList() {
		List<Map<String, Object>> resultList = new ArrayList<>();
		resultList = accountMapper.AccountUtilMemberList();
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

	// 거래처 -> 출근부 -> 연차대장 저장
	public int AccountAnnualLeaveLedgerSave(Map<String, Object> paramMap) {
		int iResult = 0;
		iResult = accountMapper.AccountAnnualLeaveLedgerSave(paramMap);
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

		return 1; // ✅ 전체 성공
	}

	// ProfitLossTotalSave만 호출 (합계 재계산, year/month/account_id를 param으로 받음)
	public void callProfitLossTotalSave(Map<String, Object> param) {
		param.put("result", 0);
		headOfficeMapper.ProfitLossTotalSave(param);
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
		String oldReceiptImage = findAccountPurchaseReceiptImage(paramMap);
		iResult = accountMapper.AccountPurchaseSave(paramMap);
		if (iResult > 0) {
			deleteReplacedReceiptImage(oldReceiptImage, paramMap.get("receipt_image"));
			try { accountMapper.AccountPurchaseHistorySave(paramMap); } catch (Exception ignored) {}
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
						if (historyParam.get("total") == null) historyParam.put("total", master.get("total"));
						if (historyParam.get("saleDate") == null) historyParam.put("saleDate", master.get("saleDate"));
						if (historyParam.get("account_id") == null) historyParam.put("account_id", master.get("account_id"));
					}
				}
				accountMapper.AccountPurchaseHistorySave(historyParam);
			} catch (Exception ignored) {}
		}
		return iResult;
	}

	// 회계 -> 매입 -> 매입마감 조회
	public List<Map<String, Object>> AccountPurchaseTallyList(Map<String, Object> paramMap) {
		List<Map<String, Object>> resultList = new ArrayList<>();
		normalizeZeroToEmpty(paramMap, "account_id", "type", "year", "month", "payType");
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
			deleteReplacedReceiptImage(oldReceiptImage, paramMap.get("receipt_image"));
		}
		return iResult;
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

	// 회계 -> 본사 법인카드 결제내역 저장
	public int AccountCorporateCardPaymentSave(Map<String, Object> paramMap) {
		int iResult = 0;
		String oldReceiptImage = findAccountCorporateReceiptImage(paramMap);
		iResult = accountMapper.AccountCorporateCardPaymentSave(paramMap);
		if (iResult > 0) {
			deleteReplacedReceiptImage(oldReceiptImage, paramMap.get("receipt_image"));
		}
		return iResult;
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
		accountMapper.TallySheetPaymentSave(paramMap);
		// OUT 값 확인
		result = (int) paramMap.get("result");
		if (result != 1) {
			throw new RuntimeException("❌ sp_sync_corp_card_to_tally_sheet_one_day 프로시저 실패");
		}

		if (result > 0) {
			// ③ 손익표 합계 + 비율 저장 프로시저 호출
			paramMap.put("result", 0); // OUT 값 초기화
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
			operateMapper.BudgetTotalSave(paramMap);
		}

		// OUT 값 확인
		result = (int) paramMap.get("result");
		if (result != 1) {
			throw new RuntimeException("❌ BudgetTotalSave 프로시저 실패");
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
}
