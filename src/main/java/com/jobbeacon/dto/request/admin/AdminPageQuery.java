package com.jobbeacon.dto.request.admin;

/** 1-based page with a bounded size, as used by every admin list endpoint. */
public record AdminPageQuery(int page, int size) {

  public static final int DEFAULT_SIZE = 20;
  public static final int MAX_SIZE = 100;

  public static AdminPageQuery of(Integer page, Integer size) {
    int safePage = page == null || page < 1 ? 1 : page;
    int safeSize = size == null || size < 1 ? DEFAULT_SIZE : Math.min(size, MAX_SIZE);
    return new AdminPageQuery(safePage, safeSize);
  }

  public int offset() {
    return (page - 1) * size;
  }
}
