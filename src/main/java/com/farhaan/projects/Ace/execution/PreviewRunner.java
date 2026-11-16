package com.farhaan.projects.Ace.execution;

import java.util.Map;

/** "Code Execution Service": something that can run a Vite project and give back a URL. */
public interface PreviewRunner {
    /** Starts (or refreshes) the preview and BLOCKS until the app answers. Throws if it cannot start. */
    RunResult start(Long projectId, Map<String, String> files) throws Exception;

    /** Stops the running app but keeps what is needed for a fast restart. */
    void stop(Long projectId);

    /** Stops and removes everything that belongs to the project. */
    void destroy(Long projectId);

    /** Last log lines of the dev server (for debugging). */
    String logs(Long projectId);

    /** false for runners whose processes die with the backend (local): previews must be reset on startup. */
    boolean survivesRestart();
}
