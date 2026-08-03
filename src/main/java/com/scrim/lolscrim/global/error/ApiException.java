package com.scrim.lolscrim.global.error;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class ApiException extends RuntimeException {

	private final HttpStatus status;
	private final ErrorCode code;

	public ApiException(HttpStatus status, String message) {
		super(message);
		this.status = status;
		this.code = ErrorCode.from(status);
	}

	public ApiException(ErrorCode code, String message) {
		super(message);
		this.status = code.getStatus();
		this.code = code;
	}
}
