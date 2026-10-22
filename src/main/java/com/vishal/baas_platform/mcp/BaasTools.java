package com.vishal.baas_platform.mcp;

import com.vishal.baas_platform.dto.project.ProjectDetailsResponse;
import com.vishal.baas_platform.dto.project.ProjectRequest;
import com.vishal.baas_platform.dto.project.ProjectResponse;
import com.vishal.baas_platform.dto.sdk.AppUserSignupRequest;
import com.vishal.baas_platform.entity.DataRecord;
import com.vishal.baas_platform.exception.CustomException;
import com.vishal.baas_platform.repository.DataRecordRepository;
import com.vishal.baas_platform.service.DataService;
import com.vishal.baas_platform.service.ProjectService;
import com.vishal.baas_platform.service.SdkAuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.regex.Pattern;

/**
 * The tools an AI agent can call to set up and use a backend.
 * Every call runs as the developer whose JWT came with the request,
 * and project-level tools only work on that developer's own projects.
 */
@Component
@RequiredArgsConstructor
public class BaasTools {

    private static final Pattern COLLECTION_NAME = Pattern.compile("^[A-Za-z0-9_-]{1,64}$");
    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 500;

    private final ProjectService projectService;
    private final DataService dataService;
    private final SdkAuthService sdkAuthService;
    private final DataRecordRepository dataRecordRepository;

    public List<McpTool> all() {
        return List.of(
                new McpTool(
                        "list_projects",
                        "List projects",
                        "List the developer's BaaS projects (id, name, created date). Use it to find a project id.",
                        schema(Map.of(), List.of()),
                        args -> projectService.getMyProjects().stream().map(BaasTools::projectSummary).toList()
                ),
                new McpTool(
                        "create_project",
                        "Create a project",
                        "Create a backend project and its API key. Safe to retry: if the developer already has a project"
                                + " with this name, it is returned instead of creating a duplicate (created=false).",
                        schema(Map.of("name", string("Project name, e.g. todo-app")), List.of("name")),
                        this::createProject
                ),
                new McpTool(
                        "get_api_key",
                        "Get a project's API key",
                        "Return the API key an app uses to read and write this project's data,"
                                + " plus how to call the data API with it.",
                        schema(Map.of("project_id", string("Project id from create_project or list_projects")),
                                List.of("project_id")),
                        this::getApiKey
                ),
                new McpTool(
                        "list_collections",
                        "List collections",
                        "List a project's collections and how many documents and app users it has.",
                        schema(Map.of("project_id", string("Project id")), List.of("project_id")),
                        this::listCollections
                ),
                new McpTool(
                        "insert_document",
                        "Insert a document",
                        "Store a JSON document in a collection. Collections are schemaless and are created on first"
                                + " insert, so there is no separate create-collection step.",
                        schema(Map.of(
                                "project_id", string("Project id"),
                                "collection", string("Collection name: letters, digits, _ or -, e.g. todos"),
                                "data", Map.of("type", "object", "description", "The document, any JSON object")
                        ), List.of("project_id", "collection", "data")),
                        this::insertDocument
                ),
                new McpTool(
                        "query_documents",
                        "Query documents",
                        "Read documents from a collection, newest first. Optionally keep only documents whose top-level"
                                + " fields equal the given values.",
                        schema(Map.of(
                                "project_id", string("Project id"),
                                "collection", string("Collection name"),
                                "where", Map.of("type", "object",
                                        "description", "Optional exact-match filter, e.g. {\"done\": false}"),
                                "limit", Map.of("type", "integer", "minimum", 1, "maximum", MAX_LIMIT,
                                        "description", "Max documents to return (default " + DEFAULT_LIMIT + ")")
                        ), List.of("project_id", "collection")),
                        this::queryDocuments
                ),
                new McpTool(
                        "create_app_user",
                        "Create an app user",
                        "Register an end user of the developer's app (not a developer account)."
                                + " The user can then log in through /sdk/auth/login with the project's API key.",
                        schema(Map.of(
                                "project_id", string("Project id"),
                                "name", string("User's name"),
                                "email", string("User's email"),
                                "password", string("Initial password (at least 6 characters)")
                        ), List.of("project_id", "name", "email", "password")),
                        this::createAppUser
                )
        );
    }

    private Object createProject(Map<String, Object> args) {
        String name = requireString(args, "name");
        Optional<ProjectResponse> existing = projectService.findMyProjectByName(name);
        if (existing.isPresent()) {
            return withKey(existing.get(), false);
        }
        ProjectRequest request = new ProjectRequest();
        request.setName(name.trim());
        return withKey(projectService.createProject(request), true);
    }

    private Object getApiKey(Map<String, Object> args) {
        ProjectDetailsResponse project = ownedProject(args);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("projectId", project.getId().toString());
        out.put("apiKey", project.getApiKey());
        String dataUrl = baseUrl() + "/api/" + project.getId() + "/{collection}";
        out.put("baseUrl", baseUrl());
        out.put("usage", "Send it as the x-api-key header. Write: POST " + dataUrl
                + " with a JSON body. Read: GET " + dataUrl + ".");
        return out;
    }

    private Object listCollections(Map<String, Object> args) {
        ProjectDetailsResponse project = ownedProject(args);
        var stats = projectService.getProjectStats(project.getId());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("projectId", project.getId().toString());
        out.put("collections", dataRecordRepository.findCollectionNames(project.getId()));
        out.put("totalDocuments", stats.getTotalDocuments());
        out.put("totalAppUsers", stats.getTotalAppUsers());
        return out;
    }

