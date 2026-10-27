package com.vishal.baas_platform.security;

import com.vishal.baas_platform.entity.Project;
import com.vishal.baas_platform.repository.ProjectRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.cors.CorsUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class ApiKeyFilter extends OncePerRequestFilter {

    private final ProjectRepository projectRepository;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String path = request.getRequestURI();

        // Browsers send a CORS preflight without custom headers; let the CORS filter answer it
        if (!path.startsWith("/api/") || CorsUtils.isPreFlightRequest(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        String apiKey = request.getHeader("x-api-key");

        if (apiKey == null || apiKey.isBlank()) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("Missing API Key");
            return;
        }

        Project project = projectRepository
                .findByApiKey(apiKey)
                .orElse(null);

        if (project == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("Invalid API Key");
            return;
        }

        // A key only grants access to its own project: /api/{projectId}/...
        String[] segments = path.split("/");
        String requestedProjectId = segments.length > 2 ? segments[2] : "";

        if (!project.getId().toString().equalsIgnoreCase(requestedProjectId)) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.getWriter().write("API Key does not belong to this project");
            return;
        }

        filterChain.doFilter(request, response);
    }
}