package com.farhaan.projects.Ace.execution;

/** What a runner returns after the preview is up. namespace / podName are only set for Kubernetes. */
public record RunResult(String url, String namespace, String podName) {
}
