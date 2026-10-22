package com.vishal.baas_platform.service;

import com.vishal.baas_platform.entity.Project;
import com.vishal.baas_platform.entity.User;
import com.vishal.baas_platform.exception.CustomException;
import com.vishal.baas_platform.repository.AppUserRepository;
import com.vishal.baas_platform.repository.DataRecordRepository;
import com.vishal.baas_platform.repository.ProjectRepository;
import com.vishal.baas_platform.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class ProjectServiceTest {

    private final User me = User.builder().id(UUID.randomUUID()).email("me@example.com").build();
    private final User someoneElse = User.builder().id(UUID.randomUUID()).email("other@example.com").build();

    private ProjectRepository projectRepository;
    private DataRecordRepository dataRecordRepository;
    private ProjectService projectService;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("me@example.com", null));
        UserRepository userRepository = mock(UserRepository.class);
        when(userRepository.findByEmail("me@example.com")).thenReturn(Optional.of(me));
        projectRepository = mock(ProjectRepository.class);
        dataRecordRepository = mock(DataRecordRepository.class);
        projectService = new ProjectService(dataRecordRepository, mock(AppUserRepository.class),
                projectRepository, userRepository);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private UUID projectOwnedBy(User owner) {
        UUID id = UUID.randomUUID();
        when(projectRepository.findById(id))
                .thenReturn(Optional.of(Project.builder().id(id).name("p").apiKey("k").owner(owner).build()));
        return id;
    }

    @Test
    void statsForMyOwnProjectAreReturned() {
        UUID id = projectOwnedBy(me);
        when(dataRecordRepository.countByProjectId(id)).thenReturn(3L);

        assertThat(projectService.getProjectStats(id).getTotalDocuments()).isEqualTo(3L);
    }

    @Test
    void statsForSomeoneElsesProjectAreRefused() {
        UUID id = projectOwnedBy(someoneElse);

        assertThatThrownBy(() -> projectService.getProjectStats(id)).isInstanceOf(CustomException.class);
        verifyNoInteractions(dataRecordRepository);
    }
}
