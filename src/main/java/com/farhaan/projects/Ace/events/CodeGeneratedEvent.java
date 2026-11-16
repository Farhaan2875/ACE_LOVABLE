package com.farhaan.projects.Ace.events;

import java.util.List;

/** "code.generated": the AI finished writing files for a project. */
public record CodeGeneratedEvent(Long projectId, Long userId, List<String> paths) {
}
