package com.smit.taskportal.api.dto;

/** Token + header/cookie names used by the vanilla-JS CSRF handling. */
public record CsrfDto(String token, String headerName, String parameterName) {
}
