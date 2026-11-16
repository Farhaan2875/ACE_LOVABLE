package com.farhaan.projects.Ace.intelligence;

import com.farhaan.projects.Ace.config.AceProperties;
import com.farhaan.projects.Ace.error.LlmException;
import com.farhaan.projects.Ace.util.Json;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Talks to Ollama (https://ollama.com), which runs the open model locally. */
@Component
@RequiredArgsConstructor
public class OllamaClient {
    private final AceProperties props;
    private final HttpClient http;

    public record ToolCall(String name, JsonNode arguments) {}

    public record ChatResult(String content, List<ToolCall> toolCalls, int promptTokens, int completionTokens) {}

    /**
     * Streaming chat. Every piece of text is passed to onToken as it arrives.
     * Returns the full text, any tool calls the model wants to make, and token counts.
     */
    public ChatResult chat(ArrayNode messages, boolean withTools, Consumer<String> onToken) {
        AceProperties.Llm cfg = props.getLlm();
        ObjectNode body = Json.mapper().createObjectNode();
        body.put("model", cfg.getModel());
        body.set("messages", messages);
        body.put("stream", true);
        if (withTools) body.set("tools", ToolDefinitions.json());
        if (cfg.getThink() != null) body.put("think", cfg.getThink());
        ObjectNode options = body.putObject("options");
        options.put("temperature", cfg.getTemperature());
        options.put("num_ctx", cfg.getNumCtx());
        options.put("num_predict", cfg.getNumPredict());

        HttpRequest request = HttpRequest.newBuilder(URI.create(cfg.getBaseUrl() + "/api/chat"))
                .timeout(Duration.ofMinutes(10))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();
        try {
            HttpResponse<Stream<String>> response = http.send(request, HttpResponse.BodyHandlers.ofLines());
            if (response.statusCode() != 200) {
                String err = response.body().collect(Collectors.joining("\n"));
                throw new LlmException(errorText(err, response.statusCode(), cfg.getModel()), response.statusCode());
            }

            StringBuilder content = new StringBuilder();
            List<ToolCall> calls = new ArrayList<>();
            int promptTokens = 0, completionTokens = 0;

            // Ollama streams one JSON object per line (NDJSON). Closing the stream cancels the generation.
            try (Stream<String> lines = response.body()) {
                Iterator<String> it = lines.iterator();
                while (it.hasNext()) {
                    String line = it.next();
                    if (line.isBlank()) continue;
                    JsonNode node = Json.read(line);
                    if (node.has("error")) throw new LlmException(node.path("error").asText(), 500);

                    JsonNode msg = node.path("message");
                    String delta = msg.path("content").asText("");
                    if (!delta.isEmpty()) {
                        content.append(delta);
                        onToken.accept(delta);
                    }
                    for (JsonNode tc : msg.path("tool_calls")) {
                        JsonNode fn = tc.path("function");
                        JsonNode args = fn.path("arguments");
                        if (args.isTextual()) {
                            try { args = Json.read(args.asText()); } catch (IllegalArgumentException ignored) { }
                        }
                        calls.add(new ToolCall(fn.path("name").asText(), args));
                    }
                    if (node.path("done").asBoolean(false)) {
                        promptTokens = node.path("prompt_eval_count").asInt(0);
                        completionTokens = node.path("eval_count").asInt(0);
                    }
                }
            }
            return new ChatResult(content.toString(), calls, promptTokens, completionTokens);
        } catch (IOException e) {
            throw new LlmException("Cannot reach the model server at " + cfg.getBaseUrl()
                    + ". Is Ollama running (ollama serve)? " + e.getMessage(), 503);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LlmException("Request was interrupted", 500);
        }
    }

    /** Turns texts into vectors for the RAG search. */
    public List<float[]> embed(List<String> inputs) {
        AceProperties.Llm cfg = props.getLlm();
        ObjectNode body = Json.mapper().createObjectNode();
        body.put("model", props.getRag().getEmbeddingModel());
        ArrayNode input = body.putArray("input");
        inputs.forEach(input::add);

        HttpRequest request = HttpRequest.newBuilder(URI.create(cfg.getBaseUrl() + "/api/embed"))
                .timeout(Duration.ofMinutes(2))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();
        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new LlmException(errorText(response.body(), response.statusCode(), props.getRag().getEmbeddingModel()), response.statusCode());
            }
            List<float[]> result = new ArrayList<>();
            for (JsonNode vec : Json.read(response.body()).path("embeddings")) {
                float[] v = new float[vec.size()];
                for (int i = 0; i < v.length; i++) v[i] = (float) vec.get(i).asDouble();
                result.add(v);
            }
            return result;
        } catch (IOException e) {
            throw new LlmException("Cannot reach the model server: " + e.getMessage(), 503);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LlmException("Request was interrupted", 500);
        }
    }

    private static String errorText(String body, int status, String model) {
        String msg = body;
        try { msg = Json.read(body).path("error").asText(body); } catch (RuntimeException ignored) { }
        if (status == 404) msg += " - run:  ollama pull " + model;
        return "Model server error (" + status + "): " + msg;
    }
}
