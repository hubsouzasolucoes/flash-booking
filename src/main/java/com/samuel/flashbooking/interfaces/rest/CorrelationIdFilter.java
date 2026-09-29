package com.samuel.flashbooking.interfaces.rest;

import com.samuel.flashbooking.application.CorrelationIds;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
public class CorrelationIdFilter extends OncePerRequestFilter {
    public static final String HEADER = "X-Correlation-Id";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        UUID id = parse(request.getHeader(HEADER));
        CorrelationIds.set(id);
        MDC.put("correlationId", id.toString());
        response.setHeader(HEADER, id.toString());
        try {
            chain.doFilter(request, response);
        } finally {
            CorrelationIds.clear();
            MDC.remove("correlationId");
        }
    }

    private UUID parse(String value) {
        if (value == null) return UUID.randomUUID();
        try { return UUID.fromString(value); }
        catch (IllegalArgumentException ignored) { return UUID.randomUUID(); }
    }
}
