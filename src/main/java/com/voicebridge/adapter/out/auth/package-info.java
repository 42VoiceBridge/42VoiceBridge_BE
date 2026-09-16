/**
 * 인증 인프라 아웃바운드 어댑터 (Auth Infrastructure Adapter)
 *
 * <p>DB(영속성)도 파일 저장소도 아닌, 인증에 특화된 인프라 구현체가 위치한다.
 * JWT 발급/검증, 비밀번호 해싱, 카카오 사용자 정보 조회, 리프레시 토큰 저장이 여기 속하며
 * 모두 port.out의 인터페이스를 구현한다.
 */
package com.voicebridge.adapter.out.auth;
