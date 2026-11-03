package com.ieum.gw.subscription.service;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.PropertySource;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ieum.gw.subscription.dto.TossPaymentDTO;

/**
 * 토스페이먼츠 API 호출. 트랜잭션 밖에서 부른다(AGENTS.md '트랜잭션·동시 처리').
 * 키는 저장소에 올리지 않는 config/toss.properties에서 읽는다(toss.properties.example 참고). 파일이 없어도 서버는 뜨고 결제만 안 된다.
 */
@Component
@PropertySource(value = "classpath:config/toss.properties", ignoreResourceNotFound = true)
public class TossPaymentsClient {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(60);
    private static final String TEST_KEY_PREFIX = "test_"; // 실제 대금이 빠져나가지 않게 테스트 키만 받는다(PAY-01)

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();
    private final ObjectMapper json = new ObjectMapper();
    private final String clientKey;
    private final String secretKey;
    private final String apiUrl;

    public TossPaymentsClient(@Value("${toss.client-key:}") String clientKey,
                              @Value("${toss.secret-key:}") String secretKey,
                              @Value("${toss.api-url:https://api.tosspayments.com}") String apiUrl) {
        this.clientKey = clientKey;
        this.secretKey = secretKey;
        this.apiUrl = apiUrl;
    }

    /** 테스트 키가 둘 다 있는가. 없으면 결제창을 열지 않는다 */
    public boolean isConfigured() {
        return clientKey.startsWith(TEST_KEY_PREFIX) && secretKey.startsWith(TEST_KEY_PREFIX);
    }

    public String getClientKey() {
        return clientKey;
    }

    /**
     * 결제 승인 요청. 같은 멱등키로 다시 부르면 토스가 처음 응답을 그대로 돌려준다.
     *
     * @throws TossApiException 토스가 오류로 답했거나 답을 받지 못했을 때
     */
    public TossPaymentDTO confirm(String paymentKey, String orderId, long amount, String idempotencyKey) {
        String body;
        try {
            body = json.writeValueAsString(Map.of("paymentKey", paymentKey, "orderId", orderId, "amount", amount));
        } catch (IOException e) {
            throw new TossApiException("승인 요청을 만들지 못했습니다", e);
        }
        HttpRequest request = authorized("/v1/payments/confirm")
                .header("Content-Type", "application/json")
                .header("Idempotency-Key", idempotencyKey)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return read(send(request));
    }

    /**
     * 주문 번호로 결제 조회. 승인 결과를 모를 때 쓴다.
     *
     * @return 토스에 없는 주문이면 null
     * @throws TossApiException 토스가 그 밖의 오류로 답했거나 답을 받지 못했을 때
     */
    public TossPaymentDTO findByOrderId(String orderId) {
        String path = "/v1/payments/orders/" + URLEncoder.encode(orderId, StandardCharsets.UTF_8);
        HttpResponse<String> response = send(authorized(path).GET().build());
        return response.statusCode() == 404 ? null : read(response);
    }

    private HttpRequest.Builder authorized(String path) {
        if (!isConfigured()) {
            throw new TossApiException(0, "NOT_CONFIGURED", "토스 테스트 키가 설정되지 않았습니다");
        }
        String basic = Base64.getEncoder().encodeToString((secretKey + ":").getBytes(StandardCharsets.UTF_8));
        return HttpRequest.newBuilder(URI.create(apiUrl + path))
                .timeout(REQUEST_TIMEOUT)
                .header("Authorization", "Basic " + basic);
    }

    private HttpResponse<String> send(HttpRequest request) {
        try {
            return http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new TossApiException("토스 API에 연결하지 못했습니다", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new TossApiException("토스 API 호출이 중단되었습니다", e);
        }
    }

    private TossPaymentDTO read(HttpResponse<String> response) {
        int status = response.statusCode();
        try {
            if (status >= 200 && status < 300) {
                return json.readValue(response.body(), TossPaymentDTO.class);
            }
            JsonNode error = json.readTree(response.body());
            throw new TossApiException(status, error.path("code").asText("UNKNOWN"),
                    error.path("message").asText("토스 API 오류입니다"));
        } catch (IOException e) {
            // 알아볼 수 없는 답(게이트웨이 오류 화면 등): 승인됐는지 모르므로 거절로 보지 않는다
            throw new TossApiException(status >= 500 ? status : 0, "UNREADABLE", "토스 API 응답을 읽지 못했습니다");
        }
    }
}
