package com.farhaan.projects.Ace.Dto.preview;

import com.farhaan.projects.Ace.entities.Preview;

public final class PreviewMapper {
    private PreviewMapper() {}

    public static PreviewResponse none() {
        return new PreviewResponse("NONE", null, null, null, null);
    }

    public static PreviewResponse of(Preview p) {
        return new PreviewResponse(p.getStatus().name(), p.getPreviewUrl(), p.getStatusMessage(), p.getStartedAt(), p.getUpdatedAt());
    }
}
