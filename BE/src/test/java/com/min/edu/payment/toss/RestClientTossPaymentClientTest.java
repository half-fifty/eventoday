package com.min.edu.payment.toss;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import com.min.edu.common.exception.GlobalErrorCode;
import com.min.edu.payment.toss.dto.TossCancelRequest;
import com.min.edu.payment.toss.dto.TossCancelResponse;
import com.min.edu.payment.toss.dto.TossConfirmRequest;
import com.min.edu.payment.toss.dto.TossConfirmResponse;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

@ExtendWith(OutputCaptureExtension.class)
class RestClientTossPaymentClientTest {

    private HttpServer server;
    private ExecutorService executorService;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
        if (executorService != null) {
            executorService.shutdownNow();
        }
    }

    @Test
    void confirm_sendsBasicAuthorizationAndBody() throws Exception {
        String[] capturedAuthorization = new String[1];
        String[] capturedIdempotencyKey = new String[1];
        String[] capturedBody = new String[1];
        startServer(exchange -> {
            capturedAuthorization[0] = exchange.getRequestHeaders()
                .getFirst("Authorization");
            capturedIdempotencyKey[0] = exchange.getRequestHeaders()
                .getFirst("Idempotency-Key");
            capturedBody[0] = new String(
                exchange.getRequestBody().readAllBytes(),
                StandardCharsets.UTF_8
            );
            respond(exchange, 200, """
                {
                  "paymentKey":"payment-key",
                  "orderId":"ORDER-1",
                  "totalAmount":10000,
                  "status":"DONE",
                  "method":"CARD",
                  "requestedAt":"2026-08-03T10:00:00+09:00",
                  "approvedAt":"2026-08-03T10:01:00+09:00"
                }
                """);
        });

        TossConfirmResponse response = client().confirm(request());

        assertThat(capturedAuthorization[0]).isEqualTo(
            "Basic " + Base64.getEncoder()
                .encodeToString("test-secret:".getBytes(StandardCharsets.UTF_8))
        );
        assertThat(capturedBody[0])
            .contains("\"paymentKey\":\"payment-key\"")
            .contains("\"orderId\":\"ORDER-1\"")
            .contains("\"amount\":10000");
        assertThat(capturedBody[0]).doesNotContain("10000.00");
        assertThat(capturedIdempotencyKey[0])
            .isEqualTo(client().idempotencyKey(request()));
        assertThat(response.status()).isEqualTo("DONE");
    }

    @Test
    void cancel_sendsBasicAuthorizationIdempotencyKeyAndBodyWithoutPaymentKey() throws Exception {
        String[] capturedAuthorization = new String[1];
        String[] capturedIdempotencyKey = new String[1];
        String[] capturedBody = new String[1];
        startServer(exchange -> {
            capturedAuthorization[0] = exchange.getRequestHeaders()
                .getFirst("Authorization");
            capturedIdempotencyKey[0] = exchange.getRequestHeaders()
                .getFirst("Idempotency-Key");
            capturedBody[0] = new String(
                exchange.getRequestBody().readAllBytes(),
                StandardCharsets.UTF_8
            );
            respond(exchange, 200, """
                {
                  "paymentKey":"payment-key",
                  "orderId":"ORDER-1",
                  "totalAmount":10000,
                  "status":"CANCELED",
                  "method":"CARD",
                  "requestedAt":"2026-08-03T10:00:00+09:00",
                  "approvedAt":"2026-08-03T10:01:00+09:00",
                  "cancels":[{
                    "transactionKey":"cancel-key",
                    "cancelAmount":10000,
                    "cancelReason":"reason",
                    "canceledAt":"2026-08-03T10:02:00+09:00"
                  }]
                }
                """);
        });
        TossCancelRequest request = new TossCancelRequest(
            "payment-key",
            "reason",
            10000L
        );

        TossCancelResponse response = client().cancel(request);

        assertThat(capturedAuthorization[0]).isEqualTo(
            "Basic " + Base64.getEncoder()
                .encodeToString("test-secret:".getBytes(StandardCharsets.UTF_8))
        );
        assertThat(capturedIdempotencyKey[0])
            .isEqualTo(client().refundIdempotencyKey(request));
        assertThat(capturedBody[0])
            .contains("\"cancelReason\":\"reason\"")
            .contains("\"cancelAmount\":10000")
            .doesNotContain("paymentKey");
        assertThat(response.status()).isEqualTo("CANCELED");
        assertThat(response.cancels()).hasSize(1);
    }

    @Test
    void cancel_mapsFourHundredToRefundRejected() throws Exception {
        startServer(exchange -> respond(exchange, 400, """
            {
              "code":"ALREADY_CANCELED_PAYMENT",
              "message":"already canceled"
            }
            """));

        assertThatThrownBy(() -> client().cancel(new TossCancelRequest(
            "payment-key",
            "reason",
            10000L
        )))
            .isInstanceOf(TossPaymentClientException.class)
            .satisfies(exception -> {
                TossPaymentClientException clientException =
                    (TossPaymentClientException) exception;
                assertThat(clientException.getErrorCode())
                    .isEqualTo(GlobalErrorCode.REFUND_REJECTED);
                assertThat(clientException.getTossErrorCode())
                    .isEqualTo("ALREADY_CANCELED_PAYMENT");
            });
    }

    @Test
    void idempotencyKey_isStableForSameOrderIdAndPaymentKey() throws Exception {
        startServer(exchange -> respond(exchange, 200, "{}"));
        RestClientTossPaymentClient client = client();

        String first = client.idempotencyKey(new TossConfirmRequest(
            "payment-key",
            "ORDER-1",
            10000L
        ));
        String second = client.idempotencyKey(new TossConfirmRequest(
            "payment-key",
            "ORDER-1",
            10000L
        ));

        assertThat(second).isEqualTo(first);
    }

    @Test
    void idempotencyKey_differsWhenOrderIdOrPaymentKeyDiffers() throws Exception {
        startServer(exchange -> respond(exchange, 200, "{}"));
        RestClientTossPaymentClient client = client();

        String base = client.idempotencyKey(new TossConfirmRequest(
            "payment-key",
            "ORDER-1",
            10000L
        ));
        String differentOrder = client.idempotencyKey(new TossConfirmRequest(
            "payment-key",
            "ORDER-2",
            10000L
        ));
        String differentPaymentKey = client.idempotencyKey(new TossConfirmRequest(
            "other-key",
            "ORDER-1",
            10000L
        ));

        assertThat(differentOrder).isNotEqualTo(base);
        assertThat(differentPaymentKey).isNotEqualTo(base);
    }

    @Test
    void confirm_mapsAlreadyProcessedTossErrorCode() throws Exception {
        startServer(exchange -> respond(exchange, 400, """
            {
              "code":"ALREADY_PROCESSED_PAYMENT",
              "message":"already processed"
            }
            """));

        assertThatThrownBy(() -> client().confirm(request()))
            .isInstanceOf(TossPaymentClientException.class)
            .satisfies(exception -> assertThat(
                ((TossPaymentClientException) exception).getTossErrorCode()
            ).isEqualTo("ALREADY_PROCESSED_PAYMENT"));
    }

    @Test
    void confirm_mapsFourHundredResponse() throws Exception {
        startServer(exchange -> respond(exchange, 400, "{}"));

        assertClientException(
            () -> client().confirm(request()),
            GlobalErrorCode.PAYMENT_CONFIRM_REJECTED
        );
    }

    @Test
    void confirm_mapsFiveHundredResponse() throws Exception {
        startServer(exchange -> respond(exchange, 500, "{}"));

        assertClientException(
            () -> client().confirm(request()),
            GlobalErrorCode.PAYMENT_GATEWAY_ERROR
        );
    }

    @Test
    void confirm_mapsTimeout() throws Exception {
        startServer(exchange -> {
            try {
                Thread.sleep(500L);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
            respond(exchange, 200, "{}");
        });
        TossPaymentProperties properties = properties();
        properties.setReadTimeoutMs(100L);

        assertClientException(
            () -> new RestClientTossPaymentClient(properties).confirm(request()),
            GlobalErrorCode.PAYMENT_GATEWAY_TIMEOUT
        );
    }

    @Test
    void confirm_usesConfirmTimeoutInsteadOfWebhookTimeout() throws Exception {
        startServer(exchange -> {
            try {
                Thread.sleep(200L);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
            respond(exchange, 200, """
                {
                  "paymentKey":"payment-key",
                  "orderId":"ORDER-1",
                  "totalAmount":10000,
                  "status":"DONE",
                  "method":"CARD",
                  "requestedAt":"2026-08-03T10:00:00+09:00",
                  "approvedAt":"2026-08-03T10:01:00+09:00"
                }
                """);
        });
        TossPaymentProperties properties = properties();
        properties.setReadTimeoutMs(1000L);
        properties.setWebhookReadTimeoutMs(50L);

        TossConfirmResponse response =
            new RestClientTossPaymentClient(properties).confirm(request());

        assertThat(response.paymentKey()).isEqualTo("payment-key");
    }

    @Test
    void confirm_deserializesVirtualAccountSandboxDateFormats() throws Exception {
        startServer(exchange -> respond(exchange, 200, """
            {
              "paymentKey":"payment-key",
              "orderId":"ORDER-1",
              "totalAmount":10000,
              "status":"WAITING_FOR_DEPOSIT",
              "method":"VIRTUAL_ACCOUNT",
              "secret":"secret-value",
              "virtualAccount":{
                "accountNumber":"1234567890",
                "bankCode":"088",
                "customerName":"tester",
                "dueDate":"2026-08-03T10:30:00"
              },
              "requestedAt":"2026-08-03T10:00:00+09:00",
              "approvedAt":null
            }
            """));

        TossConfirmResponse response = client().confirm(request());

        assertThat(response.status()).isEqualTo("WAITING_FOR_DEPOSIT");
        assertThat(response.requestedAt().toInstant())
            .isEqualTo(OffsetDateTime.parse("2026-08-03T10:00:00+09:00").toInstant());
        assertThat(response.approvedAt()).isNull();
        assertThat(response.virtualAccount().dueDate().toString())
            .isEqualTo("2026-08-03T10:30+09:00");
    }

    @Test
    void confirm_mapsMalformedSuccessfulResponseToGatewayResponseInvalid(
            CapturedOutput output) throws Exception {
        startServer(exchange -> respond(exchange, 200, """
            {
              "paymentKey":"payment-key",
              "orderId":"ORDER-1",
              "totalAmount":10000,
              "status":"WAITING_FOR_DEPOSIT",
              "method":"VIRTUAL_ACCOUNT",
              "requestedAt":"not-a-date"
            }
            """));

        assertClientException(
            () -> client().confirm(request()),
            GlobalErrorCode.PAYMENT_GATEWAY_RESPONSE_INVALID
        );
        assertThat(output)
            .contains("TOSS_CONFIRM_DESERIALIZATION_ERROR")
            .contains("Toss confirm deserialization failed")
            .contains("path=requestedAt")
            .contains("value=not-a-date")
            .contains("exceptionType=")
            .contains("rootExceptionType=");
    }

    @Test
    void getPayment_sendsBasicAuthorizationAndUsesPaymentPath() throws Exception {
        String[] capturedAuthorization = new String[1];
        String[] capturedPath = new String[1];
        startServer(exchange -> {
            capturedAuthorization[0] = exchange.getRequestHeaders()
                .getFirst("Authorization");
            capturedPath[0] = exchange.getRequestURI().getPath();
            respond(exchange, 200, """
                {
                  "paymentKey":"payment-key",
                  "orderId":"ORDER-1",
                  "totalAmount":10000,
                  "status":"DONE",
                  "method":"CARD",
                  "requestedAt":"2026-08-03T10:00:00+09:00",
                  "approvedAt":"2026-08-03T10:01:00+09:00"
                }
                """);
        });

        TossConfirmResponse response = client().getPayment("payment-key");

        assertThat(capturedAuthorization[0]).isEqualTo(
            "Basic " + Base64.getEncoder()
                .encodeToString("test-secret:".getBytes(StandardCharsets.UTF_8))
        );
        assertThat(capturedPath[0]).isEqualTo("/v1/payments/payment-key");
        assertThat(response.orderId()).isEqualTo("ORDER-1");
    }

    @Test
    void getPayment_mapsFourHundredResponse() throws Exception {
        startServer(exchange -> respond(exchange, 404, "{}"));

        assertClientException(
            () -> client().getPayment("payment-key"),
            GlobalErrorCode.PAYMENT_GATEWAY_RESPONSE_INVALID
        );
    }

    @Test
    void getPayment_usesWebhookTimeout() throws Exception {
        startServer(exchange -> {
            try {
                Thread.sleep(300L);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
            respond(exchange, 200, """
                {
                  "paymentKey":"payment-key",
                  "orderId":"ORDER-1",
                  "totalAmount":10000,
                  "status":"DONE",
                  "method":"CARD",
                  "requestedAt":"2026-08-03T10:00:00+09:00",
                  "approvedAt":"2026-08-03T10:01:00+09:00"
                }
                """);
        });
        TossPaymentProperties properties = properties();
        properties.setReadTimeoutMs(1000L);
        properties.setWebhookReadTimeoutMs(100L);

        assertClientException(
            () -> new RestClientTossPaymentClient(properties).getPayment("payment-key"),
            GlobalErrorCode.PAYMENT_GATEWAY_TIMEOUT
        );
    }

    private void assertClientException(
            Runnable runnable,
            GlobalErrorCode errorCode) {
        assertThatThrownBy(runnable::run)
            .isInstanceOf(TossPaymentClientException.class)
            .extracting("errorCode")
            .isEqualTo(errorCode);
    }

    private RestClientTossPaymentClient client() {
        return new RestClientTossPaymentClient(properties());
    }

    private TossPaymentProperties properties() {
        TossPaymentProperties properties = new TossPaymentProperties();
        properties.setSecretKey("test-secret");
        properties.setBaseUrl("http://localhost:" + server.getAddress().getPort());
        properties.setConnectTimeoutMs(1000L);
        properties.setReadTimeoutMs(1000L);
        properties.setWebhookConnectTimeoutMs(1000L);
        properties.setWebhookReadTimeoutMs(1000L);
        return properties;
    }

    private TossConfirmRequest request() {
        return new TossConfirmRequest(
            "payment-key",
            "ORDER-1",
            10000L
        );
    }

    private void startServer(Handler handler) throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/v1/payments/confirm", exchange -> handler.handle(exchange));
        server.createContext("/v1/payments/payment-key", exchange -> handler.handle(exchange));
        server.createContext("/v1/payments/payment-key/cancel", exchange -> handler.handle(exchange));
        executorService = Executors.newSingleThreadExecutor();
        server.setExecutor(executorService);
        server.start();
    }

    private void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] response = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, response.length);
        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(response);
        }
    }

    @FunctionalInterface
    private interface Handler {

        void handle(HttpExchange exchange) throws IOException;
    }
}
