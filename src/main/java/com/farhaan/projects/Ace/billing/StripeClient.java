package com.farhaan.projects.Ace.billing;

import com.farhaan.projects.Ace.config.AceProperties;
import com.farhaan.projects.Ace.error.BadRequestException;
import com.farhaan.projects.Ace.util.Json;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Minimal Stripe REST client (no SDK needed): create Checkout / Portal sessions and verify webhooks. */
@Component
public class StripeClient {
    private final AceProperties.Billing.Stripe cfg;
    private final HttpClient http;

    public StripeClient(AceProperties props, HttpClient http) {
        this.cfg = props.getBilling().getStripe();
        this.http = http;
    }

    public boolean isConfigured() {
        return cfg.getSecretKey() != null && !cfg.getSecretKey().isBlank();
    }

    /** POST https://api.stripe.com/v1/{path} with form fields; returns the JSON answer. */
    public JsonNode post(String path, Map<String, String> form) {
        String body = form.entrySet().stream()
                .map(e -> URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8) + "=" + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));
        HttpRequest request = HttpRequest.newBuilder(URI.create("https://api.stripe.com/v1/" + path))
                .timeout(Duration.ofSeconds(20))
                .header("Authorization", "Bearer " + cfg.getSecretKey())
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode json = Json.read(response.body());
            if (response.statusCode() >= 400) {
                throw new BadRequestException("Stripe error: " + json.path("error").path("message").asText("unknown"));
            }
            return json;
        } catch (IOException e) {
            throw new BadRequestException("Could not reach Stripe: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BadRequestException("Stripe request interrupted");
        }
    }

    /**
     * Checks the "Stripe-Signature" header: t=timestamp,v1=HMAC_SHA256(secret, "timestamp.payload").
     * This proves the webhook really came from Stripe.
     */
    public boolean verifySignature(String payload, String header) {
        String secret = cfg.getWebhookSecret();
        if (secret == null || secret.isBlank() || header == null) return false;

        long timestamp = 0;
        List<String> signatures = new ArrayList<>();
        for (String part : header.split(",")) {
            String[] kv = part.trim().split("=", 2);
            if (kv.length != 2) continue;
            if (kv[0].equals("t")) {
                try { timestamp = Long.parseLong(kv[1]); } catch (NumberFormatException ignored) { return false; }
            } else if (kv[0].equals("v1")) {
                signatures.add(kv[1]);
            }
        }
        if (timestamp == 0 || signatures.isEmpty()) return false;
        if (Math.abs(Instant.now().getEpochSecond() - timestamp) > 300) return false;   // replay protection (5 min)

        String expected = hmacHex(secret, timestamp + "." + payload);
        for (String sig : signatures) {
            if (MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), sig.getBytes(StandardCharsets.UTF_8))) return true;
        }
        return false;
    }

    private static String hmacHex(String secret, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            StringBuilder hex = new StringBuilder();
            for (byte b : mac.doFinal(data.getBytes(StandardCharsets.UTF_8))) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (Exception e) {
            throw new IllegalStateException("HMAC failure", e);
        }
    }
}
