package com.farhaan.projects.Ace.Dto.project;

import java.time.Instant;


public record FileNode(
        String path,
        Instant modifiedAt,
        Long size,
        String type      // file extension, e.g. "jsx", "css"
) {
}
