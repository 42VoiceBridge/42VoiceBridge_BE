/**
 * AI 연동 아웃바운드 어댑터 (AI Inference Adapter)
 *
 * <p>port.out의 {@code AiInferenceClient} 구현체가 위치한다.
 * JPyRust vs FastAPI(HTTP) PoC 결과가 나오기 전까지는 스텁 구현체를 두고,
 * 결정 이후 실제 구현으로 교체한다. 어느 쪽으로 결정되든 port.out 인터페이스는 바뀌지 않는다.
 */
package com.voicebridge.adapter.out.ai;
