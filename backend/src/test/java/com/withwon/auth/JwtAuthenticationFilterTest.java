package com.withwon.auth;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class JwtAuthenticationFilterTest {

    private AuthSessionService authSessionService;
    private AuthErrorResponseWriter responseWriter;
    private JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        authSessionService = mock(AuthSessionService.class);
        responseWriter = mock(AuthErrorResponseWriter.class);
        filter = new JwtAuthenticationFilter(authSessionService, responseWriter);
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void continuesWithoutAuthenticationHeader() throws Exception {
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), chain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        assertTrue(chain.getRequest() != null);
        verifyNoInteractions(authSessionService, responseWriter);
    }

    @Test
    void authenticatesValidBearerToken() throws Exception {
        when(authSessionService.authenticate("access-token"))
                .thenReturn(new AuthSessionService.SessionPrincipal(7L, "CUSTOMER"));
        FilterChain chain = mock(FilterChain.class);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer access-token");

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        var authentication = SecurityContextHolder.getContext().getAuthentication();
        assertEquals(7L, ((AuthSessionService.SessionPrincipal) authentication.getPrincipal()).userId());
        assertEquals("ROLE_CUSTOMER", authentication.getAuthorities().iterator().next().getAuthority());
        verify(chain).doFilter(eq(request), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void writesUnauthorizedResponseForInvalidBearerToken() throws Exception {
        doThrow(new InvalidTokenException())
                .when(authSessionService).authenticate("invalid-token");
        FilterChain chain = mock(FilterChain.class);
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.addHeader("Authorization", "Bearer invalid-token");

        filter.doFilter(request, response, chain);

        verify(responseWriter).write(response, 401, "INVALID_ACCESS_TOKEN", "Invalid access token.");
        verifyNoInteractions(chain);
    }
}
