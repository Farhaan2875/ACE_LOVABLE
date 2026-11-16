package com.farhaan.projects.Ace.intelligence;

import com.farhaan.projects.Ace.util.Json;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/**
 * Writes Server-Sent Events ("event: name \n data: {json} \n\n") straight to the HTTP response.
 * If the browser disconnects, send() throws UncheckedIOException - the orchestrator uses that to stop the LLM.
 */
public class SseWriter {
    private final HttpServletResponse response;

    public SseWriter(HttpServletResponse response) {
        this.response = response;
    }

    public synchronized void send(String event, Object data) {
        try {
            String frame = "event: " + event + "\ndata: " + Json.write(data) + "\n\n";
            response.getOutputStream().write(frame.getBytes(StandardCharsets.UTF_8));
            response.flushBuffer();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
