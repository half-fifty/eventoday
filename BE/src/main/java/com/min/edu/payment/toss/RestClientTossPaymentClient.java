package com.min.edu.payment.toss;

import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.UUID;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.min.edu.common.exception.GlobalErrorCode;
import com.min.edu.payment.toss.dto.TossConfirmRequest;
import com.min.edu.payment.toss.dto.TossConfirmResponse;
import com.min.edu.payment.toss.dto.TossCancelRequest;
import com.min.edu.payment.toss.dto.TossCancelResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public class RestClientTossPaymentClient implements TossPaymentClient {

    private static final String CONFIRM_PATH = "/v1/payments/confirm";
    private static final String PAYMENT_PATH = "/v1/payments/{paymentKey}";
    private static final String PAYMENT_BY_ORDER_PATH = "/v1/payments/orders/{orderId}";
    private static final String CANCEL_PATH = "/v1/payments/{paymentKey}/cancel";
    private static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";

    private final RestClient confirmRestClient;
    private final RestClient webhookRestClient;
    private final TossPaymentProperties properties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public RestClientTossPaymentClient(TossPaymentProperties properties) {
        this.properties = properties;
        this.confirmRestClient = RestClient.builder()
            .baseUrl(properties.getBaseUrl())
            .requestFactory(requestFactory(
                properties.getConnectTimeoutMs(),
                properties.getReadTimeoutMs()
            ))
            .build();
        this.webhookRestClient = RestClient.builder()
            .baseUrl(properties.getBaseUrl())
            .requestFactory(requestFactory(
                properties.getWebhookConnectTimeoutMs(),
                properties.getWebhookReadTimeoutMs()
            ))
            .build();
    }

    @Override
    public TossConfirmResponse confirm(TossConfirmRequest request) {
        return execute(() -> confirmRestClient.post()
            .uri(CONFIRM_PATH)
            .header(HttpHeaders.AUTHORIZATION, authorizationHeader())
            .header(IDEMPOTENCY_KEY_HEADER, idempotencyKey(request))
            .body(request)
            .retrieve()
            .onStatus(HttpStatusCode::is4xxClientError,
                (httpRequest, clientResponse) -> {
                    String tossErrorCode = extractTossErrorCode(clientResponse);
                    throw new TossPaymentClientException(
                        GlobalErrorCode.PAYMENT_CONFIRM_REJECTED,
                        tossErrorCode
                    );
                })
            .onStatus(HttpStatusCode::is5xxServerError,
                (httpRequest, clientResponse) -> {
                    throw new TossPaymentClientException(
                        GlobalErrorCode.PAYMENT_GATEWAY_ERROR
                    );
                })
            .body(TossConfirmResponse.class));
    }

    @Override
    public TossConfirmResponse getPayment(String paymentKey) {
        return execute(() -> webhookRestClient.get()
            .uri(PAYMENT_PATH, paymentKey)
            .header(HttpHeaders.AUTHORIZATION, authorizationHeader())
            .retrieve()
            .onStatus(HttpStatusCode::is4xxClientError,
                (httpRequest, clientResponse) -> {
                    throw new TossPaymentClientException(
                        GlobalErrorCode.PAYMENT_GATEWAY_RESPONSE_INVALID
                    );
                })
            .onStatus(HttpStatusCode::is5xxServerError,
                (httpRequest, clientResponse) -> {
                    throw new TossPaymentClientException(
                        GlobalErrorCode.PAYMENT_GATEWAY_ERROR
                    );
                })
            .body(TossConfirmResponse.class));
    }

    @Override
    public TossConfirmResponse getPaymentByOrderId(String orderId) {
        return execute(() -> webhookRestClient.get()
            .uri(PAYMENT_BY_ORDER_PATH, orderId)
            .header(HttpHeaders.AUTHORIZATION, authorizationHeader())
            .retrieve()
            .onStatus(HttpStatusCode::is4xxClientError,
                (httpRequest, clientResponse) -> {
                    throw new TossPaymentClientException(
                        GlobalErrorCode.PAYMENT_GATEWAY_RESPONSE_INVALID
                    );
                })
            .onStatus(HttpStatusCode::is5xxServerError,
                (httpRequest, clientResponse) -> {
                    throw new TossPaymentClientException(
                        GlobalErrorCode.PAYMENT_GATEWAY_ERROR
                    );
                })
            .body(TossConfirmResponse.class));
    }

    @Override
    public TossCancelResponse getPaymentForRefund(String paymentKey) {
        return executeCancel(() -> webhookRestClient.get()
            .uri(PAYMENT_PATH, paymentKey)
            .header(HttpHeaders.AUTHORIZATION, authorizationHeader())
            .retrieve()
            .onStatus(HttpStatusCode::is4xxClientError,
                (httpRequest, clientResponse) -> {
                    throw new TossPaymentClientException(
                        GlobalErrorCode.PAYMENT_GATEWAY_RESPONSE_INVALID
                    );
                })
            .onStatus(HttpStatusCode::is5xxServerError,
                (httpRequest, clientResponse) -> {
                    throw new TossPaymentClientException(
                        GlobalErrorCode.PAYMENT_GATEWAY_ERROR
                    );
                })
            .body(TossCancelResponse.class));
    }

    @Override
    public TossCancelResponse cancel(TossCancelRequest request) {
        return executeCancel(() -> confirmRestClient.post()
            .uri(CANCEL_PATH, request.paymentKey())
            .header(HttpHeaders.AUTHORIZATION, authorizationHeader())
            .header(IDEMPOTENCY_KEY_HEADER, refundIdempotencyKey(request))
            .body(request)
            .retrieve()
            .onStatus(HttpStatusCode::is4xxClientError,
                (httpRequest, clientResponse) -> {
                    throw new TossPaymentClientException(
                        GlobalErrorCode.REFUND_REJECTED,
                        extractTossErrorCode(clientResponse)
                    );
                })
            .onStatus(HttpStatusCode::is5xxServerError,
                (httpRequest, clientResponse) -> {
                    throw new TossPaymentClientException(
                        GlobalErrorCode.PAYMENT_GATEWAY_ERROR
                    );
                })
            .body(TossCancelResponse.class));
    }

    private TossConfirmResponse execute(TossRequest tossRequest) {
        try {
            TossConfirmResponse response = tossRequest.execute();

            if (response == null) {
                throw new TossPaymentClientException(
                    GlobalErrorCode.PAYMENT_GATEWAY_RESPONSE_INVALID
                );
            }

            return response;
        } catch (TossPaymentClientException exception) {
            throw exception;
        } catch (ResourceAccessException exception) {
            if (isTimeout(exception)) {
                throw new TossPaymentClientException(
                    GlobalErrorCode.PAYMENT_GATEWAY_TIMEOUT
                );
            }

            throw new TossPaymentClientException(GlobalErrorCode.PAYMENT_GATEWAY_ERROR);
        } catch (RestClientException exception) {
            if (isTimeout(exception)) {
                throw new TossPaymentClientException(
                    GlobalErrorCode.PAYMENT_GATEWAY_TIMEOUT
                );
            }

            throw new TossPaymentClientException(
                GlobalErrorCode.PAYMENT_GATEWAY_RESPONSE_INVALID
            );
        }
    }

    private TossCancelResponse executeCancel(TossCancelRequestExecutor tossRequest) {
        try {
            TossCancelResponse response = tossRequest.execute();

            if (response == null) {
                throw new TossPaymentClientException(
                    GlobalErrorCode.PAYMENT_GATEWAY_RESPONSE_INVALID
                );
            }

            return response;
        } catch (TossPaymentClientException exception) {
            throw exception;
        } catch (ResourceAccessException exception) {
            if (isTimeout(exception)) {
                throw new TossPaymentClientException(
                    GlobalErrorCode.PAYMENT_GATEWAY_TIMEOUT
                );
            }

            throw new TossPaymentClientException(GlobalErrorCode.PAYMENT_GATEWAY_ERROR);
        } catch (RestClientException exception) {
            if (isTimeout(exception)) {
                throw new TossPaymentClientException(
                    GlobalErrorCode.PAYMENT_GATEWAY_TIMEOUT
                );
            }

            throw new TossPaymentClientException(
                GlobalErrorCode.PAYMENT_GATEWAY_RESPONSE_INVALID
            );
        }
    }

    @FunctionalInterface
    private interface TossRequest {

        TossConfirmResponse execute();
    }

    @FunctionalInterface
    private interface TossCancelRequestExecutor {

        TossCancelResponse execute();
    }

    private String authorizationHeader() {
        String credential = properties.getSecretKey() + ":";
        String encoded = Base64.getEncoder()
            .encodeToString(credential.getBytes(StandardCharsets.UTF_8));

        return "Basic " + encoded;
    }

    String idempotencyKey(TossConfirmRequest request) {
        String source = "payment-confirm:"
            + request.orderId()
            + ":"
            + request.paymentKey();
        return UUID.nameUUIDFromBytes(source.getBytes(StandardCharsets.UTF_8))
            .toString();
    }

    String refundIdempotencyKey(TossCancelRequest request) {
        String source = "payment-refund:"
            + request.paymentKey()
            + ":"
            + request.cancelAmount();
        return UUID.nameUUIDFromBytes(source.getBytes(StandardCharsets.UTF_8))
            .toString();
    }

    private String extractTossErrorCode(org.springframework.http.client.ClientHttpResponse response) {
        try {
            JsonNode root = objectMapper.readTree(response.getBody());
            JsonNode code = root.get("code");
            if (code == null || !code.isTextual()) {
                return null;
            }

            return code.asText();
        } catch (Exception exception) {
            return null;
        }
    }

    private boolean isTimeout(Throwable throwable) {
        Throwable current = throwable;

        while (current != null) {
            if (current instanceof SocketTimeoutException) {
                return true;
            }

            current = current.getCause();
        }

        return false;
    }

    private SimpleClientHttpRequestFactory requestFactory(
            long connectTimeoutMs,
            long readTimeoutMs) {
        SimpleClientHttpRequestFactory requestFactory =
            new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofMillis(connectTimeoutMs));
        requestFactory.setReadTimeout(Duration.ofMillis(readTimeoutMs));
        return requestFactory;
    }
}
