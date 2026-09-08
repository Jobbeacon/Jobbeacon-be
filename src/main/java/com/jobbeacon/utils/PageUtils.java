package com.jobbeacon.utils;

import com.jobbeacon.dto.request.PageRequestDto;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;


public class PageUtils {

    public static Pageable buildPageable(PageRequestDto reqDto, String... sortFields) {
        int page = Math.max(reqDto.getPage() - 1, 0);
        int size = 10;
        if (reqDto.getSize() > 0) {
            size = reqDto.getSize();
        }
        Sort.Direction direction = "desc".equalsIgnoreCase(reqDto.getSortType())
                ? Sort.Direction.DESC
                : Sort.Direction.ASC;

        Sort sort = Sort.unsorted();

        if (reqDto.getSortBy() != null && !reqDto.getSortBy().isBlank()) {
            sort = Sort.by(direction, reqDto.getSortBy());

        } else if (sortFields != null && sortFields.length > 0) {
            sort = Sort.by(direction, sortFields);
        }

        return PageRequest.of(page, size, sort);
    }
}
