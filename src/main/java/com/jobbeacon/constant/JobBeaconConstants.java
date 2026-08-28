package com.jobbeacon.constant;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

public final class JobBeaconConstants {

  private JobBeaconConstants() {
  }

  @NoArgsConstructor(access = AccessLevel.PRIVATE)
  public static class CommonConstants {
    public static final String ENCODING_UTF_8 = "UTF-8";
    public static final String LANGUAGE = "Accept-Language";
    public static final String DEFAULT_LANGUAGE = "vi";
    public static final String PERCENT = "%";
    public static final String MESSAGE_SOURCE = "classpath:i18n/messages";
    public static final String NOT_FOUND_MESSAGE = "Not found";
    public static final String BAD_REQUEST_MESSAGE = "Bad request";
    public static final String CONFLICT_MESSAGE = "Conflict occurred";
    public static final String BLANK_MESSAGE = "";
    public static final String SUCCESS = "success";
  }

  @NoArgsConstructor(access = AccessLevel.PRIVATE)
  public static class StatusException {
    public static final Integer NOT_FOUND = 404;
    public static final Integer CONFLICT = 409;
    public static final Integer BAD_REQUEST = 400;
    public static final Integer UNAUTHORIZED = 401;
    public static final Integer FORBIDDEN = 403;
    public static final Integer SERVER_ERROR = 500;
  }

  @NoArgsConstructor(access = AccessLevel.PRIVATE)
  public static class MessageException {
    public static final String DEFAULT_CODE_BAD_REQUEST = "com.jobbeacon.exception.base.BadRequestException";
    public static final String DEFAULT_CODE_CONFLICT = "com.jobbeacon.exception.base.ConflictException";
    public static final String DEFAULT_CODE_NOTFOUND = "com.jobbeacon.exception.base.NotFoundException";
    public static final String DEFAULT_CODE_SERVER_ERROR = "com.jobbeacon.exception.base.InternalServerError";
    public static final String DEFAULT_CODE_UNAUTHORIZED = "com.jobbeacon.exception.base.UnauthorizedException";
    public static final String DEFAULT_CODE_FORBIDDEN = "com.jobbeacon.exception.base.ForbiddenException";
  }
}
