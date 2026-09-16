/**
 * 아웃바운드 포트 (Outbound Port)
 *
 * <p>영속성, 외부 시스템(스토리지, AI 추론 서버 등)에 대한 인터페이스를 정의한다. 구현체는 adapter.out 하위에 위치하며, 이 인터페이스만 지켜지면 구현 방식을
 * 자유롭게 교체할 수 있다.
 *
 * <p>예: {@code AiInferenceClient} 포트는 JPyRust in-process 구현과 FastAPI HTTP 구현 중 무엇으로 바뀌어도 이 인터페이스와
 * 그걸 호출하는 application 코드는 그대로 유지되어야 한다.
 */
package com.voicebridge.port.out;
