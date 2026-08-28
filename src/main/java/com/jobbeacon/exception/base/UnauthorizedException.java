package com.jobbeacon.exception.base;

import static com.jobbeacon.constant.JobBeaconConstants.MessageException.DEFAULT_CODE_UNAUTHORIZED;
import static com.jobbeacon.constant.JobBeaconConstants.StatusException.UNAUTHORIZED;

public class UnauthorizedException extends BaseException {

  public UnauthorizedException() {
    super(DEFAULT_CODE_UNAUTHORIZED, "Unauthorized", UNAUTHORIZED, null);
  }

  public UnauthorizedException(String code) {
    super(code, "", UNAUTHORIZED, null);
  }
}
