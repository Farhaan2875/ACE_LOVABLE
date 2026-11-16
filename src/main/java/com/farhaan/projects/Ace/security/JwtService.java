package com.farhaan.projects.Ace.security;

import com.farhaan.projects.Ace.config.AceProperties;
import com.farhaan.projects.Ace.util.Json;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Creates and checks JWT tokens (HS256) without any extra library.
 * A JWT is:  base64url(header) . base64url(payload) . base64url(HMAC-SHA256(header.payload, secret))
 */
@Service
public class JwtService {
    private static final Logger log = LoggerFactory.getLogger(JwtService.class);
    private static final String DEFAULT_SECRET = "change-me-change-me-change-me-change-me-please";
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    private final byte[] secret;
    private final long ttlSeconds;

    public JwtService(AceProperties props) {
        String configured = props.getSecurity().getJwtSecret();
        if (configured == null || configured.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("ace.security.jwt-secret must be at least 32 characters long");
        }
        if (DEFAULT_SECRET.equals(configured)) {
            log.warn("Using the DEFAULT JWT secret - set ACE_SECURITY_JWT_SECRET before deploying anywhere real!");
        }
        this.secret = configured.getBytes(StandardCharsets.UTF_8);
        this.ttlSeconds = props.getSecurity().getJwtExpirationMinutes() * 60;
    }

    public String generate(Long userId, String username) {
        long now = Instant.now().getEpochSecond();
        Map<String, Object> header = new LinkedHashMap<>();
        header.put("alg", "HS256");
        header.put("typ", "JWT");
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sub", String.valueOf(userId));
        payload.put("username", username);
        payload.put("iat", now);
        payload.put("exp", now + ttlSeconds);

        String unsigned = encode(Json.write(header)) + "." + encode(Json.write(payload));
        return unsigned + "." + ENCODER.encodeToString(sign(unsigned));
    }

    /** Returns the user id if the token is valid and not expired, otherwise null. */
    public Long verify(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3) return null;

            byte[] expected = sign(parts[0] + "." + parts[1]);
            byte[] actual = DECODER.decode(parts[2]);
            if (!MessageDigest.isEqual(expected, actual)) return null;       // constant-time compare

            JsonNode payload = Json.read(new String(DECODER.decode(parts[1]), StandardCharsets.UTF_8));
            if (payload.path("exp").asLong(0) < Instant.now().getEpochSecond()) return null;
            return Long.parseLong(payload.path("sub").asText());
        } catch (RuntimeException e) {
            return null;                                                      // malformed token = not authenticated
        }
    }

    private static String encode(String text) {
        return ENCODER.encodeToString(text.getBytes(StandardCharsets.UTF_8));
    }

    private byte[] sign(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("HMAC failure", e);
        }
    }
}
