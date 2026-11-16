package com.farhaan.projects.Ace.error;

/** HTTP 402 - the user's plan does not allow this (too many projects, daily tokens used up...). */
public class PlanLimitException extends RuntimeException {
    public PlanLimitException(String message) { super(message); }
}
