package com.voicebridge.adapter.in.web.dto;

// 이메일 회원만 password가 필요하다. 카카오 회원은 본문 없이 보낸다.
public record WithdrawRequest(String password) {}