    private Object insertDocument(Map<String, Object> args) {
        ProjectDetailsResponse project = ownedProject(args);
        String collection = collectionName(args);
        Map<String, Object> data = requireObject(args, "data");
        return document(dataService.createRecord(project.getId(), collection, data));
    }

    private Object queryDocuments(Map<String, Object> args) {
        ProjectDetailsResponse project = ownedProject(args);
        String collection = collectionName(args);
        Map<String, Object> where = args.get("where") == null ? Map.of() : requireObject(args, "where");
        int limit = limit(args.get("limit"));

        List<Map<String, Object>> matches = dataService.getRecords(project.getId(), collection).stream()
                .filter(record -> matches(record.getData(), where))
                .sorted(Comparator.comparing(DataRecord::getCreatedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(limit)
                .map(BaasTools::document)
                .toList();

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("collection", collection);
        out.put("count", matches.size());
        out.put("documents", matches);
        return out;
    }

    private Object createAppUser(Map<String, Object> args) {
        ProjectDetailsResponse project = ownedProject(args);
        String password = requireString(args, "password");
        if (password.length() < 6) {
            throw new CustomException("password must be at least 6 characters");
        }
        AppUserSignupRequest request = new AppUserSignupRequest();
        request.setName(requireString(args, "name"));
        request.setEmail(requireString(args, "email"));
        request.setPassword(password);
        sdkAuthService.signup(project.getApiKey(), request);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("created", true);
        out.put("email", request.getEmail());
        out.put("projectId", project.getId().toString());
        return out;
    }

    // --- helpers ---

    private ProjectDetailsResponse ownedProject(Map<String, Object> args) {
        String raw = requireString(args, "project_id");
        UUID id;
        try {
            id = UUID.fromString(raw.trim());
        } catch (IllegalArgumentException e) {
            throw new CustomException("project_id must be a project id (UUID) from list_projects");
        }
        // Throws "Project not found" / "Access denied" when the project isn't the caller's
        return projectService.getProject(id);
    }

    private static String collectionName(Map<String, Object> args) {
        String name = requireString(args, "collection").trim();
        if (!COLLECTION_NAME.matcher(name).matches()) {
            throw new CustomException("collection must be 1-64 letters, digits, _ or -");
        }
        return name;
    }

    private static int limit(Object raw) {
        if (raw == null) return DEFAULT_LIMIT;
        if (!(raw instanceof Number n) || n.intValue() < 1) {
            throw new CustomException("limit must be a positive integer");
        }
        return Math.min(n.intValue(), MAX_LIMIT);
    }

    static boolean matches(Map<String, Object> data, Map<String, Object> where) {
        if (where.isEmpty()) return true;
        if (data == null) return false;
        return where.entrySet().stream().allMatch(e -> sameValue(data.get(e.getKey()), e.getValue()));
    }

    // JSON numbers may arrive as Integer, Long or Double depending on the parser, so compare numerically
    private static boolean sameValue(Object actual, Object expected) {
        if (actual instanceof Number a && expected instanceof Number b) {
            return a.doubleValue() == b.doubleValue();
        }
        return Objects.equals(actual, expected);
    }

    private static String requireString(Map<String, Object> args, String key) {
        Object value = args.get(key);
        if (!(value instanceof String s) || s.isBlank()) {
            throw new CustomException(key + " is required");
        }
        return s;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> requireObject(Map<String, Object> args, String key) {
        if (!(args.get(key) instanceof Map<?, ?> map)) {
            throw new CustomException(key + " must be a JSON object");
        }
        return (Map<String, Object>) map;
    }

    private static Map<String, Object> projectSummary(ProjectResponse p) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", p.getId().toString());
        out.put("name", p.getName());
        out.put("createdAt", timestamp(p.getCreatedAt()));
        return out;
    }

    private static Map<String, Object> withKey(ProjectResponse p, boolean created) {
        Map<String, Object> out = projectSummary(p);
        out.put("apiKey", p.getApiKey());
        out.put("created", created);
        return out;
    }

    private static Map<String, Object> document(DataRecord record) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", String.valueOf(record.getId()));
        out.put("collection", record.getCollectionName());
        out.put("data", record.getData());
        out.put("createdAt", timestamp(record.getCreatedAt()));
        return out;
    }

    // Timestamps are stored as server-local LocalDateTime; give agents an unambiguous ISO-8601 value with offset
    static String timestamp(LocalDateTime time) {
        return time == null ? null : time.atZone(ZoneId.systemDefault()).toOffsetDateTime().toString();
    }

    // The URL the agent reached us on, so it can tell an app which host to call
    private static String baseUrl() {
        try {
            return ServletUriComponentsBuilder.fromCurrentContextPath().toUriString();
        } catch (IllegalStateException e) {
            return ""; // not inside an HTTP request (e.g. unit tests)
        }
    }

    private static Map<String, Object> string(String description) {
        return Map.of("type", "string", "description", description);
    }

    private static Map<String, Object> schema(Map<String, Object> properties, List<String> required) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("type", "object");
        out.put("properties", properties);
        if (!required.isEmpty()) out.put("required", required);
        return out;
    }
}
