/**
 * 인바운드 웹 어댑터 (Inbound Web Adapter)
 *
 * <p>REST Controller가 위치한다. port.in의 유스케이스 인터페이스에만 의존하고,
 * 요청/응답은 반드시 DTO로 주고받는다(Entity 직접 반환 금지).
 * 응답 포맷은 {@link com.gsia.dysarthria.common.response.ApiResponse}를 사용한다
 * (API 명세서 0.3절 공통 응답 포맷과 동일).
 */
package com.gsia.dysarthria.adapter.in.web;
