/**
 * 도메인 계층 (Domain Layer)
 *
 * <p>Spring, JPA 등 어떠한 프레임워크에도 의존하지 않는 순수 자바 코드만 위치한다. 기본값·검증 규칙 같은 비즈니스 정책은 반드시 이 계층이 단독으로
 * 소유한다(SSOT). DTO에 같은 기본값을 중복해서 넣지 않는다.
 *
 * <p>상태 변경 로직은 Service가 아니라 도메인 엔티티가 직접 담당한다(Rich Domain Model). 예: {@code
 * recording.markProcessed(result)} 형태로 도메인 메서드를 호출하고, application 계층은 그걸 조합만 한다.
 *
 * <p>새 기능을 시작할 때는 이 패키지 아래에 하위 패키지를 만든다. 예: {@code domain.user}, {@code domain.diagnosis}, {@code
 * domain.personalization}
 */
package com.voicebridge.domain;
