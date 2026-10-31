package com.voicebridge.adapter.in.web.dto;

// 비밀번호 규칙(길이 등)은 도메인(User)에만 둔다.
public record ChangePasswordRequest(String currentPassword, String newPassword) {}
