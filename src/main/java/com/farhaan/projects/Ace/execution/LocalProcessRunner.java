package com.farhaan.projects.Ace.execution;

import com.farhaan.projects.Ace.config.AceProperties;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * Development runner: runs `npm install` + the Vite dev server as a normal process on this machine
 * (needs Node.js installed). One folder and one port per project.
 * When only source files change, Vite's hot reload updates the open preview by itself.
 */
@Slf4j
public class LocalProcessRunner implements PreviewRunner, AutoCloseable {
    private static final boolean WINDOWS = System.getProperty("os.name").toLowerCase().contains("win");

    private static final class Running {
        Process process;
        int port;
        final Deque<String> log = new ArrayDeque<>();
    }

    private final AceProperties.Execution cfg;
    private final Path workDir;
    private final Map<Long, Running> running = new ConcurrentHashMap<>();
    private final Map<Long, Deque<String>> lastLogs = new ConcurrentHashMap<>();
    private final HttpClient probe = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build();

    public LocalProcessRunner(AceProperties.Execution cfg) {
        this.cfg = cfg;
        this.workDir = Path.of(cfg.getLocal().getWorkDir()).toAbsolutePath().normalize();
    }

    @Override
    public RunResult start(Long projectId, Map<String, String> files) throws Exception {
        Path dir = workDir.resolve("project-" + projectId);
        Files.createDirectories(dir);

        boolean dependenciesChanged = syncFiles(dir, files);
        Running current = running.get(projectId);

        // App already running and only source code changed -> Vite hot reload does the rest
        if (current != null && current.process.isAlive() && !dependenciesChanged) {
            return new RunResult(url(current.port), null, null);
        }
        if (current != null) kill(projectId);

        Deque<String> log = lastLogs.computeIfAbsent(projectId, id -> new ArrayDeque<>());
        if (dependenciesChanged || !Files.isDirectory(dir.resolve("node_modules"))) {
            append(log, "$ npm install");
            int exit = runAndWait(dir, log, 300, "npm", "install", "--no-audit", "--no-fund");
            if (exit != 0) throw new IllegalStateException("npm install failed (exit code " + exit + "). " + tail(log, 8));
        }

        int port = allocatePort();
        append(log, "$ npm run dev (port " + port + ")");
        Process process = new ProcessBuilder(command("npm", "run", "dev", "--", "--host", "0.0.0.0", "--port", String.valueOf(port), "--strictPort"))
                .directory(dir.toFile()).redirectErrorStream(true).start();
        Running r = new Running();
        r.process = process;
        r.port = port;
        running.put(projectId, r);
        Thread.ofVirtual().start(() -> pump(process, log));

        waitUntilReady(r, log);
        return new RunResult(url(port), null, null);
    }

    @Override
    public void stop(Long projectId) {
        kill(projectId);
    }

    @Override
    public void destroy(Long projectId) {
        kill(projectId);
        lastLogs.remove(projectId);
        Path dir = workDir.resolve("project-" + projectId);
        if (Files.isDirectory(dir)) {
            try (Stream<Path> walk = Files.walk(dir)) {
                walk.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
            } catch (IOException e) {
                log.warn("Could not delete {}: {}", dir, e.getMessage());
            }
        }
    }

    @Override
    public String logs(Long projectId) {
        Deque<String> log = lastLogs.get(projectId);
        return log == null ? "" : tail(log, 200);
    }

    @Override
    public boolean survivesRestart() {
        return false;
    }

    @Override
    public void close() {
        new ArrayList<>(running.keySet()).forEach(this::kill);
    }

    // ------------------------------------------------------------------ helpers

