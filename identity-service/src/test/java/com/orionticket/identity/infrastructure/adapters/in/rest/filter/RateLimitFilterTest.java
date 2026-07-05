package com.orionticket.identity.infrastructure.adapters.in.rest.filter;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Tests unitarios del {@link RateLimitFilter} (Fase 2, C4).
 */
class RateLimitFilterTest {

    private RateLimitFilter filter;

    @BeforeEach
    void setUp() {
        filter = new RateLimitFilter();
        ReflectionTestUtils.setField(filter, "capacity", 3);
        ReflectionTestUtils.setField(filter, "refillMinutes", 1);
    }

    @Test
    void givenNonProtectedPath_whenRequest_thenFilterChainProceeds() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/v1/users");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        assertEquals(200, response.getStatus());
    }

    @Test
    void givenProtectedPathWithinLimit_whenRequests_thenAllProceed() throws Exception {
        FilterChain chain = mock(FilterChain.class);

        for (int i = 0; i < 3; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/v1/auth/login");
            request.setRemoteAddr("10.0.0.1");
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilter(request, response, chain);
            verify(chain).doFilter(request, response);
            assertEquals(200, response.getStatus());
        }
    }

    @Test
    void givenProtectedPathExceedingLimit_whenFourthRequest_thenReturns429WithRetryAfter() throws Exception {
        FilterChain chain = mock(FilterChain.class);
        String ip = "10.0.0.2";

        // Consumir las 3 tokens disponibles
        for (int i = 0; i < 3; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/v1/auth/login");
            request.setRemoteAddr(ip);
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(request, response, chain);
        }

        // La 4.ª petición debe ser rechazada — usamos un mock fresco para
        // verificar que el chain NO se invoca en esta petición concreta.
        FilterChain rejectedChain = mock(FilterChain.class);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/v1/auth/login");
        request.setRemoteAddr(ip);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, rejectedChain);

        assertEquals(429, response.getStatus());
        assertNotNull(response.getHeader("Retry-After"));
        assertTrue(Integer.parseInt(response.getHeader("Retry-After")) >= 1);
        assertNotNull(response.getContentType());
        assertTrue(response.getContentAsString().contains("RATE_LIMIT_EXCEEDED"));
        verifyNoInteractions(rejectedChain);
    }

    @Test
    void givenDifferentIps_whenRequests_thenIndependentBuckets() throws Exception {
        FilterChain chain = mock(FilterChain.class);

        // IP A consume 3 tokens (capacity)
        for (int i = 0; i < 3; i++) {
            MockHttpServletRequest req = new MockHttpServletRequest("POST", "/v1/auth/login");
            req.setRemoteAddr("10.0.0.10");
            MockHttpServletResponse resp = new MockHttpServletResponse();
            filter.doFilter(req, resp, chain);
            assertEquals(200, resp.getStatus());
        }

        // IP B todavía tiene sus 3 tokens
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/v1/auth/login");
        req.setRemoteAddr("10.0.0.20");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        filter.doFilter(req, resp, chain);
        assertEquals(200, resp.getStatus());
        verify(chain).doFilter(req, resp);
    }

    @Test
    void givenXForwardedForHeader_whenRequest_thenUsesFirstIpInChain() throws Exception {
        FilterChain chain = mock(FilterChain.class);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/v1/auth/login");
        request.addHeader("X-Forwarded-For", "203.0.113.5, 10.0.0.1");
        request.setRemoteAddr("10.0.0.1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        assertEquals(200, response.getStatus());
    }

    @Test
    void givenRegisterPath_whenExceedingLimit_thenReturns429() throws Exception {
        FilterChain chain = mock(FilterChain.class);
        String ip = "10.0.0.3";

        for (int i = 0; i < 3; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/v1/auth/register");
            request.setRemoteAddr(ip);
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(request, response, chain);
        }

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/v1/auth/register");
        request.setRemoteAddr(ip);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        assertEquals(429, response.getStatus());
        assertNotNull(response.getHeader("Retry-After"));
    }
}
