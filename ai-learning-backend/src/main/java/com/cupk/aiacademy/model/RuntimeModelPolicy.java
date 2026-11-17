package com.cupk.aiacademy.model;

import java.net.URI;
import java.util.Locale;
import java.util.Set;

public final class RuntimeModelPolicy {
    private RuntimeModelPolicy() {}

    public static String status(RuntimeModelProperties.Endpoint endpoint) {
        try { requireReady(endpoint, null); return "CONFIGURED_UNVERIFIED"; }
        catch (ModelConfigurationException e) { return e.code(); }
    }

    public static void requireReady(RuntimeModelProperties.Endpoint endpoint, String requestedModel) {
        if (!endpoint.isEnabled()) throw new ModelConfigurationException("MODEL_DISABLED");
        if (endpoint.getBaseUrl().isBlank() || endpoint.getModel().isBlank()
                || endpoint.getApiKey().isBlank() || "NOT_CONFIGURED".equals(endpoint.getApiKey()))
            throw new ModelConfigurationException("MODEL_NOT_CONFIGURED");
        requireAllowedEndpoint(endpoint.getBaseUrl(), System.getenv().getOrDefault("INSTALLATION_MODEL_HTTP_ALLOWLIST", ""));
    }

    public static void requireAllowedEndpoint(String baseUrl, String httpAllowlist) {
        try {
            URI uri = URI.create(baseUrl);
            String host = uri.getHost();
            boolean allowedHttp = host != null && (Set.of("localhost", "127.0.0.1", "::1", "[::1]")
                    .contains(host.toLowerCase(Locale.ROOT))
                    || java.util.Arrays.stream(httpAllowlist.split(",")).map(String::strip)
                    .filter(s -> !s.isEmpty()).anyMatch(s -> s.equalsIgnoreCase(host)));
            if (host == null || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null
                    || uri.getPort() == 0 || uri.getPort() > 65535
                    || !("https".equalsIgnoreCase(uri.getScheme()) || ("http".equalsIgnoreCase(uri.getScheme())
                    && allowedHttp)))
                throw new IllegalArgumentException();
        } catch (IllegalArgumentException e) { throw new ModelConfigurationException("MODEL_ENDPOINT_INVALID"); }
    }
}
