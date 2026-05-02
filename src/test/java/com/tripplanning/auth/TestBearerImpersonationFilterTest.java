package com.tripplanning.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import com.tripplanning.user.UserRepository;

/**
 * Pure unit test for {@link TestBearerImpersonationFilter}; avoids the full Spring context (which
 * pulls in GCP Storage auto-configuration and requires ADC).
 */
class TestBearerImpersonationFilterTest {

  private static final String TEST_BEARER = "ci-test-bearer-secret-32-bytes!!";

  private UserRepository userRepository;
  private TestBearerImpersonationFilter filter;

  @BeforeEach
  void setUp() {
    userRepository = mock(UserRepository.class);
    filter = new TestBearerImpersonationFilter(TEST_BEARER, userRepository);
    SecurityContextHolder.clearContext();
  }

  @Test
  void blankToken_throws() {
    org.junit.jupiter.api.Assertions.assertThrows(
        IllegalStateException.class, () -> new TestBearerImpersonationFilter("", userRepository));
    org.junit.jupiter.api.Assertions.assertThrows(
        IllegalStateException.class, () -> new TestBearerImpersonationFilter(null, userRepository));
  }

  @Test
  void noAuthorizationHeader_passesThroughWithoutAuth() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v2/auth/me");
    MockHttpServletResponse response = new MockHttpServletResponse();
    FilterChain chain = mock(FilterChain.class);

    filter.doFilter(request, response, chain);

    verify(chain, times(1)).doFilter(request, response);
    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  void mismatchedBearer_passesThroughWithRealAuthorizationHeader() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v2/auth/me");
    request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer some.other.jwt");
    MockHttpServletResponse response = new MockHttpServletResponse();
    FilterChain chain = mock(FilterChain.class);

    filter.doFilter(request, response, chain);

    ArgumentCaptor<HttpServletRequest> req = ArgumentCaptor.forClass(HttpServletRequest.class);
    verify(chain, times(1)).doFilter(req.capture(), any());
    assertThat(req.getValue().getHeader(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer some.other.jwt");
    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
  }

  @Test
  void matchingBearerWithActAsHeader_authenticatesAndStripsHeader() throws Exception {
    when(userRepository.existsById(42L)).thenReturn(true);

    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v2/auth/me");
    request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + TEST_BEARER);
    request.addHeader("X-Act-As-User", "42");
    MockHttpServletResponse response = new MockHttpServletResponse();
    FilterChain chain = mock(FilterChain.class);

    filter.doFilter(request, response, chain);

    ArgumentCaptor<HttpServletRequest> req = ArgumentCaptor.forClass(HttpServletRequest.class);
    verify(chain, times(1)).doFilter(req.capture(), any());
    assertThat(req.getValue().getHeader(HttpHeaders.AUTHORIZATION)).isNull();

    var auth = SecurityContextHolder.getContext().getAuthentication();
    assertThat(auth).isInstanceOf(JwtAuthenticationToken.class);
    Jwt jwt = (Jwt) auth.getPrincipal();
    assertThat(jwt.getSubject()).isEqualTo("42");
    Object actAsClaim = jwt.getClaim("act_as_user_id");
    assertThat(actAsClaim).isEqualTo(42L);
    boolean isAuthenticated = auth.isAuthenticated();
    assertThat(isAuthenticated).isTrue();
    verify(userRepository, times(1)).existsById(42L);
  }

  @Test
  void matchingBearerWithoutActAsHeader_returns400() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v2/auth/me");
    request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + TEST_BEARER);
    MockHttpServletResponse response = new MockHttpServletResponse();
    FilterChain chain = mock(FilterChain.class);

    filter.doFilter(request, response, chain);

    verify(chain, never()).doFilter(any(), any());
    assertThat(response.getStatus()).isEqualTo(400);
    assertThat(response.getHeader(HttpHeaders.WWW_AUTHENTICATE)).contains("invalid_request");
    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    verify(userRepository, never()).existsById(anyLong());
  }

  @Test
  void matchingBearerWithNonNumericActAsHeader_returns400() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v2/auth/me");
    request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + TEST_BEARER);
    request.addHeader("X-Act-As-User", "not-a-number");
    MockHttpServletResponse response = new MockHttpServletResponse();
    FilterChain chain = mock(FilterChain.class);

    filter.doFilter(request, response, chain);

    verify(chain, never()).doFilter(any(), any());
    assertThat(response.getStatus()).isEqualTo(400);
    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    verify(userRepository, never()).existsById(anyLong());
  }

  @Test
  void matchingBearerWithUnknownUser_returns401() throws Exception {
    when(userRepository.existsById(99L)).thenReturn(false);

    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v2/auth/me");
    request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + TEST_BEARER);
    request.addHeader("X-Act-As-User", "99");
    MockHttpServletResponse response = new MockHttpServletResponse();
    FilterChain chain = mock(FilterChain.class);

    filter.doFilter(request, response, chain);

    verify(chain, never()).doFilter(any(), any());
    assertThat(response.getStatus()).isEqualTo(401);
    assertThat(response.getHeader(HttpHeaders.WWW_AUTHENTICATE)).contains("invalid_token");
    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
  }

  @Test
  void caseInsensitiveBearerPrefix_matches() throws Exception {
    when(userRepository.existsById(7L)).thenReturn(true);

    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v2/auth/me");
    request.addHeader(HttpHeaders.AUTHORIZATION, "bearer " + TEST_BEARER);
    request.addHeader("X-Act-As-User", "7");
    MockHttpServletResponse response = new MockHttpServletResponse();
    FilterChain chain = mock(FilterChain.class);

    filter.doFilter(request, response, chain);

    verify(chain, times(1)).doFilter(any(), any());
    var auth = SecurityContextHolder.getContext().getAuthentication();
    assertThat(auth).isNotNull();
    assertThat(((Jwt) auth.getPrincipal()).getSubject()).isEqualTo("7");
  }
}
