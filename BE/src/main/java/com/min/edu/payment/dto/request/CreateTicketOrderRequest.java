package com.min.edu.payment.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import com.min.edu.payment.domain.PaymentMethod;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

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

    // 결제완료 퍼널 이벤트를 같은 세션에 이어붙이기 위한 분석용 값. 클라이언트가 함께 보낸다
    // (event-contract.md 참고). 없어도 주문 자체는 정상 처리된다.
    private String funnelSessionId;

    // 게스트 구매자의 익명 방문자 식별자. 클라이언트가 보내는 값이 아니라, 컨트롤러가 쿠키에서
    // 읽어 바인딩 이후에 채워 넣는다.
    @Setter
    private String anonymousId;

    public CreateTicketOrderRequest(Integer quantity, GuestBuyerRequest buyer) {
        this.quantity = quantity;
        this.buyer = buyer;
        this.paymentMethod = PaymentMethod.CARD;
    }
}
