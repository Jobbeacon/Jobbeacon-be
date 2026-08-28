package com.jobbeacon.exception.base;

import static com.jobbeacon.constant.JobBeaconConstants.CommonConstants.BAD_REQUEST_MESSAGE;
import static com.jobbeacon.constant.JobBeaconConstants.CommonConstants.BLANK_MESSAGE;
import static com.jobbeacon.constant.JobBeaconConstants.MessageException.DEFAULT_CODE_BAD_REQUEST;
import static com.jobbeacon.constant.JobBeaconConstants.StatusException.BAD_REQUEST;

import java.util.HashMap;
import java.util.Map;

public class BadRequestException extends BaseException {

  public BadRequestException() {
    super(DEFAULT_CODE_BAD_REQUEST, BAD_REQUEST_MESSAGE, BAD_REQUEST, null);
  }

  public BadRequestException(String code) {
    super(code, BLANK_MESSAGE, BAD_REQUEST, null);
  }

  public BadRequestException(String code, String objectName) {
    super(code, BAD_REQUEST_MESSAGE, BAD_REQUEST, addParam(objectName));
  }

  private static Map<String, String> addParam(String objectName) {
    Map<String, String> params = new HashMap<>();
    params.put("objectName", objectName);
    return params;
  }
}
