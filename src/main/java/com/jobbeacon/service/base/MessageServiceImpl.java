package com.jobbeacon.service.base;

import static com.jobbeacon.constant.JobBeaconConstants.CommonConstants.PERCENT;

import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;

@RequiredArgsConstructor
public class MessageServiceImpl implements MessageService {
  private final MessageSource messageSource;

  @Override
  public String getMessage(String code, String language) {
    return getMessage(code, language, null);
  }

  @Override
  public String getMessage(String code, String language, Map<String, String> params) {
    try {
      String message = messageSource.getMessage(code, null, new Locale(language));
      if (params != null && !params.isEmpty()) {
        for (var param : params.entrySet()) {
          message = message.replace(PERCENT + param.getKey() + PERCENT, param.getValue());
        }
      }
      return message;
    } catch (Exception ex) {
      return code;
    }
  }
}
