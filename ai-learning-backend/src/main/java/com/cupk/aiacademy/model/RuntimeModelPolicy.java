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
        URI uri;
        try {
            uri = URI.create(endpoint.getBaseUrl());
            String host = uri.getHost();
            if (host == null || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null
                    || !("https".equalsIgnoreCase(uri.getScheme()) || ("http".equalsIgnoreCase(uri.getScheme())
                    && Set.of("localhost", "127.0.0.1", "::1", "[::1]").contains(host.toLowerCase(Locale.ROOT)))))
                throw new IllegalArgumentException();
        } catch (IllegalArgumentException e) { throw new ModelConfigurationException("MODEL_ENDPOINT_INVALID"); }
        String host = uri.getHost().toLowerCase(Locale.ROOT).replaceAll("\\.+$", "");
        if (host.equals("openai.com") || host.endsWith(".openai.com") || blockedModel(endpoint.getModel())
                || (requestedModel != null && !requestedModel.isBlank() && blockedModel(requestedModel)))
            throw new ModelConfigurationException("MODEL_BLOCKED");
    }

    private static boolean blockedModel(String model) {
        String value = model.strip().toLowerCase(Locale.ROOT);
        for (String part : value.split("[/.:]")) {
            if (part.equals("openai") || part.startsWith("gpt") || part.startsWith("chatgpt")
                    || part.matches("o[134](-.*)?") || part.startsWith("text-embedding-")
                    || part.startsWith("text-davinci") || part.startsWith("davinci")) return true;
        }
        return false;
    }
}
