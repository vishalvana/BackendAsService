package com.vishal.baas_platform.repository;

import com.vishal.baas_platform.entity.Project;
import com.vishal.baas_platform.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProjectRepository
        extends JpaRepository<Project, UUID> {
    long countByOwner(User owner);
    List<Project> findByOwner(User owner);
    Optional<Project> findByApiKey(String apiKey);
    Optional<Project> findFirstByOwnerAndNameIgnoreCase(User owner, String name);
}