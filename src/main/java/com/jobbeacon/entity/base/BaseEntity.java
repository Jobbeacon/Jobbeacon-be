package com.jobbeacon.entity.base;

import com.jobbeacon.utils.UuidUtils;
import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import java.util.UUID;

@MappedSuperclass
public abstract class BaseEntity {

  @Id
  @Column(name = "id", updatable = false, nullable = false)
  private UUID id;

  protected BaseEntity() {
  }

  @PrePersist
  public void handlePrePersist() {
    if (this.id == null) {
      this.id = UuidUtils.v7();
    }
  }

  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    BaseEntity that = (BaseEntity) o;
    return id != null && id.equals(that.id);
  }

  @Override
  public int hashCode() {
    return getClass().hashCode();
  }
}
