package com.farhaan.projects.Ace.intelligence;

import com.farhaan.projects.Ace.config.AceProperties;
import com.farhaan.projects.Ace.util.Json;
import com.farhaan.projects.Ace.util.PathUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The "RAG" box: stores the codebase as vectors in Qdrant ("chunk and embed and ingest")
 * and finds the most relevant code for a question ("similarity search").
 * Everything here FAILS SOFT: if Qdrant or the embedding model is down, chat still works - just without extra context.
 */
@Slf4j
@Service
public class RagService {
    private static final Set<String> INDEXABLE = Set.of("js", "jsx", "ts", "tsx", "css", "html", "json", "md", "svg");

    public record Snippet(String path, int startLine, String text, double score) {}

    private final AceProperties.Rag cfg;
    private final OllamaClient ollama;
    private final HttpClient http;
    private final ExecutorService executor;
    private volatile boolean collectionReady = false;
    private final AtomicBoolean warned = new AtomicBoolean(false);

    public RagService(AceProperties props, OllamaClient ollama, HttpClient http, ExecutorService aceExecutor) {
        this.cfg = props.getRag();
        this.ollama = ollama;
        this.http = http;
        this.executor = aceExecutor;
    }

    // ------------------------------------------------------------------ ingest

    /** Re-indexes the given files in the background so the chat answer is not delayed. */
    public void indexFilesAsync(Long projectId, Map<String, String> files) {
        if (!cfg.isEnabled() || files.isEmpty()) return;
        executor.submit(() -> {
            try {
                indexFiles(projectId, files);
            } catch (Exception e) {
                warnOnce("RAG indexing skipped: " + e.getMessage());
            }
        });
    }

    private void indexFiles(Long projectId, Map<String, String> files) throws Exception {
        ensureCollection();
        for (Map.Entry<String, String> file : files.entrySet()) {
            String path = file.getKey();
            if (!INDEXABLE.contains(PathUtil.extension(path)) || file.getValue().length() > 200_000) continue;

            deleteFile(projectId, path);                               // drop the old version's chunks
            List<String> lines = file.getValue().lines().toList();
            List<String> texts = new ArrayList<>();
            List<Integer> starts = new ArrayList<>();
            int step = Math.max(1, cfg.getChunkLines() - cfg.getChunkOverlap());
            for (int start = 0; start < lines.size(); start += step) {
                int end = Math.min(lines.size(), start + cfg.getChunkLines());
                texts.add("File: " + path + " (line " + (start + 1) + ")\n" + String.join("\n", lines.subList(start, end)));
                starts.add(start + 1);
                if (end == lines.size()) break;
            }
            for (int i = 0; i < texts.size(); i += 16) {              // embed in small batches
                int to = Math.min(texts.size(), i + 16);
                List<float[]> vectors = ollama.embed(texts.subList(i, to));
                upsert(projectId, path, i, texts.subList(i, to), starts.subList(i, to), vectors);
            }
        }
    }

    private void upsert(Long projectId, String path, int offset, List<String> texts, List<Integer> starts, List<float[]> vectors) throws Exception {
        ObjectNode body = Json.mapper().createObjectNode();
        ArrayNode points = body.putArray("points");
        for (int i = 0; i < texts.size() && i < vectors.size(); i++) {
            ObjectNode point = points.addObject();
            // same project+path+chunk always gives the same id, so re-indexing overwrites instead of duplicating
            point.put("id", UUID.nameUUIDFromBytes((projectId + ":" + path + ":" + (offset + i)).getBytes(StandardCharsets.UTF_8)).toString());
            ArrayNode vec = point.putArray("vector");
            for (float f : vectors.get(i)) vec.add(f);
            ObjectNode payload = point.putObject("payload");
            payload.put("project_id", projectId);
            payload.put("path", path);
            payload.put("start_line", starts.get(i));
            payload.put("text", texts.get(i));
        }
        call("PUT", "/collections/" + cfg.getCollection() + "/points?wait=true", body.toString());
    }

    private void deleteFile(Long projectId, String path) throws Exception {
        ObjectNode body = Json.mapper().createObjectNode();
        ArrayNode must = body.putObject("filter").putArray("must");
        must.addObject().put("key", "project_id").putObject("match").put("value", projectId);
        must.addObject().put("key", "path").putObject("match").put("value", path);
        call("POST", "/collections/" + cfg.getCollection() + "/points/delete?wait=true", body.toString());
    }

    // ------------------------------------------------------------------ search

    /** Top-k code chunks of this project that are most similar to the question. Empty list on any problem. */
    public List<Snippet> search(Long projectId, String query) {
        if (!cfg.isEnabled()) return List.of();
        try {
            ensureCollection();
            List<float[]> vectors = ollama.embed(List.of(query));
            if (vectors.isEmpty()) return List.of();

            ObjectNode body = Json.mapper().createObjectNode();
            ArrayNode vec = body.putArray("vector");
            for (float f : vectors.get(0)) vec.add(f);
            body.put("limit", cfg.getTopK());
            body.put("with_payload", true);
            ArrayNode must = body.putObject("filter").putArray("must");
            must.addObject().put("key", "project_id").putObject("match").put("value", projectId);   // never mix projects!

            JsonNode result = Json.read(call("POST", "/collections/" + cfg.getCollection() + "/points/search", body.toString()));
            List<Snippet> snippets = new ArrayList<>();
            for (JsonNode hit : result.path("result")) {
                JsonNode p = hit.path("payload");
                snippets.add(new Snippet(p.path("path").asText(), p.path("start_line").asInt(1), p.path("text").asText(), hit.path("score").asDouble()));
            }
            return snippets;
        } catch (Exception e) {
            warnOnce("RAG search skipped: " + e.getMessage());
            return List.of();
        }
    }

    // ------------------------------------------------------------------ Qdrant plumbing

    private void ensureCollection() throws Exception {
        if (collectionReady) return;
        HttpResponse<String> get = send("GET", "/collections/" + cfg.getCollection(), null);
        if (get.statusCode() == 404) {
            call("PUT", "/collections/" + cfg.getCollection(),
                    "{\"vectors\":{\"size\":" + cfg.getVectorSize() + ",\"distance\":\"Cosine\"}}");
            // an index makes the project_id filter fast
            call("PUT", "/collections/" + cfg.getCollection() + "/index?wait=true",
                    "{\"field_name\":\"project_id\",\"field_schema\":\"integer\"}");
        } else if (get.statusCode() >= 400) {
            throw new IOException("Qdrant answered " + get.statusCode());
        }
        collectionReady = true;
    }

    private String call(String method, String path, String body) throws Exception {
        HttpResponse<String> response = send(method, path, body);
        if (response.statusCode() >= 400) throw new IOException("Qdrant " + response.statusCode() + ": " + response.body());
        return response.body();
    }

    private HttpResponse<String> send(String method, String path, String body) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(cfg.getQdrantUrl() + path))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json");
        if (cfg.getApiKey() != null && !cfg.getApiKey().isBlank()) builder.header("api-key", cfg.getApiKey());
        builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        return http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private void warnOnce(String message) {
        if (warned.compareAndSet(false, true)) log.warn("{} (further RAG problems are logged at debug level)", message);
        else log.debug(message);
    }
}
