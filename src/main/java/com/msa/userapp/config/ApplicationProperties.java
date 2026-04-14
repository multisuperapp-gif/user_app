package com.msa.userapp.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record ApplicationProperties(
        Security security,
        RateLimit rateLimit
) {
    public record Security(
            String accessTokenSecret
    ) {
    }

    public record RateLimit(
            boolean enabled,
            long windowSeconds,
            int generalAuthenticatedMaxRequests,
            int cartMutationMaxRequests,
            int checkoutPreviewMaxRequests,
            int orderPlaceMaxRequests,
            int orderCancelMaxRequests,
            int pushTokenMaxRequests
    ) {
    }
}
