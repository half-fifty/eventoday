package com.min.edu.payment.dto.response;

import java.util.List;

import org.springframework.data.domain.Page;

import com.min.edu.payment.repository.RefundListProjection;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class MyRefundListResponse {

    private List<RefundListItemResponse> content;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;
    private boolean first;
    private boolean last;
    private boolean empty;

    public static MyRefundListResponse from(Page<RefundListProjection> page) {
        return new MyRefundListResponse(
            page.getContent().stream()
                .map(RefundListItemResponse::from)
                .toList(),
            page.getNumber(),
            page.getSize(),
            page.getTotalElements(),
            page.getTotalPages(),
            page.isFirst(),
            page.isLast(),
            page.isEmpty()
        );
    }
}