    /** Writes changed files, deletes removed ones. Returns true if package.json changed (= npm install needed). */
    private boolean syncFiles(Path dir, Map<String, String> files) throws IOException {
        boolean packageChanged = false;
        for (Map.Entry<String, String> f : files.entrySet()) {
            Path target = dir.resolve(f.getKey()).normalize();
            if (!target.startsWith(dir)) throw new IOException("Illegal path: " + f.getKey());   // defence in depth
            String old = Files.exists(target) ? Files.readString(target, StandardCharsets.UTF_8) : null;
            if (!f.getValue().equals(old)) {
                Files.createDirectories(target.getParent());
                Files.writeString(target, f.getValue(), StandardCharsets.UTF_8);
                if (f.getKey().equals("package.json")) packageChanged = true;
            }
        }
        try (Stream<Path> walk = Files.walk(dir)) {            // remove files the project no longer has
            for (Path p : walk.filter(Files::isRegularFile).toList()) {
                String rel = dir.relativize(p).toString().replace('\\', '/');
                if (rel.startsWith("node_modules/") || rel.startsWith("dist/") || rel.equals("package-lock.json")) continue;
                if (!files.containsKey(rel)) Files.delete(p);
            }
        }
        return packageChanged;
    }

    private static List<String> command(String... args) {
        List<String> cmd = new ArrayList<>();
        if (WINDOWS) { cmd.add("cmd"); cmd.add("/c"); }
        cmd.addAll(List.of(args));
        return cmd;
    }

    private int runAndWait(Path dir, Deque<String> log, int timeoutSeconds, String... args) throws Exception {
        Process p = new ProcessBuilder(command(args)).directory(dir.toFile()).redirectErrorStream(true).start();
        Thread.ofVirtual().start(() -> pump(p, log));
        if (!p.waitFor(timeoutSeconds, java.util.concurrent.TimeUnit.SECONDS)) {
            p.destroyForcibly();
            throw new IllegalStateException("Command timed out: " + String.join(" ", args));
        }
        return p.exitValue();
    }

    private void pump(Process process, Deque<String> log) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) append(log, line);
        } catch (IOException ignored) {
            // process ended
        }
    }

    private void waitUntilReady(Running r, Deque<String> log) throws Exception {
        long deadline = System.currentTimeMillis() + cfg.getStartupTimeoutSeconds() * 1000L;
        while (System.currentTimeMillis() < deadline) {
            if (!r.process.isAlive()) throw new IllegalStateException("Dev server stopped unexpectedly. " + tail(log, 10));
            try {
                probe.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + r.port + "/")).timeout(Duration.ofSeconds(2)).build(),
                        HttpResponse.BodyHandlers.discarding());
                return;                                         // any answer means the server is up
            } catch (IOException notYet) {
                Thread.sleep(500);
            }
        }
        kill(running.entrySet().stream().filter(e -> e.getValue() == r).map(Map.Entry::getKey).findFirst().orElse(-1L));
        throw new IllegalStateException("Dev server did not start in time. " + tail(log, 10));
    }

    private synchronized int allocatePort() {
        Set<Integer> used = new HashSet<>();
        running.values().forEach(r -> used.add(r.port));
        for (int port = cfg.getLocal().getPortStart(); port <= cfg.getLocal().getPortEnd(); port++) {
            if (used.contains(port)) continue;
            try (ServerSocket s = new ServerSocket(port)) {
                return port;
            } catch (IOException busy) {
                // try next
            }
        }
        throw new IllegalStateException("No free preview port between " + cfg.getLocal().getPortStart() + " and " + cfg.getLocal().getPortEnd());
    }

    private void kill(Long projectId) {
        Running r = running.remove(projectId);
        if (r == null) return;
        r.process.descendants().forEach(ProcessHandle::destroy);   // npm starts node as a child: stop both
        r.process.destroy();
    }

    private String url(int port) {
        return "http://" + cfg.getLocal().getPublicHost() + ":" + port;
    }

    private static void append(Deque<String> log, String line) {
        synchronized (log) {
            log.addLast(line);
            while (log.size() > 400) log.removeFirst();
        }
    }

    private static String tail(Deque<String> log, int lines) {
        synchronized (log) {
            List<String> all = new ArrayList<>(log);
            return String.join("\n", all.subList(Math.max(0, all.size() - lines), all.size()));
        }
    }
}
