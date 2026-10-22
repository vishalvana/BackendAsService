package com.vishal.baas_platform.mcp;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * MCP endpoint. Connect with your developer JWT, e.g.
 * {@code claude mcp add --transport http baas http://localhost:8080/mcp --header "Authorization: Bearer <token>"}
 */
@RestController
@RequestMapping("/mcp")
@RequiredArgsConstructor
public class McpController {

    private final McpServer mcpServer;

    @PostMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Object> handle(@RequestBody Object body) {
        if (body instanceof List<?> batch) {
            List<Map<String, Object>> replies = batch.stream().map(mcpServer::handle).filter(Objects::nonNull).toList();
            return replies.isEmpty() ? ResponseEntity.accepted().build() : ResponseEntity.ok(replies);
        }
        Map<String, Object> reply = mcpServer.handle(body);
        return reply == null ? ResponseEntity.accepted().build() : ResponseEntity.ok(reply);
    }

    // MCP clients may open a GET event stream; this server only answers POSTs
    @GetMapping
    public ResponseEntity<Void> stream() {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).header(HttpHeaders.ALLOW, "POST").build();
    }
}
