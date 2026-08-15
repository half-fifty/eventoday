package com.min.edu.payment.toss.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.io.IOException;

public record TossConfirmResponse(
        String paymentKey,
        String orderId,
        BigDecimal totalAmount,
        String status,
        String method,
        String secret,
        VirtualAccount virtualAccount,
        OffsetDateTime requestedAt,
        OffsetDateTime approvedAt
) {

    public TossConfirmResponse(
            String paymentKey,
            String orderId,
            BigDecimal totalAmount,
            String status,
            String method,
            OffsetDateTime requestedAt,
            OffsetDateTime approvedAt) {
        this(
            paymentKey,
            orderId,
            totalAmount,
            status,
            method,
            null,
            null,
            requestedAt,
            approvedAt
        );
    }

    public record VirtualAccount(
            String accountNumber,
            String bankCode,
            String customerName,
            @JsonDeserialize(using = TossVirtualAccountDueDateDeserializer.class)
            OffsetDateTime dueDate
    ) {

        @Override
        public String toString() {
            return "VirtualAccount[accountNumber=***, bankCode="
                + bankCode
                + ", customerName=***, dueDate="
                + dueDate
                + "]";
        }
    }

    public static class TossVirtualAccountDueDateDeserializer
            extends JsonDeserializer<OffsetDateTime> {

        private static final ZoneId TOSS_VIRTUAL_ACCOUNT_ZONE =
            ZoneId.of("Asia/Seoul");

        @Override
        public OffsetDateTime deserialize(
                JsonParser parser,
                DeserializationContext context) throws IOException {
            String value = parser.getValueAsString();
            if (value == null || value.isBlank()) {
                return null;
            }

            try {
                return OffsetDateTime.parse(value);
            } catch (java.time.format.DateTimeParseException exception) {
                return LocalDateTime.parse(value)
                    .atZone(TOSS_VIRTUAL_ACCOUNT_ZONE)
                    .toOffsetDateTime();
            }
        }
    }

    @Override
    public String toString() {
        return "TossConfirmResponse[paymentKey="
            + paymentKey
            + ", orderId="
            + orderId
            + ", totalAmount="
            + totalAmount
            + ", status="
            + status
            + ", method="
            + method
            + ", secret=***, virtualAccount="
            + (virtualAccount == null ? null : "[MASKED]")
            + ", requestedAt="
            + requestedAt
            + ", approvedAt="
            + approvedAt
            + "]";
    }
}
