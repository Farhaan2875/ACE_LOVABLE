package com.farhaan.projects.Ace.security;

import com.farhaan.projects.Ace.config.AceProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JwtServiceTest {
    private final JwtService jwt = new JwtService(new AceProperties());

    @Test
    void roundTrip() {
        assertEquals(42L, jwt.verify(jwt.generate(42L, "a@b.com")));
    }

    @Test
    void tamperedOrGarbageTokensAreRejected() {
        String token = jwt.generate(1L, "a@b.com");
        String[] parts = token.split("\\.");
        assertNull(jwt.verify(parts[0] + "." + parts[1] + "x." + parts[2]));
        assertNull(jwt.verify("not-a-token"));
        assertNull(jwt.verify(""));
    }

    @Test
    void shortSecretIsRefused() {
        AceProperties props = new AceProperties();
        props.getSecurity().setJwtSecret("short");
        assertThrows(IllegalStateException.class, () -> new JwtService(props));
    }
}
