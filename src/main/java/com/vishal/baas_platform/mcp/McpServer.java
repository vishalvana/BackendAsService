package com.vishal.baas_platform.mcp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vishal.baas_platform.exception.CustomException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A minimal, stateless MCP server (JSON-RPC 2.0 over Streamable HTTP, JSON responses only).
 * Handles initialize, ping, tools/list and tools/call; notifications get no reply.
 */
@Slf4j
@Component
public class McpServer {

    static final List<String> PROTOCOL_VERSIONS = List.of("2025-11-25", "2025-06-18", "2025-03-26", "2024-11-05");

    private static final String INSTRUCTIONS = """
            Backend-as-a-Service for apps. Typical setup: create_project (safe to retry) -> insert_document to \
            create collections and seed data -> create_app_user for test users -> get_api_key to wire the app. \
            Apps call the data API with the project's API key in the x-api-key header.""";

    private final List<McpTool> tools;
    private final ObjectMapper objectMapper;

    @Autowired
    public McpServer(BaasTools baasTools, ObjectMapper objectMapper) {
        this(baasTools.all(), objectMapper);
    }

    McpServer(List<McpTool> tools, ObjectMapper objectMapper) {
        this.tools = tools;
        this.objectMapper = objectMapper;
    }

    /** Returns the JSON-RPC reply, or null when the message is a notification. */
    public Map<String, Object> handle(Object raw) {
        if (!(raw instanceof Map<?, ?> msg)) {
            return error(null, -32600, "Invalid request");
        }
        Object id = msg.get("id");
        boolean notification = !msg.containsKey("id");
        if (!"2.0".equals(msg.get("jsonrpc")) || !(msg.get("method") instanceof String method)) {
            return notification ? null : error(id, -32600, "Invalid request");
        }
        if (notification) {
            return null; // notifications/initialized and friends need no reply
        }
        Map<?, ?> params = msg.get("params") instanceof Map<?, ?> p ? p : Map.of();

        return switch (method) {
            case "initialize" -> result(id, initialize(params));
            case "ping" -> result(id, Map.of());
            case "tools/list" -> result(id, Map.of("tools", tools.stream().map(McpServer::describe).toList()));
            case "tools/call" -> callTool(id, params);
            default -> error(id, -32601, "Method not found: " + method);
        };
    }

    private Map<String, Object> initialize(Map<?, ?> params) {
        Object asked = params.get("protocolVersion");
        String version = asked instanceof String s && PROTOCOL_VERSIONS.contains(s) ? s : PROTOCOL_VERSIONS.get(0);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("protocolVersion", version);
        out.put("capabilities", Map.of("tools", Map.of("listChanged", false)));
        out.put("serverInfo", Map.of("name", "baas", "title", "BackendAsService", "version", "0.1.0"));
        out.put("instructions", INSTRUCTIONS);
        return out;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> callTool(Object id, Map<?, ?> params) {
        Object name = params.get("name");
        McpTool tool = tools.stream().filter(t -> t.name().equals(name)).findFirst().orElse(null);
        if (tool == null) {
            return error(id, -32602, "Unknown tool: " + name);
        }
        Map<String, Object> args = params.get("arguments") instanceof Map<?, ?> a
                ? (Map<String, Object>) a
                : Map.of();
        try {
            return result(id, toolResult(toJson(tool.run().apply(args)), false));
        } catch (CustomException e) {
            return result(id, toolResult(e.getMessage(), true));
        } catch (Exception e) {
            log.error("MCP tool {} failed", tool.name(), e);
            return result(id, toolResult("The tool failed on the server. Try again, or check the server logs.", true));
        }
    }

    private String toJson(Object value) throws JsonProcessingException {
        return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(value);
    }

    private static Map<String, Object> describe(McpTool t) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("name", t.name());
        out.put("title", t.title());
        out.put("description", t.description());
        out.put("inputSchema", t.inputSchema());
        return out;
    }

    private static Map<String, Object> toolResult(String text, boolean isError) {
        return Map.of("content", List.of(Map.of("type", "text", "text", text)), "isError", isError);
    }

    private static Map<String, Object> result(Object id, Object result) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("jsonrpc", "2.0");
        out.put("id", id);
        out.put("result", result);
        return out;
    }

    static Map<String, Object> error(Object id, int code, String message) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("jsonrpc", "2.0");
        out.put("id", id);
        out.put("error", Map.of("code", code, "message", message));
        return out;
    }
}
