/**
 * 인바운드 포트 (Inbound Port)
 *
 * <p>유스케이스 인터페이스를 정의한다. adapter.in.web의 Controller는 이 인터페이스에만 의존하고,
 * 실제 구현은 application 계층에 둔다.
 *
 * <p>헥사고날 대칭성 원칙: outbound port만 만들고 이 계층을 빼먹지 않는다 — 그래야 도메인이
 * 특정 진입 방식(REST 등)에 은근히 의존하지 않는다.
 */
package com.voicebridge.port.in;
