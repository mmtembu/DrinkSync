package com.smarteventbar.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@Order(0)
public class RequestLoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);

    private static final Pattern STATION_ID_PATTERN = Pattern.compile("/api/stations/(\\d+)");
    private static final Pattern ORDER_ID_PATTERN = Pattern.compile("/api/orders/(\\d+)");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String requestId = UUID.randomUUID().toString().substring(0, 8);
        long startTime = System.currentTimeMillis();

        MDC.put("requestId", requestId);

        String sessionId = request.getHeader("X-Session-Id");
        if (sessionId != null) {
            MDC.put("sessionId", sessionId);
        }

        String path = request.getRequestURI();
        extractAndSetMdcIds(path);

        try {
            chain.doFilter(request, response);
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            log.info("HTTP {} {} {} {}ms", request.getMethod(), path, response.getStatus(), duration);
            MDC.clear();
        }
    }

    private void extractAndSetMdcIds(String path) {
        Matcher stationMatcher = STATION_ID_PATTERN.matcher(path);
        if (stationMatcher.find()) {
            MDC.put("stationId", stationMatcher.group(1));
        }

        Matcher orderMatcher = ORDER_ID_PATTERN.matcher(path);
        if (orderMatcher.find()) {
            MDC.put("orderId", orderMatcher.group(1));
        }
    }
}
