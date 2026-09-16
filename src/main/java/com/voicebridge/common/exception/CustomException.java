package com.voicebridge.common.exception;

import lombok.Getter;

/**
 * 도메인 검증 실패 등 비즈니스 규칙 위반을 표현하는 전용 예외.
 *
 * <p>Adapter 계층에서 발생하는 프레임워크 예외(DataIntegrityViolationException 등)는
 * 절대 그대로 던지지 말고, 이 예외로 번역해서 던진다.
 */
@Getter
public class CustomException extends RuntimeException {

    private final ErrorCode errorCode;

    public CustomException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public CustomException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
}
