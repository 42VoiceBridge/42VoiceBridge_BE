/**
 * 스토리지 아웃바운드 어댑터 (Storage Adapter)
 *
 * <p>S3 등 음성 파일 저장소 접근 구현체가 위치한다. 버킷명, 리전 등 환경설정 값은 이 클래스에서 직접 {@code System.getenv()}로 읽지 말고 생성자
 * 주입(Composition Root)으로 받는다.
 */
package com.voicebridge.adapter.out.storage;
