package com.vishal.baas_platform.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vishal.baas_platform.exception.CustomException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class McpServerTest {

    private final McpTool echo = new McpTool("echo", "Echo", "Returns its arguments",
            Map.of("type", "object"), args -> Map.of("got", args.get("text")));
    private final McpTool broken = new McpTool("broken", "Broken", "Always fails",
            Map.of("type", "object"), args -> { throw new CustomException("project_id is required"); });
    private final McpServer server = new McpServer(List.of(echo, broken), new ObjectMapper());

    private static Map<String, Object> request(Object id, String method, Map<String, Object> params) {
        return Map.of("jsonrpc", "2.0", "id", id, "method", method, "params", params);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> result(Map<String, Object> reply) {
        return (Map<String, Object>) reply.get("result");
    }

    @Test
    void initializeAgreesOnASupportedProtocolVersion() {
        var reply = server.handle(request(1, "initialize", Map.of("protocolVersion", "2025-06-18")));

        assertThat(result(reply)).containsEntry("protocolVersion", "2025-06-18");
        assertThat(result(reply)).containsKey("serverInfo");
    }

    @Test
    void initializeFallsBackToTheLatestVersionForUnknownOnes() {
        var reply = server.handle(request(1, "initialize", Map.of("protocolVersion", "1999-01-01")));

        assertThat(result(reply)).containsEntry("protocolVersion", McpServer.PROTOCOL_VERSIONS.get(0));
    }

    @Test
    void notificationsGetNoReply() {
        assertThat(server.handle(Map.of("jsonrpc", "2.0", "method", "notifications/initialized"))).isNull();
    }

    @Test
    void toolsListDescribesEachTool() {
        var tools = (List<Map<String, Object>>) result(server.handle(request(2, "tools/list", Map.of()))).get("tools");

        assertThat(tools).extracting(t -> t.get("name")).containsExactly("echo", "broken");
        assertThat(tools.get(0)).containsKeys("description", "inputSchema");
    }

    @Test
    void toolsCallReturnsTheResultAsJsonText() {
        var reply = server.handle(request(3, "tools/call", Map.of("name", "echo", "arguments", Map.of("text", "hi"))));

        var content = (List<Map<String, Object>>) result(reply).get("content");
        assertThat(result(reply)).containsEntry("isError", false);
        assertThat((String) content.get(0).get("text")).contains("\"got\"").contains("\"hi\"");
    }

    @Test
    void toolErrorsComeBackAsIsErrorWithTheMessage() {
        var reply = server.handle(request(4, "tools/call", Map.of("name", "broken", "arguments", Map.of())));

        var content = (List<Map<String, Object>>) result(reply).get("content");
        assertThat(result(reply)).containsEntry("isError", true);
        assertThat(content.get(0)).containsEntry("text", "project_id is required");
    }

    @Test
    void unknownToolIsAnInvalidParamsError() {
        var reply = server.handle(request(5, "tools/call", Map.of("name", "nope")));

        assertThat((Map<String, Object>) reply.get("error")).containsEntry("code", -32602);
    }

    @Test
    void unknownMethodIsMethodNotFound() {
        var reply = server.handle(request(6, "resources/list", Map.of()));

        assertThat((Map<String, Object>) reply.get("error")).containsEntry("code", -32601);
    }
}
