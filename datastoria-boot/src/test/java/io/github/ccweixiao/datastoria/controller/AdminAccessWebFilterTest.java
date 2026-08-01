package io.github.ccweixiao.datastoria.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.reactive.result.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.server.WebFilterChain;

import io.github.ccweixiao.datastoria.common.identity.AdminAccess;
import io.github.ccweixiao.datastoria.common.identity.Identity;
import io.github.ccweixiao.datastoria.common.identity.IdentityContext;

import reactor.core.publisher.Mono;
import reactor.util.context.Context;

class AdminAccessWebFilterTest {

  @Test
  void rejectsOrdinaryUserBeforeAnnotatedControllerRuns() throws Exception {
    RequestMappingHandlerMapping mapping = mock(RequestMappingHandlerMapping.class);
    WebFilterChain chain = mock(WebFilterChain.class);
    MockServerWebExchange exchange = exchange();
    when(mapping.getHandler(exchange)).thenReturn(Mono.just(handler("adminOnly")));

    new AdminAccessWebFilter(mapping)
        .filter(exchange, chain)
        .contextWrite(
            Context.of(
                IdentityContext.CONTEXT_KEY, new Identity("tenant", "user", Set.of("ROLE_USER"))))
        .block();

    assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(exchange.getResponse().getHeaders().getFirst("X-Error-Code"))
        .isEqualTo("ADMIN_ACCESS_REQUIRED");
    verifyNoInteractions(chain);
  }

  @Test
  void allowsAdministratorThroughAnnotatedControllerExactlyOnce() throws Exception {
    RequestMappingHandlerMapping mapping = mock(RequestMappingHandlerMapping.class);
    WebFilterChain chain = mock(WebFilterChain.class);
    MockServerWebExchange exchange = exchange();
    when(mapping.getHandler(exchange)).thenReturn(Mono.just(handler("adminOnly")));
    when(chain.filter(exchange)).thenReturn(Mono.empty());

    new AdminAccessWebFilter(mapping)
        .filter(exchange, chain)
        .contextWrite(
            Context.of(
                IdentityContext.CONTEXT_KEY,
                new Identity("tenant", "admin", Set.of("ROLE_ADMIN", "ROLE_USER"))))
        .block();

    verify(chain, times(1)).filter(exchange);
  }

  @Test
  void rejectsOrdinaryUserFromClassLevelAdminController() throws Exception {
    RequestMappingHandlerMapping mapping = mock(RequestMappingHandlerMapping.class);
    WebFilterChain chain = mock(WebFilterChain.class);
    MockServerWebExchange exchange = exchange();
    Method method = ClassProtectedController.class.getDeclaredMethod("endpoint");
    when(mapping.getHandler(exchange))
        .thenReturn(Mono.just(new HandlerMethod(new ClassProtectedController(), method)));

    new AdminAccessWebFilter(mapping)
        .filter(exchange, chain)
        .contextWrite(
            Context.of(
                IdentityContext.CONTEXT_KEY, new Identity("tenant", "user", Set.of("ROLE_USER"))))
        .block();

    assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    verifyNoInteractions(chain);
  }

  @Test
  void allowsOrdinaryUserThroughUnannotatedControllerExactlyOnce() throws Exception {
    RequestMappingHandlerMapping mapping = mock(RequestMappingHandlerMapping.class);
    WebFilterChain chain = mock(WebFilterChain.class);
    MockServerWebExchange exchange = exchange();
    when(mapping.getHandler(exchange)).thenReturn(Mono.just(handler("ordinary")));
    when(chain.filter(exchange)).thenReturn(Mono.empty());

    new AdminAccessWebFilter(mapping)
        .filter(exchange, chain)
        .contextWrite(
            Context.of(
                IdentityContext.CONTEXT_KEY, new Identity("tenant", "user", Set.of("ROLE_USER"))))
        .block();

    verify(chain, times(1)).filter(exchange);
  }

  private static HandlerMethod handler(String name) throws Exception {
    Method method = TestController.class.getDeclaredMethod(name);
    return new HandlerMethod(new TestController(), method);
  }

  private static MockServerWebExchange exchange() {
    return MockServerWebExchange.from(MockServerHttpRequest.get("/annotation-protected").build());
  }

  private static class TestController {
    @AdminAccess
    public void adminOnly() {}

    public void ordinary() {}
  }

  @AdminAccess
  private static class ClassProtectedController {
    public void endpoint() {}
  }
}
