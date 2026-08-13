package com.min.edu.payment.toss;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.min.edu.payment.toss.dto.TossConfirmResponse;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class TossConfirmResponseTest {

    @Test
    void deserializeVirtualAccountDueDateWithoutTimezoneOffset() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());

        TossConfirmResponse response = objectMapper.readValue(
            """
            {
              "paymentKey": "payment-key",
              "orderId": "ORDER-1",
              "totalAmount": 10000,
              "status": "WAITING_FOR_DEPOSIT",
              "method": "VIRTUAL_ACCOUNT",
              "secret": "secret-value",
              "virtualAccount": {
                "accountNumber": "1234567890",
                "bankCode": "088",
                "customerName": "tester",
                "dueDate": "2026-08-03T10:30:00"
              },
              "requestedAt": "2026-08-03T10:00:00+09:00"
            }
            """,
            TossConfirmResponse.class
        );

        assertThat(response.virtualAccount().dueDate())
            .isEqualTo(LocalDateTime.parse("2026-08-03T10:30:00"));
    }

    @Test
    void toStringMasksVirtualAccountSensitiveFields() {
        TossConfirmResponse response = new TossConfirmResponse(
            "payment-key",
            "ORDER-1",
            java.math.BigDecimal.valueOf(10000),
            "WAITING_FOR_DEPOSIT",
            "VIRTUAL_ACCOUNT",
            "secret-value",
            new TossConfirmResponse.VirtualAccount(
                "1234567890",
                "088",
                "tester",
                LocalDateTime.parse("2026-08-03T10:30:00")
            ),
            null,
            null
        );

        assertThat(response.toString())
            .doesNotContain("secret-value")
            .doesNotContain("1234567890")
            .doesNotContain("tester")
            .contains("secret=***")
            .contains("virtualAccount=[MASKED]");
        assertThat(response.virtualAccount().toString())
            .doesNotContain("1234567890")
            .doesNotContain("tester");
    }
}
