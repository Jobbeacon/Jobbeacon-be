package com.jobbeacon.service.base;

import com.jobbeacon.repository.BaseRepository;
import java.util.List;
import java.util.UUID;

public class BaseServiceImpl<T> implements BaseService<T> {
  private final BaseRepository<T> repository;

  public BaseServiceImpl(BaseRepository<T> repository) {
    this.repository = repository;
  }

  @Override
  public T create(T t) {
    return repository.save(t);
  }

  @Override
  public T update(T t) {
    return repository.save(t);
  }

  @Override
  public void delete(UUID id) {
    repository.deleteById(id);
  }

  @Override
  public T get(UUID id) {
    return repository.findById(id).orElse(null);
  }

  @Override
  public List<T> list() {
    return repository.findAll();
  }
}
