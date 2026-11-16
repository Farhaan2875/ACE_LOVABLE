package com.farhaan.projects.Ace.intelligence;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Splits the LLM's streamed answer into "chat text" and "files".
 *
 * The model writes files like this:
 *     <ace-file path="src/App.jsx"> ...code... </ace-file>
 * Tokens arrive in tiny pieces, so a tag can be cut in half ("<ace-f" + "ile ...").
 * This parser holds back any text that might be the start of a tag until it knows more.
 * It has no dependencies, so it is easy to unit test.
 */
public class StreamingFileParser {

    public interface Listener {
        void onText(String text);                                      // normal chat text
        void onFileStart(String path);                                 // "file created"
        void onFileChunk(String path, String chunk);                   // "content added"
        void onFileEnd(String path, String content, boolean complete); // complete=false -> the model stopped mid-file
    }

    private static final String OPEN = "<ace-file";
    private static final String CLOSE = "</ace-file>";
    private static final Pattern PATH = Pattern.compile("path\\s*=\\s*[\"']([^\"']+)[\"']");

    private final Listener listener;
    private final StringBuilder buffer = new StringBuilder();
    private final StringBuilder fileContent = new StringBuilder();
    private boolean inFile = false;
    private String currentPath;

    public StreamingFileParser(Listener listener) {
        this.listener = listener;
    }

    /** Feed the next piece of streamed text. */
    public void feed(String chunk) {
        buffer.append(chunk);
        process(false);
    }

    /** Call once when the model is done: flushes held-back text and closes an unfinished file. */
    public void finish() {
        process(true);
    }

    private void process(boolean flushAll) {
        boolean progress = true;
        while (progress) {
            progress = false;
            if (!inFile) {
                int open = buffer.indexOf(OPEN);
                if (open >= 0) {
                    int gt = buffer.indexOf(">", open);
                    if (gt < 0) {                        // the tag is not complete yet
                        emitText(open);                  // text before the tag can go out now
                        if (flushAll) emitText(buffer.length());
                        return;
                    }
                    String tag = buffer.substring(open, gt + 1);
                    emitText(open);
                    buffer.delete(0, tag.length());
                    Matcher m = PATH.matcher(tag);
                    if (m.find()) {
                        inFile = true;
                        currentPath = m.group(1);
                        fileContent.setLength(0);
                        listener.onFileStart(currentPath);
                    } else {
                        listener.onText(tag);            // malformed tag: show it as text instead of losing it
                    }
                    progress = true;
                } else {
                    emitText(flushAll ? buffer.length() : buffer.length() - partialSuffix(OPEN));
                }
            } else {
                int close = buffer.indexOf(CLOSE);
                if (close >= 0) {
                    appendFile(close);
                    buffer.delete(0, CLOSE.length());
                    endFile(true);
                    progress = true;
                } else {
                    appendFile(flushAll ? buffer.length() : buffer.length() - partialSuffix(CLOSE));
                }
            }
        }
        if (flushAll && inFile) endFile(false);
    }

    /** How many characters at the end of the buffer could be the beginning of `marker`. */
    private int partialSuffix(String marker) {
        int max = Math.min(marker.length() - 1, buffer.length());
        for (int k = max; k > 0; k--) {
            if (marker.startsWith(buffer.substring(buffer.length() - k))) return k;
        }
        return 0;
    }

    private void emitText(int length) {
        if (length <= 0) return;
        listener.onText(buffer.substring(0, length));
        buffer.delete(0, length);
    }

    private void appendFile(int length) {
        if (length <= 0) return;
        String piece = buffer.substring(0, length);
        buffer.delete(0, length);
        fileContent.append(piece);
        listener.onFileChunk(currentPath, piece);
    }

    private void endFile(boolean complete) {
        String path = currentPath;
        String content = clean(fileContent.toString());
        inFile = false;
        currentPath = null;
        fileContent.setLength(0);
        listener.onFileEnd(path, content, complete);
    }

    /** Removes the newline after the opening tag and markdown fences some models add anyway. */
    static String clean(String raw) {
        String s = raw;
        if (s.startsWith("\r\n")) s = s.substring(2);
        else if (s.startsWith("\n")) s = s.substring(1);

        String t = s.stripTrailing();
        int firstNl = t.indexOf('\n');
        if (firstNl > 0 && t.substring(0, firstNl).trim().matches("```[A-Za-z0-9_+-]*") && t.endsWith("```")) {
            t = t.substring(firstNl + 1, t.length() - 3);
        }
        return t.stripTrailing() + "\n";
    }
}
