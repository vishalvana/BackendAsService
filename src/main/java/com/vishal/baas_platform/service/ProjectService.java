package com.vishal.baas_platform.service;

import com.vishal.baas_platform.dto.project.ProjectDetailsResponse;
import com.vishal.baas_platform.dto.project.ProjectRequest;
import com.vishal.baas_platform.dto.project.ProjectResponse;
import com.vishal.baas_platform.dto.project.ProjectStatsResponse;
import com.vishal.baas_platform.entity.Project;
import com.vishal.baas_platform.entity.User;
import com.vishal.baas_platform.exception.CustomException;
import com.vishal.baas_platform.repository.AppUserRepository;
import com.vishal.baas_platform.repository.DataRecordRepository;
import com.vishal.baas_platform.repository.ProjectRepository;
import com.vishal.baas_platform.repository.UserRepository;
import com.vishal.baas_platform.util.ApiKeyGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProjectService {
    private final DataRecordRepository dataRecordRepository;
    private final AppUserRepository appUserRepository;

    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;

    public ProjectResponse createProject(ProjectRequest request) {

        String email = SecurityContextHolder
                .getContext()
                .getAuthentication()
                .getName();

        User user = userRepository.findByEmail(email)
                .orElseThrow(() ->
                        new CustomException("User not found"));

        Project project = Project.builder()
                .name(request.getName())
                .apiKey(ApiKeyGenerator.generateApiKey())
                .owner(user)
                .createdAt(LocalDateTime.now())
                .build();

        Project savedProject = projectRepository.save(project);

        return ProjectResponse.builder()
                .id(savedProject.getId())
                .name(savedProject.getName())
                .apiKey(savedProject.getApiKey())
                .createdAt(savedProject.getCreatedAt())
                .build();
    }
    public ProjectDetailsResponse getProject(
            UUID projectId
    ) {

        String email = SecurityContextHolder
                .getContext()
                .getAuthentication()
                .getName();

        User user = userRepository
                .findByEmail(email)
                .orElseThrow(() ->
                        new CustomException("User not found"));

        Project project = projectRepository
                .findById(projectId)
                .orElseThrow(() ->
                        new CustomException("Project not found"));

        if (!project.getOwner().getId()
                .equals(user.getId())) {

            throw new CustomException(
                    "Access denied"
            );
        }

        return ProjectDetailsResponse.builder()
                .id(project.getId())
                .name(project.getName())
                .apiKey(project.getApiKey())
                .createdAt(project.getCreatedAt())
                .build();
    }

    // Used by the MCP create_project tool so an agent can re-run setup without making duplicates
    public Optional<ProjectResponse> findMyProjectByName(String name) {

        String email = SecurityContextHolder
                .getContext()
                .getAuthentication()
                .getName();

        User user = userRepository.findByEmail(email)
                .orElseThrow(() ->
                        new CustomException("User not found"));

        return projectRepository.findFirstByOwnerAndNameIgnoreCase(user, name.trim())
                .map(project -> ProjectResponse.builder()
                        .id(project.getId())
                        .name(project.getName())
                        .apiKey(project.getApiKey())
                        .createdAt(project.getCreatedAt())
                        .build());
    }

    public List<ProjectResponse> getMyProjects(){

        String email = SecurityContextHolder
                .getContext()
                .getAuthentication()
                .getName();

        User user = userRepository.findByEmail(email)
                .orElseThrow(() ->
                        new CustomException("User not found"));

        return projectRepository.findByOwner(user)
                .stream()
                .map(project -> ProjectResponse.builder()
                        .id(project.getId())
                        .name(project.getName())
                        .apiKey(project.getApiKey())
                        .createdAt(project.getCreatedAt())
                        .build())
                .toList();
    }
    public ProjectStatsResponse getProjectStats(
            UUID projectId
    ) {

        long totalDocuments =
                dataRecordRepository
                        .countByProjectId(projectId);

        long totalCollections =
                dataRecordRepository
                        .countDistinctCollections(projectId);

        long totalAppUsers =
                appUserRepository
                        .countByProjectId(projectId);

        return ProjectStatsResponse.builder()
                .totalCollections(totalCollections)
                .totalDocuments(totalDocuments)
                .totalAppUsers(totalAppUsers)
                .build();
    }
}