package com.scrim.lolscrim.global.error;

public record ErrorResponse(int status, String code, String message) {
}
