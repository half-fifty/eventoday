package com.min.edu.payment.toss;

import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.converter.HttpMessageConversionException;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import com.min.edu.common.exception.GlobalErrorCode;
import com.min.edu.payment.toss.dto.TossConfirmRequest;
import com.min.edu.payment.toss.dto.TossConfirmResponse;
import com.min.edu.payment.toss.dto.TossCancelRequest;
import com.min.edu.payment.toss.dto.TossCancelResponse;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class RestClientTossPaymentClient implements TossPaymentClient {

    private static final String CONFIRM_PATH = "/v1/payments/confirm";
    private static final String PAYMENT_PATH = "/v1/payments/{paymentKey}";
    private static final String PAYMENT_BY_ORDER_PATH = "/v1/payments/orders/{orderId}";
    private static final String CANCEL_PATH = "/v1/payments/{paymentKey}/cancel";
    private static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";

    private final RestClient confirmRestClient;
    private final RestClient webhookRestClient;
    private final TossPaymentProperties properties;
    private final ObjectMapper objectMapper = new ObjectMapper()
        .registerModule(new JavaTimeModule())
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

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
        return execute("TOSS_CONFIRM", () -> deserializeTossConfirmResponse(
            "TOSS_CONFIRM",
            confirmRestClient.post()
                .uri(CONFIRM_PATH)
                .header(HttpHeaders.AUTHORIZATION, authorizationHeader())
                .header(IDEMPOTENCY_KEY_HEADER, idempotencyKey(request))
                .body(request)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError,
                    (httpRequest, clientResponse) -> {
                        String tossErrorCode = extractTossErrorCode(clientResponse);
                        logTossClientFailure(
                            "TOSS_CONFIRM",
                            "TOSS_CONFIRM_HTTP_ERROR",
                            "TossPaymentClientException",
                            null,
                            null,
                            tossErrorCode
                        );
                        throw new TossPaymentClientException(
                            GlobalErrorCode.PAYMENT_CONFIRM_REJECTED,
                            tossErrorCode
                        );
                    })
                .onStatus(HttpStatusCode::is5xxServerError,
                    (httpRequest, clientResponse) -> {
                        logTossClientFailure(
                            "TOSS_CONFIRM",
                            "TOSS_CONFIRM_HTTP_ERROR",
                            "TossPaymentClientException",
                            null,
                            null,
                            null
                        );
                        throw new TossPaymentClientException(
                            GlobalErrorCode.PAYMENT_GATEWAY_ERROR
                        );
                    })
                .body(String.class)
        ));
    }

    @Override
    public TossConfirmResponse getPayment(String paymentKey) {
        return execute("TOSS_GET_PAYMENT", () -> deserializeTossConfirmResponse(
            "TOSS_GET_PAYMENT",
            webhookRestClient.get()
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
                .body(String.class)
        ));
    }

    @Override
    public TossConfirmResponse getPaymentByOrderId(String orderId) {
        return execute("TOSS_GET_PAYMENT_BY_ORDER", () -> deserializeTossConfirmResponse(
            "TOSS_GET_PAYMENT_BY_ORDER",
            webhookRestClient.get()
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
                .body(String.class)
        ));
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

    private TossConfirmResponse execute(String stage, TossRequest tossRequest) {
        try {
            TossConfirmResponse response = tossRequest.execute();

            if (response == null) {
                logTossClientFailure(
                    stage,
                    stage + "_RESPONSE_NULL",
                    "TossPaymentClientException",
                    null,
                    null,
                    null
                );
                throw new TossPaymentClientException(
                    GlobalErrorCode.PAYMENT_GATEWAY_RESPONSE_INVALID
                );
            }

            return response;
        } catch (TossPaymentClientException exception) {
            throw exception;
        } catch (ResourceAccessException exception) {
            if (isTimeout(exception)) {
                logTossClientFailure(
                    stage,
                    stage + "_TIMEOUT",
                    exception.getClass().getSimpleName(),
                    rootExceptionType(exception),
                    null,
                    null
                );
                throw new TossPaymentClientException(
                    GlobalErrorCode.PAYMENT_GATEWAY_TIMEOUT
                );
            }

            logTossClientFailure(
                stage,
                stage + "_CLIENT_ERROR",
                exception.getClass().getSimpleName(),
                rootExceptionType(exception),
                null,
                null
            );
            throw new TossPaymentClientException(GlobalErrorCode.PAYMENT_GATEWAY_ERROR);
        } catch (RestClientResponseException exception) {
            logTossClientFailure(
                stage,
                stage + "_HTTP_ERROR",
                exception.getClass().getSimpleName(),
                rootExceptionType(exception),
                exception.getStatusCode().toString(),
                null
            );
            throw new TossPaymentClientException(
                GlobalErrorCode.PAYMENT_GATEWAY_RESPONSE_INVALID
            );
        } catch (RestClientException exception) {
            if (isTimeout(exception)) {
                logTossClientFailure(
                    stage,
                    stage + "_TIMEOUT",
                    exception.getClass().getSimpleName(),
                    rootExceptionType(exception),
                    null,
                    null
                );
                throw new TossPaymentClientException(
                    GlobalErrorCode.PAYMENT_GATEWAY_TIMEOUT
                );
            }

            logTossClientFailure(
                stage,
                restClientFailureReason(stage, exception),
                exception.getClass().getSimpleName(),
                rootExceptionType(exception),
                null,
                null
            );
            if (hasCause(exception, HttpMessageConversionException.class)) {
                logTossDeserializationFailure(stage, exception);
            }
            throw new TossPaymentClientException(
                GlobalErrorCode.PAYMENT_GATEWAY_RESPONSE_INVALID
            );
        }
    }

    private TossConfirmResponse deserializeTossConfirmResponse(
            String stage,
            String responseBody) {
        if (responseBody == null) {
            return null;
        }

        try {
            return objectMapper.readValue(responseBody, TossConfirmResponse.class);
        } catch (JsonProcessingException exception) {
            logTossClientFailure(
                stage,
                stage + "_DESERIALIZATION_ERROR",
                "JsonProcessingException",
                rootExceptionType(exception),
                null,
                null
            );
            logTossDeserializationFailure(stage, exception);
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

    private String restClientFailureReason(String stage, RestClientException exception) {
        return hasCause(exception, HttpMessageConversionException.class)
            ? stage + "_DESERIALIZATION_ERROR"
            : stage + "_CLIENT_ERROR";
    }

    private boolean hasCause(Throwable throwable, Class<? extends Throwable> type) {
        Throwable current = throwable;
        while (current != null) {
            if (type.isInstance(current)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private void logTossClientFailure(
            String stage,
            String reason,
            String exceptionType,
            String rootExceptionType,
            String httpStatus,
            String tossErrorCode) {
        log.warn(
            "Toss payment client failure: stage={}, reason={}, exceptionType={}, rootExceptionType={}, httpStatus={}, tossErrorCode={}",
            stage,
            reason,
            exceptionType,
            rootExceptionType,
            httpStatus,
            tossErrorCode
        );
    }

    private void logTossDeserializationFailure(String stage, Throwable throwable) {
        Throwable rootCause = rootCause(throwable);
        log.warn(
            "Toss confirm deserialization failed: stage={}, path={}, value={}, exceptionType={}, rootExceptionType={}, rootMessage={}",
            stage,
            jsonMappingPath(throwable),
            parsedDateTimeValue(rootCause),
            throwable.getClass().getSimpleName(),
            rootCause == null ? null : rootCause.getClass().getSimpleName(),
            rootCause == null ? null : rootCause.getMessage()
        );
    }

    private String rootExceptionType(Throwable throwable) {
        Throwable root = rootCause(throwable);
        return root == null ? null : root.getClass().getSimpleName();
    }

    private Throwable rootCause(Throwable throwable) {
        Throwable current = throwable;
        Throwable root = throwable;

        while (current != null) {
            root = current;
            current = current.getCause();
        }

        return root;
    }

    private String parsedDateTimeValue(Throwable throwable) {
        if (throwable instanceof DateTimeParseException exception) {
            return exception.getParsedString();
        }

        return null;
    }

    private String jsonMappingPath(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof JsonMappingException exception) {
                String path = readJsonMappingPath(exception);
                if (path != null) {
                    return path;
                }
            }
            current = current.getCause();
        }

        return null;
    }

    private String readJsonMappingPath(JsonMappingException mappingException) {
        List<JsonMappingException.Reference> references = mappingException.getPath();
        if (references == null || references.isEmpty()) {
            return null;
        }

        StringBuilder builder = new StringBuilder();
        for (JsonMappingException.Reference reference : references) {
            appendJsonPathReference(builder, reference);
        }

        return builder.isEmpty() ? null : builder.toString();
    }

    private void appendJsonPathReference(
            StringBuilder builder,
            JsonMappingException.Reference reference) {
        String fieldName = reference.getFieldName();
        if (fieldName != null) {
            if (!builder.isEmpty()) {
                builder.append('.');
            }
            builder.append(fieldName);
            return;
        }

        int index = reference.getIndex();
        if (index >= 0) {
            builder.append('[').append(index).append(']');
        }
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
