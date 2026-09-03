package com.jobbeacon.exception;

import lombok.Getter;

@Getter
public class BusinessException extends RuntimeException {

  private final String errorCode;

  public BusinessException(final String errorCode, final String message) {
    super(message);
    this.errorCode = errorCode;
  }
}
