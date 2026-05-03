package com.diepau1312.financeTrackerBE.dto.common;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PageResponse<T> {
  private java.util.List<T> content;
  private int page;           // trang hiện tại (0-based)
  private int size;           // size mỗi trang
  private long totalElements;  // tổng số items
  private int totalPages;     // tổng số trang
  private boolean first;       // có phải trang đầu không
  private boolean last;        // có phải trang cuối không
  private boolean empty;       // trang có rỗng không

  public static <T> PageResponse<T> from(org.springframework.data.domain.Page<T> page) {
    return PageResponse.<T>builder()
        .content(page.getContent())
        .page(page.getNumber())
        .size(page.getSize())
        .totalElements(page.getTotalElements())
        .totalPages(page.getTotalPages())
        .first(page.isFirst())
        .last(page.isLast())
        .empty(page.isEmpty())
        .build();
  }
}