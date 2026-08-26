package com.min.edu.payment.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;

import com.min.edu.payment.repository.PaymentOrderRepository;

class OrderNoGeneratorTest {

    @Test
    void generate_returnsOrderNumberWithSixteenHexCharacters() {
        PaymentOrderRepository paymentOrderRepository = mock(PaymentOrderRepository.class);
        given(paymentOrderRepository.existsByOrderNo(anyString())).willReturn(false);
        OrderNoGenerator orderNoGenerator = new OrderNoGenerator(paymentOrderRepository);

        String orderNo = orderNoGenerator.generate();

        assertThat(orderNo).matches("^EVT-\\d{8}-[A-F0-9]{16}$");
    }
}
