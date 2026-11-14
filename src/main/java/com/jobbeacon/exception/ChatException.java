package com.jobbeacon.exception;

import com.jobbeacon.exception.base.BaseException;

public class ChatException extends BaseException {

  public static final String UNAVAILABLE = "chat_unavailable";

  private static final int STATUS_CONFLICT = 409;
  private static final int STATUS_SERVICE_UNAVAILABLE = 503;

  private ChatException(int status, Throwable cause) {
    super(UNAVAILABLE, UNAVAILABLE, status, null);
    if (cause != null) {
      initCause(cause);
    }
  }

  public static ChatException unavailable() {
    return new ChatException(STATUS_SERVICE_UNAVAILABLE, null);
  }

  public static ChatException unavailable(Throwable cause) {
    return new ChatException(STATUS_SERVICE_UNAVAILABLE, cause);
  }

  public static ChatException busy() {
    return new ChatException(STATUS_CONFLICT, null);
  }
}
