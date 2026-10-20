package com.jobbeacon.exception;

import com.jobbeacon.exception.base.BaseException;
import java.util.Map;
import lombok.Getter;

@Getter
public class CvExtractionException extends BaseException {

  public static final String INVALID_FILE_TYPE = "cv_invalid_file_type";
  public static final String FILE_TOO_LARGE = "cv_file_too_large";
  public static final String TOO_MANY_PAGES = "cv_too_many_pages";
  public static final String PASSWORD_PROTECTED = "cv_password_protected";
  public static final String UNREADABLE = "cv_unreadable";
  public static final String MODEL_TIMEOUT = "cv_model_timeout";
  public static final String MODEL_ERROR = "cv_model_error";
  public static final String INSUFFICIENT_DATA = "cv_insufficient_data";

  private static final int STATUS_BAD_REQUEST = 400;
  private static final int STATUS_PAYLOAD_TOO_LARGE = 413;
  private static final int STATUS_UNPROCESSABLE = 422;
  private static final int STATUS_SERVICE_UNAVAILABLE = 503;
  private static final int STATUS_GATEWAY_TIMEOUT = 504;

  public enum ErrorKind {
    TRANSIENT,
    PERMANENT,
    DATA
  }

  private final ErrorKind kind;

  public CvExtractionException(String code, ErrorKind kind, int status) {
    this(code, kind, status, null, null);
  }

  public CvExtractionException(
      String code, ErrorKind kind, int status, Map<String, String> params, Throwable cause
  ) {
    super(code, code, status, params);
    this.kind = kind;
    if (cause != null) {
      initCause(cause);
    }
  }

  public boolean isRetryable() {
    return kind == ErrorKind.TRANSIENT;
  }

  public static CvExtractionException invalidFileType() {
    return new CvExtractionException(INVALID_FILE_TYPE, ErrorKind.PERMANENT, STATUS_BAD_REQUEST);
  }

  public static CvExtractionException fileTooLarge() {
    return new CvExtractionException(FILE_TOO_LARGE, ErrorKind.PERMANENT, STATUS_PAYLOAD_TOO_LARGE);
  }

  public static CvExtractionException tooManyPages() {
    return new CvExtractionException(TOO_MANY_PAGES, ErrorKind.PERMANENT, STATUS_BAD_REQUEST);
  }

  public static CvExtractionException passwordProtected() {
    return new CvExtractionException(PASSWORD_PROTECTED, ErrorKind.PERMANENT, STATUS_BAD_REQUEST);
  }

  public static CvExtractionException unreadable(Throwable cause) {
    return new CvExtractionException(UNREADABLE, ErrorKind.PERMANENT, STATUS_BAD_REQUEST, null, cause);
  }

  public static CvExtractionException modelTimeout(Throwable cause) {
    return new CvExtractionException(
        MODEL_TIMEOUT, ErrorKind.TRANSIENT, STATUS_GATEWAY_TIMEOUT, null, cause);
  }

  public static CvExtractionException modelError(Throwable cause) {
    return new CvExtractionException(
        MODEL_ERROR, ErrorKind.TRANSIENT, STATUS_SERVICE_UNAVAILABLE, null, cause);
  }

  public static CvExtractionException insufficientData() {
    return new CvExtractionException(INSUFFICIENT_DATA, ErrorKind.DATA, STATUS_UNPROCESSABLE);
  }
}
