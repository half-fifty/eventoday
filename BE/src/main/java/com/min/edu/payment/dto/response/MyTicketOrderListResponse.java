package com.min.edu.payment.dto.response;

import java.util.List;

import org.springframework.data.domain.Page;

import com.min.edu.payment.repository.TicketOrderListProjection;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class MyTicketOrderListResponse {

    private List<TicketOrderListItemResponse> content;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;
    private boolean first;
    private boolean last;
    private boolean empty;

    public static MyTicketOrderListResponse from(Page<TicketOrderListProjection> orders) {
        return new MyTicketOrderListResponse(
            orders.getContent().stream()
                .map(TicketOrderListItemResponse::from)
                .toList(),
            orders.getNumber(),
            orders.getSize(),
            orders.getTotalElements(),
            orders.getTotalPages(),
            orders.isFirst(),
            orders.isLast(),
            orders.isEmpty()
        );
    }
}
