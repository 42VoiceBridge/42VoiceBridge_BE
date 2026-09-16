/**
 * 애플리케이션 계층 (Application Layer)
 *
 * <p>port.in에 정의된 유스케이스 인터페이스의 구현체가 위치한다.
 * 여러 도메인 객체와 outbound port를 조합해 유스케이스를 실행하지만,
 * 비즈니스 규칙 자체는 소유하지 않는다(SSOT는 domain에만 있음) — 이 계층은 "조합"만 담당한다.
 *
 * <p>Create/Update 유스케이스처럼 비슷해 보이는 것도 섣불리 공통화하지 말고 각각 독립적으로 둔다(YAGNI).
 */
package com.voicebridge.application;
