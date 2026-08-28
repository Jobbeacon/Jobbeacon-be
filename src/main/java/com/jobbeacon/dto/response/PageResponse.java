package com.jobbeacon.dto.response;

import java.util.List;
import java.util.Objects;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor(staticName = "of")
@NoArgsConstructor
public class PageResponse<T> {
  private List<T> content;
  private int amount;

  public static <T> PageResponse<T> of(List<T> data, Integer amount) {
    return new PageResponse<>(data, Objects.isNull(amount) ? 0 : amount);
  }
}
