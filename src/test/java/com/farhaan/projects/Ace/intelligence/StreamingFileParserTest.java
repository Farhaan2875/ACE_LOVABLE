package com.farhaan.projects.Ace.intelligence;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StreamingFileParserTest {

    /** Records everything the parser reports, as readable strings. */
    private static class Recorder implements StreamingFileParser.Listener {
        final List<String> events = new ArrayList<>();
        final StringBuilder text = new StringBuilder();
        public void onText(String t) { text.append(t); }
        public void onFileStart(String p) { events.add("start:" + p); }
        public void onFileChunk(String p, String c) { }
        public void onFileEnd(String p, String c, boolean ok) { events.add("end:" + p + ":" + ok + ":" + c); }
    }

    private static Recorder parse(String full, int pieceSize) {
        Recorder r = new Recorder();
        StreamingFileParser parser = new StreamingFileParser(r);
        for (int i = 0; i < full.length(); i += pieceSize) parser.feed(full.substring(i, Math.min(full.length(), i + pieceSize)));
        parser.finish();
        return r;
    }

    private static final String ANSWER = "Hello!\n<ace-file path=\"src/App.jsx\">\nconst a = 1 < 2;\n</ace-file>\nDone.";

    @Test
    void worksNoMatterHowTheStreamIsCut() {
        for (int size : new int[]{1, 2, 3, 5, 7, 100}) {      // size 1 = worst case: every tag split everywhere
            Recorder r = parse(ANSWER, size);
            assertEquals(List.of("start:src/App.jsx", "end:src/App.jsx:true:const a = 1 < 2;\n"), r.events, "piece size " + size);
            assertEquals("Hello!\n\nDone.", r.text.toString(), "piece size " + size);
        }
    }

    @Test
    void stripsMarkdownFences() {
        Recorder r = parse("<ace-file path=\"a.js\">\n```js\nlet x;\n```\n</ace-file>", 4);
        assertEquals("end:a.js:true:let x;\n", r.events.get(1));
    }

    @Test
    void unfinishedFileIsReportedAsIncomplete() {
        Recorder r = parse("<ace-file path=\"a.js\">\nlet x", 3);
        assertTrue(r.events.get(1).startsWith("end:a.js:false"));
    }

    @Test
    void lessThanSignInNormalTextIsKept() {
        assertEquals("if a < b and <div> ok", parse("if a < b and <div> ok", 1).text.toString());
    }
}
