package com.vishal.baas_platform.mcp;

import java.util.Map;
import java.util.function.Function;

/**
 * One MCP tool: its public description plus the code that runs it.
 * {@code run} returns any JSON-serializable value; throwing marks the call as an error.
 */
public record McpTool(
        String name,
        String title,
        String description,
        Map<String, Object> inputSchema,
        Function<Map<String, Object>, Object> run
) {
}
