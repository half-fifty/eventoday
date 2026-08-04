package com.min.edu.payment.toss;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.min.edu.common.exception.GlobalErrorCode;
import com.min.edu.payment.toss.dto.TossConfirmRequest;
import com.min.edu.payment.toss.dto.TossConfirmResponse;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

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
        String[] capturedBody = new String[1];
        startServer(exchange -> {
            capturedAuthorization[0] = exchange.getRequestHeaders()
                .getFirst("Authorization");
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
        assertThat(response.status()).isEqualTo("DONE");
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
        return properties;
    }

    private TossConfirmRequest request() {
        return new TossConfirmRequest(
            "payment-key",
            "ORDER-1",
            BigDecimal.valueOf(10000)
        );
    }

    private void startServer(Handler handler) throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/v1/payments/confirm", exchange -> handler.handle(exchange));
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
