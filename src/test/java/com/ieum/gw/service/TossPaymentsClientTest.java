package com.ieum.gw.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ieum.gw.dto.TossPaymentDTO;
import com.sun.net.httpserver.HttpServer;

/** 토스 대신 이 컴퓨터에 띄운 가짜 서버로 요청 모양과 응답 해석을 확인한다 */
class TossPaymentsClientTest {

    private static final String SECRET = "test_sk_abc";
    private static final String DONE = "{\"mId\":\"tosspayments\",\"paymentKey\":\"pk1\",\"orderId\":\"ieum-1\","
            + "\"status\":\"DONE\",\"currency\":\"KRW\",\"totalAmount\":12000,\"approvedAt\":\"2026-11-02T14:03:22+09:00\",\"card\":{\"number\":\"1234\"}}";

    private HttpServer server;
    private TossPaymentsClient client;
    private int status = 200;
    private String answer = DONE;
    private String path;
    private String method;
    private String authorization;
    private String idempotencyKey;
    private String contentType;
    private String body;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            path = exchange.getRequestURI().getPath();
            method = exchange.getRequestMethod();
            authorization = exchange.getRequestHeaders().getFirst("Authorization");
            idempotencyKey = exchange.getRequestHeaders().getFirst("Idempotency-Key");
            contentType = exchange.getRequestHeaders().getFirst("Content-Type");
            body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            byte[] bytes = answer.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        client = new TossPaymentsClient("test_ck_abc", SECRET, "http://127.0.0.1:" + server.getAddress().getPort());
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    @DisplayName("승인 요청: 주소·Basic 인증(시크릿 키 + 콜론)·멱등키·본문을 문서대로 보내고 응답을 읽는다")
    void confirm() throws IOException {
        TossPaymentDTO result = client.confirm("pk1", "ieum-1", 12000L, "ieum-1");

        assertEquals("POST", method);
        assertEquals("/v1/payments/confirm", path);
        assertEquals("Basic " + Base64.getEncoder().encodeToString((SECRET + ":").getBytes(StandardCharsets.UTF_8)), authorization);
        assertEquals("ieum-1", idempotencyKey);
        assertEquals("application/json", contentType);
        JsonNode sent = new ObjectMapper().readTree(body);
        assertEquals("pk1", sent.get("paymentKey").asText());
        assertEquals("ieum-1", sent.get("orderId").asText());
        assertEquals(12000L, sent.get("amount").asLong());
        assertEquals("DONE", result.getStatus());
        assertEquals(12000L, result.getTotalAmount());
        assertEquals("KRW", result.getCurrency());
        assertEquals("2026-11-02T14:03:22+09:00", result.getApprovedAt());
    }

    @Test
    @DisplayName("거절: 토스의 코드와 문구를 그대로 올리고 거절로 판단한다")
    void rejected() {
        status = 403;
        answer = "{\"code\":\"REJECT_CARD_PAYMENT\",\"message\":\"한도초과 혹은 잔액부족으로 결제에 실패했습니다.\",\"data\":null}";

        TossApiException e = assertThrows(TossApiException.class, () -> client.confirm("pk1", "ieum-1", 1L, "k"));

        assertEquals(403, e.getStatus());
        assertEquals("REJECT_CARD_PAYMENT", e.getCode());
        assertEquals("한도초과 혹은 잔액부족으로 결제에 실패했습니다.", e.getMessage());
        assertTrue(e.isRejected());
    }

    @Test
    @DisplayName("승인 여부를 단정할 수 없는 오류는 거절로 보지 않는다")
    void notFinal() {
        assertFalse(new TossApiException(500, "FAILED_INTERNAL_SYSTEM_PROCESSING", "").isRejected());
        assertFalse(new TossApiException(400, "ALREADY_PROCESSED_PAYMENT", "").isRejected());
        assertFalse(new TossApiException(400, "PROVIDER_ERROR", "").isRejected());
        assertFalse(new TossApiException(401, "UNAUTHORIZED_KEY", "").isRejected());
        assertFalse(new TossApiException(429, "TOO_MANY_REQUESTS", "").isRejected());
        assertFalse(new TossApiException("연결 실패", new IOException()).isRejected());
        assertTrue(new TossApiException(409, "IDEMPOTENT_REQUEST_PROCESSING", "").isInProgress());
        assertTrue(new TossApiException(404, "NOT_FOUND_PAYMENT_SESSION", "").isRejected());
    }

    @Test
    @DisplayName("JSON이 아닌 5xx 응답은 거절이 아니라 결과를 모르는 것이다")
    void unreadableError() {
        status = 502;
        answer = "<html>Bad Gateway</html>";

        TossApiException e = assertThrows(TossApiException.class, () -> client.confirm("pk1", "ieum-1", 1L, "k"));

        assertFalse(e.isRejected());
    }

    @Test
    @DisplayName("JSON이 아닌 200 응답도 승인으로 보지 않는다")
    void unreadableSuccess() {
        answer = "ok";

        assertFalse(assertThrows(TossApiException.class, () -> client.confirm("pk1", "ieum-1", 1L, "k")).isRejected());
    }

    @Test
    @DisplayName("주문 번호로 조회한다. 토스에 없으면 null이다")
    void find() {
        assertEquals("pk1", client.findByOrderId("ieum-1").getPaymentKey());
        assertEquals("GET", method);
        assertEquals("/v1/payments/orders/ieum-1", path);

        status = 404;
        answer = "{\"code\":\"NOT_FOUND_PAYMENT\",\"message\":\"없음\"}";
        assertNull(client.findByOrderId("ieum-2"));
    }

    @Test
    @DisplayName("연결할 수 없으면 거절이 아니라 결과를 모르는 것이다")
    void cannotConnect() {
        server.stop(0);

        TossApiException e = assertThrows(TossApiException.class, () -> client.confirm("pk1", "ieum-1", 1L, "k"));

        assertEquals(0, e.getStatus());
        assertFalse(e.isRejected());
    }

    @Test
    @DisplayName("키가 비었거나 테스트 키가 아니면 부르지 않는다")
    void onlyTestKeys() {
        assertTrue(client.isConfigured());
        assertFalse(new TossPaymentsClient("", "", "http://x").isConfigured());
        assertFalse(new TossPaymentsClient("live_ck_a", "live_sk_a", "http://x").isConfigured());
        assertFalse(new TossPaymentsClient("test_ck_a", "live_sk_a", "http://x").isConfigured());

        TossPaymentsClient live = new TossPaymentsClient("live_ck_a", "live_sk_a", "http://127.0.0.1:1");
        TossApiException e = assertThrows(TossApiException.class, () -> live.confirm("pk1", "ieum-1", 1L, "k"));
        assertEquals("NOT_CONFIGURED", e.getCode());
    }
}
