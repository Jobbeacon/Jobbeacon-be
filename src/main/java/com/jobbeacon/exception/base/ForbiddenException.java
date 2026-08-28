package com.jobbeacon.exception.base;

import static com.jobbeacon.constant.JobBeaconConstants.MessageException.DEFAULT_CODE_FORBIDDEN;
import static com.jobbeacon.constant.JobBeaconConstants.StatusException.FORBIDDEN;

import java.util.HashMap;
import java.util.Map;

public class ForbiddenException extends BaseException {

  public ForbiddenException(String id, String objectName) {
    super(DEFAULT_CODE_FORBIDDEN, "Forbidden", FORBIDDEN, createParams(id, objectName));
  }

  public ForbiddenException() {
    super(DEFAULT_CODE_FORBIDDEN, "Forbidden", FORBIDDEN, null);
  }

  public ForbiddenException(String code) {
    super(code, "", FORBIDDEN, null);
  }

  private static Map<String, String> createParams(String id, String objectName) {
    Map<String, String> params = new HashMap<>();
    params.put("id", id);
    params.put("objectName", objectName);
    return params;
  }
}
