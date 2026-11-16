package com.farhaan.projects.Ace.intelligence;

import com.farhaan.projects.Ace.util.Json;
import com.fasterxml.jackson.databind.JsonNode;

/** The tools the LLM may call (see "Tools: list_files, get_file_content" in the architecture). */
final class ToolDefinitions {
    private static final JsonNode TOOLS = Json.read("""
            [
              {"type":"function","function":{
                "name":"list_files",
                "description":"List every file in the project with its size.",
                "parameters":{"type":"object","properties":{}}}},
              {"type":"function","function":{
                "name":"get_file_content",
                "description":"Read the full content of one project file.",
                "parameters":{"type":"object",
                  "properties":{"path":{"type":"string","description":"Relative path, e.g. src/App.jsx"}},
                  "required":["path"]}}}
            ]""");

    private ToolDefinitions() {}

    static JsonNode json() {
        return TOOLS;
    }
}
