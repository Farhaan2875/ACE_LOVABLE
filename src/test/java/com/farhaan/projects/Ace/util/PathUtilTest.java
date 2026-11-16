package com.farhaan.projects.Ace.util;

import com.farhaan.projects.Ace.error.BadRequestException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PathUtilTest {
    @Test
    void acceptsNormalPaths() {
        assertEquals("src/App.jsx", PathUtil.normalize("/src/App.jsx"));
        assertEquals("index.html", PathUtil.normalize("./index.html"));
        assertEquals("jsx", PathUtil.extension("src/App.jsx"));
    }

    @Test
    void rejectsDangerousPaths() {
        for (String bad : new String[]{"../secret", "a/../../b", "src/node_modules/x.js", "a b.js", "dir/", "", "C:\\\\win.js;rm"}) {
            assertThrows(BadRequestException.class, () -> PathUtil.normalize(bad), bad);
        }
    }
}
