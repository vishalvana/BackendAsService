package com.vishal.baas_platform.security;

import com.vishal.baas_platform.entity.Project;
import com.vishal.baas_platform.repository.ProjectRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ApiKeyFilterTest {

    private final UUID projectId = UUID.randomUUID();
    private ApiKeyFilter filter;

    @BeforeEach
    void setUp() {
        ProjectRepository projectRepository = mock(ProjectRepository.class);
        Project project = Project.builder().id(projectId).name("todo").apiKey("key-a").build();
        when(projectRepository.findByApiKey("key-a")).thenReturn(Optional.of(project));
        when(projectRepository.findByApiKey("unknown")).thenReturn(Optional.empty());
        filter = new ApiKeyFilter(projectRepository);
    }

    private MockHttpServletResponse run(String path, String apiKey, MockFilterChain chain) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        if (apiKey != null) request.addHeader("x-api-key", apiKey);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, chain);
        return response;
    }

    @Test
    void nonApiPathsSkipTheKeyCheck() throws Exception {
        MockFilterChain chain = new MockFilterChain();
        MockHttpServletResponse response = run("/projects", null, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void missingKeyIsRejected() throws Exception {
        MockFilterChain chain = new MockFilterChain();
        MockHttpServletResponse response = run("/api/" + projectId + "/todos", null, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    void unknownKeyIsRejected() throws Exception {
        MockFilterChain chain = new MockFilterChain();
        MockHttpServletResponse response = run("/api/" + projectId + "/todos", "unknown", chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    void keyCannotAccessAnotherProject() throws Exception {
        MockFilterChain chain = new MockFilterChain();
        MockHttpServletResponse response = run("/api/" + UUID.randomUUID() + "/todos", "key-a", chain);

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    void corsPreflightPassesWithoutKey() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", "/api/" + projectId + "/todos");
        request.addHeader("Origin", "http://localhost:5173");
        request.addHeader("Access-Control-Request-Method", "GET");
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void plainOptionsStillNeedsKey() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", "/api/" + projectId + "/todos");
        MockFilterChain chain = new MockFilterChain();
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    void keyCanAccessItsOwnProject() throws Exception {
        MockFilterChain chain = new MockFilterChain();
        MockHttpServletResponse response = run("/api/" + projectId + "/todos", "key-a", chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(chain.getRequest()).isNotNull();
    }
}
