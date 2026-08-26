package com.min.edu.payment.toss;

import com.min.edu.payment.toss.dto.TossConfirmRequest;
import com.min.edu.payment.toss.dto.TossConfirmResponse;
import com.min.edu.payment.toss.dto.TossCancelRequest;
import com.min.edu.payment.toss.dto.TossCancelResponse;

public interface TossPaymentClient {

    TossConfirmResponse confirm(TossConfirmRequest request);

    TossConfirmResponse getPayment(String paymentKey);

    default TossConfirmResponse getPaymentByOrderId(String orderId) {
        throw new UnsupportedOperationException("Payment lookup by orderId is not implemented.");
    }

    TossCancelResponse getPaymentForRefund(String paymentKey);

    TossCancelResponse cancel(TossCancelRequest request);
}
