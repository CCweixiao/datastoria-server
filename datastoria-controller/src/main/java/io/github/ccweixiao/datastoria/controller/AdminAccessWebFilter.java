package io.github.ccweixiao.datastoria.controller;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.reactive.result.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;

import io.github.ccweixiao.datastoria.common.error.ApiErrorCode;
import io.github.ccweixiao.datastoria.common.identity.AdminAccess;
import io.github.ccweixiao.datastoria.common.identity.IdentityContext;

import reactor.core.publisher.Mono;

/** Enforces {@link AdminAccess} before an annotated controller method is invoked. */
@Component
@Order(-190)
public class AdminAccessWebFilter implements WebFilter {

  private final RequestMappingHandlerMapping handlerMapping;

  public AdminAccessWebFilter(RequestMappingHandlerMapping handlerMapping) {
    this.handlerMapping = handlerMapping;
  }

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
    if (HttpMethod.OPTIONS.equals(exchange.getRequest().getMethod())) {
      return chain.filter(exchange);
    }
    return handlerMapping
        .getHandler(exchange)
        .map(AdminAccessWebFilter::requiresAdmin)
        .defaultIfEmpty(false)
        .flatMap(
            requiresAdmin -> {
              if (!requiresAdmin) {
                return chain.filter(exchange);
              }
              return IdentityContext.current()
                  .flatMap(
                      identity ->
                          identity.isAdmin() ? chain.filter(exchange) : writeForbidden(exchange));
            });
  }

  private static boolean requiresAdmin(Object handler) {
    if (!(handler instanceof HandlerMethod method)) {
      return false;
    }
    return AnnotatedElementUtils.hasAnnotation(method.getMethod(), AdminAccess.class)
        || AnnotatedElementUtils.hasAnnotation(method.getBeanType(), AdminAccess.class);
  }

  private static Mono<Void> writeForbidden(ServerWebExchange exchange) {
    ApiErrorCode error = ApiErrorCode.ADMIN_ACCESS_REQUIRED;
    Locale locale = exchange.getLocaleContext().getLocale();
    exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
    exchange.getResponse().getHeaders().setContentType(MediaType.TEXT_PLAIN);
    exchange.getResponse().getHeaders().set("X-Error-Code", error.name());
    exchange
        .getResponse()
        .getHeaders()
        .set("Content-Language", ApiErrorCode.isChinese(locale) ? "zh-CN" : "en");
    DataBuffer body =
        exchange
            .getResponse()
            .bufferFactory()
            .wrap(error.title(locale).getBytes(StandardCharsets.UTF_8));
    return exchange.getResponse().writeWith(Mono.just(body));
  }
}
