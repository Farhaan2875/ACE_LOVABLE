package com.farhaan.projects.Ace.error;

/** The open-model server (Ollama) could not answer. Carries a user-friendly message. */
public class LlmException extends RuntimeException {
    private final int status;

    public LlmException(String message, int status) {
        super(message);
        this.status = status;
    }

    public int getStatus() { return status; }
}
