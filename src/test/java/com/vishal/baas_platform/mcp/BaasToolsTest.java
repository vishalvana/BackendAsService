package com.vishal.baas_platform.mcp;

import com.vishal.baas_platform.dto.project.ProjectDetailsResponse;
import com.vishal.baas_platform.dto.project.ProjectRequest;
import com.vishal.baas_platform.dto.project.ProjectResponse;
import com.vishal.baas_platform.exception.CustomException;
import com.vishal.baas_platform.repository.DataRecordRepository;
import com.vishal.baas_platform.service.DataService;
import com.vishal.baas_platform.service.ProjectService;
import com.vishal.baas_platform.service.SdkAuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class BaasToolsTest {

    private ProjectService projectService;
    private DataService dataService;
    private BaasTools tools;

    @BeforeEach
    void setUp() {
        projectService = mock(ProjectService.class);
        dataService = mock(DataService.class);
        tools = new BaasTools(projectService, dataService, mock(SdkAuthService.class), mock(DataRecordRepository.class));
    }

    private Object call(String name, Map<String, Object> args) {
        return tools.all().stream().filter(t -> t.name().equals(name)).findFirst().orElseThrow().run().apply(args);
    }

    @Test
    void exposesTheSetupTools() {
        assertThat(tools.all()).extracting(McpTool::name).containsExactly(
                "list_projects", "create_project", "get_api_key", "list_collections",
                "insert_document", "query_documents", "create_app_user");
    }

    @Test
    void createProjectReturnsTheExistingProjectInsteadOfADuplicate() {
        ProjectResponse existing = ProjectResponse.builder().id(UUID.randomUUID()).name("todo-app").apiKey("k").build();
        when(projectService.findMyProjectByName("todo-app")).thenReturn(Optional.of(existing));

        var out = (Map<String, Object>) call("create_project", Map.of("name", "todo-app"));

        assertThat(out).containsEntry("created", false).containsEntry("apiKey", "k");
        verify(projectService, never()).createProject(any());
    }

    @Test
    void createProjectCreatesWhenTheNameIsNew() {
        ProjectResponse created = ProjectResponse.builder().id(UUID.randomUUID()).name("todo-app").apiKey("k").build();
        when(projectService.findMyProjectByName("todo-app")).thenReturn(Optional.empty());
        when(projectService.createProject(any(ProjectRequest.class))).thenReturn(created);

        var out = (Map<String, Object>) call("create_project", Map.of("name", "todo-app"));

        assertThat(out).containsEntry("created", true);
    }

    @Test
    void projectToolsRejectIdsThatAreNotUuids() {
        assertThatThrownBy(() -> call("get_api_key", Map.of("project_id", "todo-app")))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("project_id");
    }

    @Test
    void insertRejectsCollectionNamesThatBreakTheUrl() {
        UUID id = UUID.randomUUID();
        when(projectService.getProject(id)).thenReturn(ProjectDetailsResponse.builder().id(id).apiKey("k").build());

        assertThatThrownBy(() -> call("insert_document",
                Map.of("project_id", id.toString(), "collection", "../users", "data", Map.of("a", 1))))
                .isInstanceOf(CustomException.class);
        verifyNoInteractions(dataService);
    }

    @Test
    void timestampsCarryATimezoneOffset() {
        String out = BaasTools.timestamp(java.time.LocalDateTime.of(2026, 10, 23, 1, 22, 9));

        assertThat(out).startsWith("2026-10-23T01:22:09").matches(".*([+-]\\d{2}:\\d{2}|Z)$");
        assertThat(BaasTools.timestamp(null)).isNull();
    }

    @Test
    void whereFilterMatchesNumbersRegardlessOfType() {
        assertThat(BaasTools.matches(Map.of("count", 3L, "done", false), Map.of("count", 3, "done", false))).isTrue();
        assertThat(BaasTools.matches(Map.of("count", 3L), Map.of("count", 4))).isFalse();
        assertThat(BaasTools.matches(Map.of("title", "x"), Map.of())).isTrue();
    }
}
