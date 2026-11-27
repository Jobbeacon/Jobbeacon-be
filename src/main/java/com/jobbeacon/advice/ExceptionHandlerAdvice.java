package com.jobbeacon.advice;

import static com.jobbeacon.constant.JobBeaconConstants.CommonConstants.DEFAULT_LANGUAGE;
import static com.jobbeacon.constant.JobBeaconConstants.CommonConstants.LANGUAGE;
import static com.jobbeacon.constant.JobBeaconConstants.CommonConstants.PERCENT;

import com.jobbeacon.dto.response.Error;
import com.jobbeacon.dto.response.ResponseGeneral;
import com.jobbeacon.exception.base.BadRequestException;
import com.jobbeacon.exception.base.BaseException;
import com.jobbeacon.exception.base.UnauthorizedException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class ExceptionHandlerAdvice {
  private static final String GATEWAY_HEADER_PREFIX = "X-User-";

  private final MessageSource messageSource;

  @ExceptionHandler(value = {BaseException.class})
  public ResponseEntity<ResponseGeneral<Error>> handleBaseException(
      BaseException ex,
      WebRequest webRequest
  ) {
    return ResponseEntity
        .status(ex.getStatus())
        .body(getError(ex.getStatus(), ex.getCode(), webRequest.getLocale(), ex.getParams()));
  }

  @ExceptionHandler(MissingRequestHeaderException.class)
  public ResponseEntity<ResponseGeneral<Error>> handleMissingHeader(
      MissingRequestHeaderException ex,
      WebRequest webRequest
  ) {
    if (ex.getHeaderName().startsWith(GATEWAY_HEADER_PREFIX)) {
      return handleBaseException(new UnauthorizedException(), webRequest);
    }
    return handleBaseException(new BadRequestException(), webRequest);
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ResponseGeneral<Error>> handleValidationExceptions(
      MethodArgumentNotValidException exception,
      WebRequest webRequest
  ) {
    log.error("(handleValidationExceptions)exception: {}", exception.getMessage());
    String language = resolveLanguage(webRequest);

    String errorMessage = exception.getBindingResult().getFieldErrors().stream()
        .map(DefaultMessageSourceResolvable::getDefaultMessage)
        .filter(Objects::nonNull)
        .findFirst()
        .orElse(exception.getMessage());

    log.error("(handleValidationExceptions) {}", errorMessage);
    return ResponseEntity
        .status(HttpStatus.BAD_REQUEST)
        .body(getError(HttpStatus.BAD_REQUEST.value(), errorMessage, language));
  }

  @ExceptionHandler(ConstraintViolationException.class)
  public ResponseEntity<ResponseGeneral<Error>> handleConstraintViolationExceptions(
      ConstraintViolationException exception,
      WebRequest webRequest
  ) {
    log.error("(handleConstraintViolationExceptions) exception: {}", exception.getMessage());
    String language = resolveLanguage(webRequest);

    String errorMessage = exception.getConstraintViolations().stream()
        .map(ConstraintViolation::getMessage)
        .findFirst()
        .orElse(exception.getMessage());

    log.error("(handleConstraintViolationExceptions) {}", errorMessage);
    return ResponseEntity
        .status(HttpStatus.BAD_REQUEST)
        .body(getError(HttpStatus.BAD_REQUEST.value(), errorMessage, language));
  }

  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  public ResponseEntity<ResponseGeneral<Error>> handleTypeMismatchException(
      MethodArgumentTypeMismatchException exception,
      WebRequest webRequest
  ) {
    log.error("(handleTypeMismatchException) {}: {}", exception.getName(), exception.getValue());
    return ResponseEntity
        .status(HttpStatus.BAD_REQUEST)
        .body(getError(
            HttpStatus.BAD_REQUEST.value(),
            "com.jobbeacon.exception.base.BadRequestException",
            resolveLanguage(webRequest)
        ));
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<ResponseGeneral<Error>> handleNotReadableException(
      HttpMessageNotReadableException exception,
      WebRequest webRequest
  ) {
    log.error("(handleNotReadableException) {}", exception.getMessage());
    return ResponseEntity
        .status(HttpStatus.BAD_REQUEST)
        .body(getError(HttpStatus.BAD_REQUEST.value(), "invalid_request_body", resolveLanguage(webRequest)));
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ResponseGeneral<Error>> handleAllExceptions(
      Exception ex,
      WebRequest webRequest
  ) {
    log.error("Unhandled exception: {}", ex.getMessage(), ex);
    return ResponseEntity
        .status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(getError(
            HttpStatus.INTERNAL_SERVER_ERROR.value(),
            "internal_server_error",
            String.valueOf(webRequest.getLocale())
        ));
  }

  @ExceptionHandler(MaxUploadSizeExceededException.class)
  public ResponseEntity<ResponseGeneral<Error>> handleMaxSizeException(
      MaxUploadSizeExceededException ex,
      WebRequest webRequest
  ) {
    log.error("(handleMaxSizeException) {}", ex.getMessage());
    return ResponseEntity
        .status(HttpStatus.PAYLOAD_TOO_LARGE)
        .body(getError(
            HttpStatus.PAYLOAD_TOO_LARGE.value(),
            "file_size_exceeded",
            resolveLanguage(webRequest)
        ));
  }

  private ResponseGeneral<Error> getError(int status, String code, String language) {
    return ResponseGeneral.of(
        status,
        HttpStatus.valueOf(status).getReasonPhrase(),
        Error.of(code, getMessage(code, new Locale(language)))
    );
  }

  private ResponseGeneral<Error> getError(int status, String code, Locale locale, Map<String, String> params) {
    return ResponseGeneral.of(
        status,
        HttpStatus.valueOf(status).getReasonPhrase(),
        Error.of(code, getMessage(code, locale, params))
    );
  }

  private String getMessage(String code, Locale locale, Map<String, String> params) {
    var message = getMessage(code, locale);
    if (params != null && !params.isEmpty()) {
      for (var param : params.entrySet()) {
        message = message.replace(getMessageParamsKey(param.getKey()), param.getValue());
      }
    }
    return message;
  }

  private String getMessage(String code, Locale locale) {
    try {
      return messageSource.getMessage(code, null, locale);
    } catch (Exception ex) {
      return code;
    }
  }

  private String getMessageParamsKey(String key) {
    return PERCENT + key + PERCENT;
  }

  private String resolveLanguage(WebRequest webRequest) {
    String header = webRequest.getHeader(LANGUAGE);
    if (header == null || header.isBlank()) {
      return DEFAULT_LANGUAGE;
    }
    String primary = header.split(",")[0].trim().split(";")[0].trim();
    if (primary.isEmpty()) {
      return DEFAULT_LANGUAGE;
    }
    return primary.split("[-_]")[0].toLowerCase(Locale.ROOT);
  }
}
