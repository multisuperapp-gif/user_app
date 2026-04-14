package com.msa.userapp.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.msa.userapp.common.api.ApiResponse;
import com.msa.userapp.config.ApplicationProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 2)
public class AuthenticatedApiRateLimitFilter extends OncePerRequestFilter {
    private static final String RATE_LIMITED_CODE = "RATE_LIMITED";

    private final ApplicationProperties applicationProperties;
    private final ObjectMapper objectMapper;
    private final Map<String, RateWindow> windows = new ConcurrentHashMap<>();

    public AuthenticatedApiRateLimitFilter(ApplicationProperties applicationProperties, ObjectMapper objectMapper) {
        this.applicationProperties = applicationProperties;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!applicationProperties.rateLimit().enabled()) {
            return true;
        }
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        return endpointRule(request) == null;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        EndpointRule rule = endpointRule(request);
        if (rule == null) {
            filterChain.doFilter(request, response);
            return;
        }

        long windowMillis = applicationProperties.rateLimit().windowSeconds() * 1000L;
        long now = System.currentTimeMillis();
        String clientKey = rule.key() + ":" + clientFingerprint(request);

        RateWindow window = windows.compute(clientKey, (ignored, existing) -> {
            if (existing == null || existing.expiresAtMillis < now) {
                return new RateWindow(1, now + windowMillis);
            }
            return new RateWindow(existing.count + 1, existing.expiresAtMillis);
        });

        pruneExpiredEntries(now);

        if (window.count > rule.maxRequests()) {
            long retryAfterSeconds = Math.max(1L, (window.expiresAtMillis - now + 999L) / 1000L);
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
            response.getWriter().write(objectMapper.writeValueAsString(ApiResponse.failure(RATE_LIMITED_CODE, rule.message())));
            return;
        }

        filterChain.doFilter(request, response);
    }

    private void pruneExpiredEntries(long now) {
        if (windows.size() < 512) {
            return;
        }
        windows.entrySet().removeIf(entry -> entry.getValue().expiresAtMillis < now);
    }

    private String clientFingerprint(HttpServletRequest request) {
        String userId = request.getHeader("X-User-Id");
        if (userId != null && !userId.isBlank()) {
            return "user:" + userId.trim();
        }
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return "ip:" + forwardedFor.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return "ip:" + realIp.trim();
        }
        return "ip:" + request.getRemoteAddr();
    }

    private EndpointRule endpointRule(HttpServletRequest request) {
        ApplicationProperties.RateLimit properties = applicationProperties.rateLimit();
        String requestUri = request.getRequestURI();
        String method = request.getMethod();
        if (requestUri.startsWith("/api/v1/public/")) {
            return null;
        }

        List<EndpointRule> rules = List.of(
                new EndpointRule("POST", "/api/v1/cart/items", "cart-mutation", properties.cartMutationMaxRequests(),
                        "Too many cart updates from this account. Please wait a moment and try again."),
                new EndpointRule("PATCH", "/api/v1/cart/items/", "cart-mutation", properties.cartMutationMaxRequests(),
                        "Too many cart updates from this account. Please wait a moment and try again."),
                new EndpointRule("DELETE", "/api/v1/cart/items/", "cart-mutation", properties.cartMutationMaxRequests(),
                        "Too many cart updates from this account. Please wait a moment and try again."),
                new EndpointRule("POST", "/api/v1/orders/checkout-preview", "checkout-preview", properties.checkoutPreviewMaxRequests(),
                        "Too many checkout preview requests from this account. Please wait a moment and try again."),
                new EndpointRule("POST", "/api/v1/orders/place", "order-place", properties.orderPlaceMaxRequests(),
                        "Too many order placement attempts from this account. Please wait a moment and try again."),
                new EndpointRule("POST", "/api/v1/orders/", "order-cancel", properties.orderCancelMaxRequests(),
                        "Too many order action requests from this account. Please wait a moment and try again."),
                new EndpointRule("POST", "/api/v1/profile/push-tokens", "push-token", properties.pushTokenMaxRequests(),
                        "Too many device registration attempts from this account. Please wait a moment and try again."),
                new EndpointRule("PATCH", "/api/v1/profile/push-tokens/deactivate", "push-token", properties.pushTokenMaxRequests(),
                        "Too many device registration attempts from this account. Please wait a moment and try again."),
                new EndpointRule(null, "/api/v1/", "authenticated-general", properties.generalAuthenticatedMaxRequests(),
                        "Too many requests from this account. Please wait a moment and try again.")
        );

        for (EndpointRule rule : rules) {
            if (rule.matches(method, requestUri)) {
                return rule;
            }
        }
        return null;
    }

    private record EndpointRule(String method, String pathPrefix, String key, int maxRequests, String message) {
        private boolean matches(String requestMethod, String requestUri) {
            if (method != null && !method.equalsIgnoreCase(requestMethod)) {
                return false;
            }
            if (!requestUri.startsWith(pathPrefix)) {
                return false;
            }
            if ("POST".equalsIgnoreCase(method) && "/api/v1/orders/".equals(pathPrefix)) {
                return requestUri.endsWith("/cancel");
            }
            return true;
        }
    }

    private record RateWindow(int count, long expiresAtMillis) {
    }
}
