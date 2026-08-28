package com.jobbeacon.service.base;

import java.util.Map;

public interface MessageService {
  String getMessage(String code, String language);

  String getMessage(String code, String language, Map<String, String> params);
}
