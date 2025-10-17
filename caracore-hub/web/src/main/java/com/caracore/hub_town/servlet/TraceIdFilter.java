package com.caracore.hub_town.servlet;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.logging.log4j.ThreadContext;

import java.io.IOException;
import java.util.UUID;

@WebFilter("/*")
public class TraceIdFilter implements Filter {
    private static final String TRACE_ID_HEADER = "X-Trace-Id";
    private static final String CORRELATION_HEADER = "X-Correlation-Id";

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        if (!(request instanceof HttpServletRequest) || !(response instanceof HttpServletResponse)) {
            chain.doFilter(request, response);
            return;
        }

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        String traceId = resolveTraceId(httpRequest);
        ThreadContext.put("trace_id", traceId);
        ThreadContext.put("http_method", httpRequest.getMethod());
        ThreadContext.put("path", httpRequest.getRequestURI());
        httpRequest.setAttribute("trace_id", traceId);
        httpResponse.setHeader(TRACE_ID_HEADER, traceId);

        try {
            chain.doFilter(request, response);
        } finally {
            ThreadContext.remove("trace_id");
            ThreadContext.remove("http_method");
            ThreadContext.remove("path");
        }
    }

    private String resolveTraceId(HttpServletRequest request) {
        String traceId = normalize(request.getHeader(TRACE_ID_HEADER));
        if (traceId == null) {
            traceId = normalize(request.getHeader(CORRELATION_HEADER));
        }
        if (traceId == null) {
            traceId = parseTraceParent(request.getHeader("traceparent"));
        }
        return traceId != null ? traceId : UUID.randomUUID().toString();
    }

    private String normalize(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }

    private String parseTraceParent(String header) {
        String value = normalize(header);
        if (value == null) {
            return null;
        }
        String[] parts = value.split("-");
        if (parts.length >= 2) {
            return parts[1];
        }
        return value;
    }
}
