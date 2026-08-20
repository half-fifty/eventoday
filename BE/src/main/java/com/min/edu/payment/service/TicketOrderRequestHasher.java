package com.min.edu.payment.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

import org.springframework.stereotype.Component;

import com.min.edu.payment.domain.PaymentMethod;
import com.min.edu.payment.dto.request.CreateTicketOrderRequest;
import com.min.edu.payment.dto.request.GuestBuyerRequest;

@Component
public class TicketOrderRequestHasher {

    public String hash(Long eventId, Long buyerMemberId, CreateTicketOrderRequest request) {
        return sha256(canonicalize(eventId, buyerMemberId, request));
    }

    private String canonicalize(Long eventId, Long buyerMemberId, CreateTicketOrderRequest request) {
        StringBuilder builder = new StringBuilder();
        append(builder, "eventId", eventId);
        append(builder, "quantity", request.getQuantity());
        append(builder, "paymentMethod", selectedPaymentMethod(request).name());

        if (buyerMemberId != null) {
            append(builder, "buyerType", "member");
            append(builder, "memberId", buyerMemberId);
            return builder.toString();
        }

        GuestBuyerRequest buyer = request.getBuyer();
        append(builder, "buyerType", "guest");
        append(builder, "guestName", normalize(buyer == null ? null : buyer.getName()));
        append(builder, "guestEmail", normalizeEmail(buyer == null ? null : buyer.getEmail()));
        append(builder, "guestPhone", normalize(buyer == null ? null : buyer.getPhone()));
        return builder.toString();
    }

    private PaymentMethod selectedPaymentMethod(CreateTicketOrderRequest request) {
        return request.getPaymentMethod() == null
            ? PaymentMethod.CARD
            : request.getPaymentMethod();
    }

    private void append(StringBuilder builder, String key, Object value) {
        builder.append(key)
            .append('=')
            .append(value == null ? "" : value)
            .append('\n');
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private String normalizeEmail(String value) {
        return normalize(value).toLowerCase(Locale.ROOT);
    }

    private String sha256(String canonicalInput) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(canonicalInput.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available.", exception);
        }
    }
}
