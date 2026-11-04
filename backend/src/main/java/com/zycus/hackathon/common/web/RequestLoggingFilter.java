package com.zycus.hackathon.common.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** One structured access-log line per request: method, path, status, duration. */
@Slf4j
@Component
@Order(2)
public class RequestLoggingFilter extends OncePerRequestFilter {

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    long start = System.currentTimeMillis();
    try {
      chain.doFilter(request, response);
    } finally {
      long tookMs = System.currentTimeMillis() - start;
      log.info(
          "{} {} -> {} ({}ms)",
          request.getMethod(),
          request.getRequestURI(),
          response.getStatus(),
          tookMs);
    }
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return request.getRequestURI().startsWith("/actuator");
  }
}
