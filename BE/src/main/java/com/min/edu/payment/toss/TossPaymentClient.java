package com.min.edu.payment.toss;

import com.min.edu.payment.toss.dto.TossConfirmRequest;
import com.min.edu.payment.toss.dto.TossConfirmResponse;

public interface TossPaymentClient {

    TossConfirmResponse confirm(TossConfirmRequest request);

    TossConfirmResponse getPayment(String paymentKey);
}
