package com.jobbeacon.exception.base;

import static com.jobbeacon.constant.JobBeaconConstants.CommonConstants.BLANK_MESSAGE;
import static com.jobbeacon.constant.JobBeaconConstants.CommonConstants.NOT_FOUND_MESSAGE;
import static com.jobbeacon.constant.JobBeaconConstants.MessageException.DEFAULT_CODE_NOTFOUND;
import static com.jobbeacon.constant.JobBeaconConstants.StatusException.NOT_FOUND;

import java.util.HashMap;
import java.util.Map;

public class NotFoundException extends BaseException {

  public NotFoundException(String id, String objectName) {
    super(id, NOT_FOUND_MESSAGE, NOT_FOUND, addParams(objectName));
  }

  public NotFoundException() {
    super(DEFAULT_CODE_NOTFOUND, NOT_FOUND_MESSAGE, NOT_FOUND, null);
  }

  public NotFoundException(String code) {
    super(code, BLANK_MESSAGE, NOT_FOUND, null);
  }

  public NotFoundException(String code, Map<String, String> params) {
    super(code, BLANK_MESSAGE, NOT_FOUND, params);
  }

  public NotFoundException(String id, String objectName, int status) {
    super(id, NOT_FOUND_MESSAGE, status, addParams(objectName));
  }

  private static Map<String, String> addParams(String objectName) {
    Map<String, String> params = new HashMap<>();
    params.put("objectName", objectName);
    return params;
  }
}
