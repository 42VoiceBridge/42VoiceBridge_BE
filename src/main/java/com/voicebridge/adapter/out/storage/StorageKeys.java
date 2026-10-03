package com.voicebridge.adapter.out.storage;

import java.util.Locale;
import java.util.Set;
import java.util.UUID;

// S3 어댑터와 로컬 어댑터가 같은 형식의 키를 만들어야 한다. 프로파일을 바꿨다고 Recording.s3Path의
// 형식이 달라지면 안 되므로 생성 규칙을 한 곳에 둔다.
final class StorageKeys {

  private static final String PREFIX = "recordings/";
  private static final Set<String> ALLOWED_EXTENSIONS = Set.of("wav", "mp3", "m4a", "webm", "ogg");
  private static final String DEFAULT_EXTENSION = "wav";

  private StorageKeys() {}

  static String newKey(String fileName) {
    return PREFIX + UUID.randomUUID() + "." + extensionOf(fileName);
  }

  // 원본 파일명은 사용자가 보낸 값이라 키에 그대로 쓰지 않는다(경로 조작·비정상 문자 차단).
  // 허용 목록에 있는 확장자만 추출해 쓰고 파일명 자체는 버린다.
  private static String extensionOf(String fileName) {
    if (fileName == null) {
      return DEFAULT_EXTENSION;
    }
    int dot = fileName.lastIndexOf('.');
    if (dot < 0 || dot == fileName.length() - 1) {
      return DEFAULT_EXTENSION;
    }
    String extension = fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    return ALLOWED_EXTENSIONS.contains(extension) ? extension : DEFAULT_EXTENSION;
  }
}
