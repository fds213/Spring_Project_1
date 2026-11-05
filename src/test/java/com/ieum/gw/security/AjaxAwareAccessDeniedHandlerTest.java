package com.ieum.gw.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.csrf.MissingCsrfTokenException;

class AjaxAwareAccessDeniedHandlerTest {

	AjaxAwareAccessDeniedHandler handler = new AjaxAwareAccessDeniedHandler();

	static MockHttpServletRequest request() {
		MockHttpServletRequest req = new MockHttpServletRequest("GET", "/gw/asset/admin/list");
		req.setContextPath("/gw");
		return req;
	}

	static MockHttpServletRequest ajax() {
		MockHttpServletRequest req = request();
		req.addHeader("X-Requested-With", "XMLHttpRequest");
		return req;
	}

	@Test
	@DisplayName("AJAX 요청에 권한이 없으면 403과 '접근 권한이 없습니다' JSON")
	void ajaxDenied() throws Exception {
		MockHttpServletResponse res = new MockHttpServletResponse();

		handler.handle(ajax(), res, new AccessDeniedException("x"));

		assertEquals(403, res.getStatus());
		assertTrue(res.getContentType().startsWith("application/json"));
		assertEquals("{\"success\":false,\"message\":\"접근 권한이 없습니다\"}", res.getContentAsString());
	}

	@Test
	@DisplayName("AJAX 요청의 CSRF 토큰이 없거나 만료되면 403과 '새로 고친 뒤 다시' 문구")
	void ajaxCsrf() throws Exception {
		MockHttpServletResponse res = new MockHttpServletResponse();

		handler.handle(ajax(), res, new MissingCsrfTokenException(null));

		assertEquals(403, res.getStatus());
		assertTrue(res.getContentAsString().contains(AjaxAwareAccessDeniedHandler.MSG_EXPIRED));
	}

	@Test
	@DisplayName("X-Requested-With 없이 JSON을 받겠다는 요청(fetch)도 AJAX로 보고 JSON으로 답한다")
	void acceptJson() throws Exception {
		MockHttpServletRequest req = request();
		req.addHeader("Accept", "application/json");
		MockHttpServletResponse res = new MockHttpServletResponse();

		handler.handle(req, res, new AccessDeniedException("x"));

		assertEquals(403, res.getStatus());
		assertTrue(res.getContentAsString().contains(AjaxAwareAccessDeniedHandler.MSG_DENIED));
	}

	@Test
	@DisplayName("화면 요청에 권한이 없으면 권한 없음 안내 화면으로 이동한다")
	void pageDenied() throws Exception {
		MockHttpServletResponse res = new MockHttpServletResponse();

		handler.handle(request(), res, new AccessDeniedException("x"));

		assertEquals("/gw/access-denied", res.getRedirectedUrl());
	}

	@Test
	@DisplayName("화면 요청의 CSRF 토큰이 만료되면 ?expired 안내 화면으로 이동한다")
	void pageCsrf() throws Exception {
		MockHttpServletResponse res = new MockHttpServletResponse();

		handler.handle(request(), res, new MissingCsrfTokenException(null));

		assertEquals("/gw/access-denied?expired", res.getRedirectedUrl());
	}
}