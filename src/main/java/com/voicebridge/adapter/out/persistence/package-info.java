/**
 * 영속성 아웃바운드 어댑터 (Persistence Adapter)
 *
 * <p>JPA Repository 등 DB 접근 구현체가 위치한다. port.out 인터페이스를 구현하며,
 * 여기서 발생하는 프레임워크 전용 예외(DataIntegrityViolationException 등)는 절대
 * application 계층으로 그대로 흘려보내지 말고 도메인 전용 예외({@code CustomException})로 번역해서 던진다.
 *
 * <p>낙관적 락({@code @Version})을 쓰는 엔티티는 도메인 ↔ 엔티티 매퍼를 통과할 때
 * 버전 값이 누락되지 않는지 항상 확인한다.
 */
package com.voicebridge.adapter.out.persistence;
