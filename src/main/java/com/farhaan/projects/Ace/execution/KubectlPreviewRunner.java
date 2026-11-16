package com.farhaan.projects.Ace.execution;

import com.farhaan.projects.Ace.config.AceProperties;
import com.farhaan.projects.Ace.storage.ObjectStorage;
import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Production runner: "execution-service creates a new namespace with a pod" from the architecture.
 * For every project it makes:  Namespace -> Pod (init container downloads the code, then Vite runs on :3000)
 *                              -> Service -> Ingress  (= preview URL  http://project-<id>.<preview-domain>)
 *
 * It calls the `kubectl` command line tool (no Java Kubernetes library = nothing extra to maintain).
 * The pod gets a time-limited download link instead of MinIO credentials, because it runs untrusted AI code.
 */
@Slf4j
public class KubectlPreviewRunner implements PreviewRunner {
    private final AceProperties.Execution cfg;
    private final AceProperties.Execution.Kubernetes k8s;
    private final ObjectStorage storage;

    public KubectlPreviewRunner(AceProperties.Execution cfg, ObjectStorage storage) {
        this.cfg = cfg;
        this.k8s = cfg.getKubernetes();
        this.storage = storage;
    }

    @Override
    public RunResult start(Long projectId, Map<String, String> files) throws Exception {
        String ns = namespace(projectId);

        // 1) bundle the code into one zip in MinIO and create a short-lived download link for the pod
        String key = "bundles/" + projectId + "/bundle.zip";
        storage.put(key, zip(files), "application/zip");
        String downloadUrl = storage.presignedGetUrl(key, Duration.ofMinutes(20));

        // 2) remove the old pod (spec of a pod can't be changed), ignore "not found"
        kubectl(null, 90, "delete", "pod", "preview", "-n", ns, "--ignore-not-found=true", "--wait=true", "--timeout=60s");

        // 3) namespace + pod + service + ingress
        String host = "project-" + projectId + "." + k8s.getPreviewDomain();
        kubectlChecked(manifest(projectId, ns, host, downloadUrl), 60, "apply", "-f", "-");

        // 4) wait until the readiness probe (Vite answering on :3000) succeeds
        kubectlChecked(null, cfg.getStartupTimeoutSeconds() + 30, "wait", "--for=condition=Ready", "pod/preview", "-n", ns,
                "--timeout=" + cfg.getStartupTimeoutSeconds() + "s");
        return new RunResult(k8s.getScheme() + "://" + host, ns, "preview");
    }

    @Override
    public void stop(Long projectId) {
        kubectl(null, 90, "delete", "pod", "preview", "-n", namespace(projectId), "--ignore-not-found=true", "--wait=false");
    }

    @Override
    public void destroy(Long projectId) {
        kubectl(null, 90, "delete", "namespace", namespace(projectId), "--ignore-not-found=true", "--wait=false");
        try {
            storage.delete("bundles/" + projectId + "/bundle.zip");
        } catch (RuntimeException e) {
            log.debug("Bundle cleanup skipped: {}", e.getMessage());
        }
    }

    @Override
    public String logs(Long projectId) {
        return kubectl(null, 30, "logs", "preview", "-n", namespace(projectId), "-c", "dev-server", "--tail=200");
    }

    @Override
    public boolean survivesRestart() {
        return true;   // pods keep running while the backend restarts
    }

    // ------------------------------------------------------------------ helpers

    private String namespace(Long projectId) {
        return k8s.getNamespacePrefix() + projectId;
    }

    private static byte[] zip(Map<String, String> files) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            for (Map.Entry<String, String> f : files.entrySet()) {
                zip.putNextEntry(new ZipEntry(f.getKey()));
                zip.write(f.getValue().getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        return bytes.toByteArray();
    }

    private String manifest(Long projectId, String ns, String host, String downloadUrl) {
        return """
                apiVersion: v1
                kind: Namespace
                metadata:
                  name: {{NS}}
                  labels: {app.kubernetes.io/managed-by: ace, ace-project-id: "{{ID}}"}
                ---
                apiVersion: v1
                kind: Pod
                metadata:
                  name: preview
                  namespace: {{NS}}
                  labels: {app: preview}
                spec:
                  restartPolicy: Never
                  automountServiceAccountToken: false
                  securityContext: {runAsNonRoot: true, runAsUser: 1000, fsGroup: 1000}
                  initContainers:
                  - name: fetch-code
                    image: {{IMAGE}}
                    command: ["/bin/sh", "-c", "wget -q -O /tmp/bundle.zip \\"$BUNDLE_URL\\" && unzip -q /tmp/bundle.zip -d /app"]
                    env:
                    - {name: BUNDLE_URL, value: '{{URL}}'}
                    volumeMounts: [{name: app, mountPath: /app}]
                  containers:
                  - name: dev-server
                    image: {{IMAGE}}
                    workingDir: /app
                    command: ["/bin/sh", "-c", "npm install --no-audit --no-fund && npm run dev -- --host 0.0.0.0 --port 3000"]
                    env:
                    - {name: HOME, value: /tmp}
                    ports: [{containerPort: 3000}]
                    resources:
                      requests: {cpu: 100m, memory: 256Mi}
                      limits: {cpu: "{{CPU}}", memory: {{MEM}}}
                    readinessProbe:
                      httpGet: {path: /, port: 3000}
                      initialDelaySeconds: 5
                      periodSeconds: 3
                      failureThreshold: 200
                    volumeMounts: [{name: app, mountPath: /app}]
                  volumes:
                  - {name: app, emptyDir: {}}
                ---
                apiVersion: v1
                kind: Service
                metadata: {name: preview, namespace: {{NS}}}
                spec:
                  selector: {app: preview}
                  ports: [{port: 80, targetPort: 3000}]
                ---
                apiVersion: networking.k8s.io/v1
                kind: Ingress
                metadata: {name: preview, namespace: {{NS}}}
                spec:
                  ingressClassName: {{CLASS}}
                  rules:
                  - host: {{HOST}}
                    http:
                      paths:
                      - {path: /, pathType: Prefix, backend: {service: {name: preview, port: {number: 80}}}}
                """
                .replace("{{NS}}", ns)
                .replace("{{ID}}", String.valueOf(projectId))
                .replace("{{IMAGE}}", k8s.getNodeImage())
                .replace("{{URL}}", downloadUrl.replace("'", "''"))
                .replace("{{CPU}}", k8s.getCpuLimit())
                .replace("{{MEM}}", k8s.getMemoryLimit())
                .replace("{{CLASS}}", k8s.getIngressClass())
                .replace("{{HOST}}", host);
    }

    private String kubectlChecked(String stdin, int timeoutSeconds, String... args) {
        String out = kubectl(stdin, timeoutSeconds, args);
        if (out.startsWith("ERROR:")) throw new IllegalStateException("kubectl " + args[0] + " failed: " + out);
        return out;
    }

    /** Runs kubectl; returns its output, or "ERROR: ..." if it failed. */
    private String kubectl(String stdin, int timeoutSeconds, String... args) {
        List<String> cmd = new ArrayList<>();
        cmd.add(k8s.getKubectl());
        cmd.addAll(List.of(args));
        try {
            Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
            if (stdin != null) {
                try (OutputStream out = p.getOutputStream()) {
                    out.write(stdin.getBytes(StandardCharsets.UTF_8));
                }
            } else {
                p.getOutputStream().close();
            }
            String output = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (!p.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                return "ERROR: kubectl timed out";
            }
            return p.exitValue() == 0 ? output : "ERROR: " + output.trim();
        } catch (Exception e) {
            return "ERROR: " + e.getMessage();
        }
    }
}
