package com.farhaan.projects.Ace.util;

import com.farhaan.projects.Ace.error.BadRequestException;

import java.util.regex.Pattern;

/**
 * Validates file paths that come from users or from the LLM.
 * The LLM is NOT trusted: "../../etc/passwd" must never reach the disk or MinIO.
 */
public final class PathUtil {
    private static final Pattern ALLOWED = Pattern.compile("^[A-Za-z0-9_\\-.@/]+$");
    private static final int MAX_LENGTH = 200;
    private static final int MAX_DEPTH = 8;

    private PathUtil() {}

    /** Returns a clean relative path (no leading slash) or throws BadRequestException. */
    public static String normalize(String raw) {
        if (raw == null) throw new BadRequestException("File path is required");
        String path = raw.trim().replace('\\', '/');
        while (path.startsWith("/")) path = path.substring(1);
        while (path.startsWith("./")) path = path.substring(2);

        if (path.isEmpty() || path.length() > MAX_LENGTH) throw new BadRequestException("Invalid file path length");
        if (!ALLOWED.matcher(path).matches()) throw new BadRequestException("File path contains illegal characters: " + path);
        if (path.endsWith("/")) throw new BadRequestException("File path must point to a file: " + path);

        String[] parts = path.split("/");
        if (parts.length > MAX_DEPTH) throw new BadRequestException("File path is nested too deeply: " + path);
        for (String part : parts) {
            if (part.isEmpty() || part.equals(".") || part.equals("..")) throw new BadRequestException("Invalid path segment in: " + path);
            if (part.equals("node_modules") || part.equals(".git")) throw new BadRequestException("Reserved folder in path: " + path);
        }
        return path;
    }

    /** Same as normalize() but returns null instead of throwing (handy for LLM output). */
    public static String normalizeOrNull(String raw) {
        try {
            return normalize(raw);
        } catch (BadRequestException e) {
            return null;
        }
    }

    /** File extension without dot ("src/App.jsx" -> "jsx"), or "" if there is none. */
    public static String extension(String path) {
        int slash = path.lastIndexOf('/');
        int dot = path.lastIndexOf('.');
        return dot > slash ? path.substring(dot + 1).toLowerCase() : "";
    }
}
