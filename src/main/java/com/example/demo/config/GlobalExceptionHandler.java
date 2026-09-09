package com.example.demo.config;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	// 정적 리소스 미존재(이미지·파일 404)는 500 대신 404로 반환
	@ExceptionHandler(NoResourceFoundException.class)
	public ResponseEntity<Void> handleNoResourceFound(NoResourceFoundException e) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
	}

	// 서비스단에서 사용자에게 그대로 보여줄 의도로 던진 검증 오류(예: 삭제 불가 사유)는
	// 아래 전역 Exception 핸들러가 "서버 오류가 발생했습니다."로 뭉개기 전에 메시지를 그대로 내려준다.
	@ExceptionHandler(IllegalStateException.class)
	public ResponseEntity<String> handleIllegalState(IllegalStateException e) {
		return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
	}

    @ExceptionHandler(Exception.class)
    public ResponseEntity<String> handleException(Exception e) {
        log.error("🚨 전역 예외 발생: {}", e.getMessage(), e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                             .body("서버 오류가 발생했습니다.");
    }
}
