package com.jobbeacon.service.base;

import java.util.List;
import java.util.UUID;

public interface BaseService<T> {
  T create(T t);

  T update(T t);

  void delete(UUID id);

  T get(UUID id);

  List<T> list();
}
