package com.farhaan.projects.Ace.error;

/** HTTP 401 - not logged in / wrong credentials. */
public class UnauthorizedException extends RuntimeException {
    public UnauthorizedException(String message) { super(message); }
}
