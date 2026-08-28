package com.jobbeacon.exception.base;

import static com.jobbeacon.constant.JobBeaconConstants.CommonConstants.BLANK_MESSAGE;
import static com.jobbeacon.constant.JobBeaconConstants.CommonConstants.CONFLICT_MESSAGE;
import static com.jobbeacon.constant.JobBeaconConstants.MessageException.DEFAULT_CODE_CONFLICT;
import static com.jobbeacon.constant.JobBeaconConstants.StatusException.CONFLICT;

import java.util.HashMap;
import java.util.Map;

public class ConflictException extends BaseException {

  public ConflictException(String code, String objectName) {
    super(code, CONFLICT_MESSAGE, CONFLICT, addParams(objectName));
  }

  public ConflictException(String code, String param1, String param2) {
    super(code, CONFLICT_MESSAGE, CONFLICT, createParams(param1, param2));
  }

  public ConflictException() {
    super(DEFAULT_CODE_CONFLICT, CONFLICT_MESSAGE, CONFLICT, null);
  }

  public ConflictException(String code) {
    super(code, BLANK_MESSAGE, CONFLICT, null);
  }

  private static Map<String, String> createParams(String param1, String param2) {
    Map<String, String> params = new HashMap<>();
    params.put("objectName1", param1 == null ? "" : param1);
    params.put("objectName2", param2 == null ? "" : param2);
    return params;
  }

  private static Map<String, String> addParams(String objectName) {
    Map<String, String> params = new HashMap<>();
    params.put("objectName", objectName == null ? "" : objectName);
    return params;
  }
}
