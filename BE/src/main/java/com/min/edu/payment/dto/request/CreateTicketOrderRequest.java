package com.min.edu.payment.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import com.min.edu.payment.domain.PaymentMethod;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class CreateTicketOrderRequest {

    @NotNull(message = "티켓 수량은 필수입니다.")
    @Min(value = 1, message = "티켓 수량은 1 이상이어야 합니다.")
    private Integer quantity;

    @Valid
    private GuestBuyerRequest buyer;

    private PaymentMethod paymentMethod = PaymentMethod.CARD;

    public CreateTicketOrderRequest(Integer quantity, GuestBuyerRequest buyer) {
        this.quantity = quantity;
        this.buyer = buyer;
        this.paymentMethod = PaymentMethod.CARD;
    }
}
