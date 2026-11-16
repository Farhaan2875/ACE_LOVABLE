package com.farhaan.projects.Ace.error;

/** HTTP 403 - logged in, but not allowed to do this. */
public class ForbiddenException extends RuntimeException {
    public ForbiddenException(String message) { super(message); }
}
