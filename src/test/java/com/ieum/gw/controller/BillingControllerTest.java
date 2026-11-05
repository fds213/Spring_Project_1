package com.ieum.gw.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ieum.gw.dto.AuthEmpDTO;
import com.ieum.gw.dto.LoginUser;
import com.ieum.gw.dto.BillingPaymentDTO;
import com.ieum.gw.dto.ConfirmFormDTO;
import com.ieum.gw.dto.FailFormDTO;
import com.ieum.gw.dto.OrderFormDTO;
import com.ieum.gw.dto.OrderResultDTO;
import com.ieum.gw.dto.QuoteDTO;
import com.ieum.gw.service.BillingException;
import com.ieum.gw.service.PaymentService;

/** 화면 JS가 받는 응답 모양(docs/front-screen-api.md 2장)을 확인한다. Security(CSRF)는 빼고 Controller만 띄운다 */
class BillingControllerTest {

    /** 정해 둔 예외를 던지거나 받은 값을 기억하는 가짜 */
    static class FakeService extends PaymentService {
        RuntimeException error;
        LoginUser user;
        ConfirmFormDTO confirmForm;
        OrderFormDTO orderForm;
        FailFormDTO failForm;

        FakeService() {
            super(null, null, null, null, null);
        }

        private void check() {
            if (error != null) {
                throw error;
            }
        }

        @Override
        public QuoteDTO quote(LoginUser u, String plan, int users) {
            user = u;
            check();
            QuoteDTO quote = new QuoteDTO();
            quote.setUsers(users);
            return quote;
        }

        @Override
        public OrderResultDTO createOrder(LoginUser u, OrderFormDTO form) {
            orderForm = form;
            check();
            OrderResultDTO result = new OrderResultDTO();
            result.setOrderNo("ieum-1");
            return result;
        }

        @Override
        public BillingPaymentDTO confirm(LoginUser u, ConfirmFormDTO form) {
            confirmForm = form;
            check();
            BillingPaymentDTO payment = new BillingPaymentDTO();
            payment.setId(form.getOrderId());
            return payment;
        }

        @Override
        public void reportFailure(LoginUser u, FailFormDTO form) {
            failForm = form;
        }
    }

    private FakeService service;
    private MockMvc mvc;
    private LoginUser login;

    @BeforeEach
    void setUp() {
        service = new FakeService();
        mvc = MockMvcBuilders.standaloneSetup(new BillingController(service))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
        AuthEmpDTO emp = new AuthEmpDTO();
        emp.setRole("ROLE_ADMIN");
        login = new LoginUser(emp);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(login, null, login.getAuthorities()));
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private static JsonNode json(MvcResult result) throws Exception {
        return new ObjectMapper().readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private MvcResult postJson(String path, String body) throws Exception {
        return mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(body)).andReturn();
    }

    @Test
    @DisplayName("결제 화면과 토스가 돌아오는 주소는 같은 화면을 연다")
    void pages() throws Exception {
        for (String path : new String[] {"/subscription/billing", "/subscription/billing/success", "/subscription/billing/fail"}) {
            MvcResult result = mvc.perform(get(path).param("orderId", "x").param("paymentKey", "y")).andReturn();
            assertEquals("subscription/billing", result.getModelAndView().getViewName());
        }
        assertEquals(null, service.confirmForm, "돌아온 GET만으로는 승인 확인을 부르지 않는다");
    }

    @Test
    @DisplayName("로그인 사용자를 서버 인증 정보에서 받고, 성공은 {success, data}로 감싼다")
    void quote() throws Exception {
        MvcResult result = mvc.perform(get("/subscription/api/quote").param("plan", "MONTHLY").param("users", "3")).andReturn();

        assertEquals(200, result.getResponse().getStatus());
        assertEquals(login, service.user);
        assertTrue(json(result).get("success").asBoolean());
        assertEquals(3, json(result).get("data").get("users").asInt());
    }

    @Test
    @DisplayName("인원이 숫자가 아니면 400과 안내 문구다")
    void quoteBadUsers() throws Exception {
        MvcResult result = mvc.perform(get("/subscription/api/quote").param("plan", "MONTHLY").param("users", "3.5")).andReturn();

        assertEquals(400, result.getResponse().getStatus());
        assertFalse(json(result).get("message").asText().isEmpty());
    }

    @Test
    @DisplayName("주문·승인 확인·실패 알림은 JSON 본문을 받는다")
    void postBodies() throws Exception {
        MvcResult order = postJson("/subscription/api/orders", "{\"plan\":\"MONTHLY\",\"users\":3,\"amount\":12000}");
        assertEquals("ieum-1", json(order).get("data").get("orderNo").asText());
        assertEquals(12000L, service.orderForm.getAmount());

        MvcResult confirm = postJson("/subscription/api/orders/confirm", "{\"orderId\":\"ieum-1\",\"paymentKey\":\"pk\",\"amount\":12000}");
        assertEquals("ieum-1", json(confirm).get("data").get("id").asText());
        assertEquals("pk", service.confirmForm.getPaymentKey());

        MvcResult fail = postJson("/subscription/api/orders/fail", "{\"orderId\":\"ieum-1\",\"code\":\"X\",\"message\":\"m\"}");
        assertTrue(json(fail).get("success").asBoolean());
        assertEquals("X", service.failForm.getCode());
    }

    @Test
    @DisplayName("거절은 정한 상태 코드와 문구, 다시 보내도 되는지를 알린다")
    void rejected() throws Exception {
        service.error = new BillingException(503, "결제 결과를 확인하지 못했습니다.", true);

        MvcResult result = postJson("/subscription/api/orders/confirm", "{\"orderId\":\"ieum-1\"}");

        assertEquals(503, result.getResponse().getStatus());
        assertEquals("결제 결과를 확인하지 못했습니다.", json(result).get("message").asText());
        assertTrue(json(result).get("retry").asBoolean());
    }

    @Test
    @DisplayName("그 밖의 오류는 내용을 숨기고 500으로, 다시 보내도 된다고 알린다")
    void unexpected() throws Exception {
        service.error = new IllegalStateException("ORA-00001 표 이름");

        MvcResult result = postJson("/subscription/api/orders/confirm", "{\"orderId\":\"ieum-1\"}");

        assertEquals(500, result.getResponse().getStatus());
        assertFalse(json(result).get("message").asText().contains("ORA"));
        assertTrue(json(result).get("retry").asBoolean());
    }

    @Test
    @DisplayName("JSON이 깨지면 400이다")
    void brokenJson() throws Exception {
        assertEquals(400, postJson("/subscription/api/orders", "{").getResponse().getStatus());
    }
}
