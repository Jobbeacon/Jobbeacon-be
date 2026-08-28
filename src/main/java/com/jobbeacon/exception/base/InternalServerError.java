package com.jobbeacon.exception.base;

import static com.jobbeacon.constant.JobBeaconConstants.CommonConstants.BLANK_MESSAGE;
import static com.jobbeacon.constant.JobBeaconConstants.MessageException.DEFAULT_CODE_SERVER_ERROR;

import java.util.Map;

public class InternalServerError extends BaseException {

  public InternalServerError(String code, String message, Map<String, String> params) {
    super(code, message, StatusConstants.SERVER_ERROR, params);
  }

  public InternalServerError() {
    super(DEFAULT_CODE_SERVER_ERROR, BLANK_MESSAGE, StatusConstants.SERVER_ERROR, null);
  }

  public InternalServerError(String message) {
    super(DEFAULT_CODE_SERVER_ERROR, message, StatusConstants.SERVER_ERROR, null);
  }
}
