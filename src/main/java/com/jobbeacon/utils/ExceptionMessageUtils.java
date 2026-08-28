package com.jobbeacon.utils;

import com.jobbeacon.exception.base.BaseException;
import java.util.Locale;
import java.util.Map;
import org.springframework.context.MessageSource;

public class ExceptionMessageUtils {

  private static final String FALLBACK_VI = "Thao tác thất bại";
  private static final String FALLBACK_EN = "The operation failed";

  public static class ReasonPair {
    public final String vi;
    public final String en;

    public ReasonPair(String vi, String en) {
      this.vi = vi;
      this.en = en;
    }
  }

  public static ReasonPair resolveExceptionMessages(MessageSource messageSource, Exception ex) {
    if (ex instanceof BaseException be) {
      String vi = resolveMessage(messageSource, be.getCode(), new Locale("vi"), be.getParams());
      String en = resolveMessage(messageSource, be.getCode(), new Locale("en"), be.getParams());
      return new ReasonPair(vi, en);
    }
    String vi = resolveMessage(messageSource, "internal_server_error", new Locale("vi"), null);
    String en = resolveMessage(messageSource, "internal_server_error", new Locale("en"), null);
    return new ReasonPair(vi, en);
  }

  public static String resolveMessage(
      MessageSource messageSource,
      String code,
      Locale locale,
      Map<String, String> params
  ) {
    String fallback = fallbackMessage(locale);
    String message;
    try {
      message = messageSource.getMessage(code, null, locale);
    } catch (Exception e) {
      message = fallback;
    }
    if (isUnresolvedExceptionCode(code, message)) {
      message = fallback;
    }
    if (params != null && !params.isEmpty()) {
      for (var entry : params.entrySet()) {
        message = message.replace("%" + entry.getKey() + "%", entry.getValue());
      }
    }
    return message;
  }

  private static String fallbackMessage(Locale locale) {
    return locale != null && "vi".equalsIgnoreCase(locale.getLanguage()) ? FALLBACK_VI : FALLBACK_EN;
  }

  private static boolean isUnresolvedExceptionCode(String code, String message) {
    return code != null && code.equals(message)
        && (code.contains(".exception.") || code.endsWith("Exception"));
  }
}
