package com.vishal.baas_platform.repository;

import com.vishal.baas_platform.entity.DataRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface DataRecordRepository
        extends JpaRepository<DataRecord, UUID> {

    long countByProjectIdIn(List<UUID> projectIds);

    @Query("""
SELECT COUNT(DISTINCT d.collectionName)
FROM DataRecord d
WHERE d.projectId IN :projectIds
""")
    long countDistinctCollections(List<UUID> projectIds);
    long countByProjectId(UUID projectId);
    @Query("""
SELECT COUNT(DISTINCT d.collectionName)
FROM DataRecord d
WHERE d.projectId = :projectId
""")
    long countDistinctCollections(UUID projectId);

    @Query("""
SELECT DISTINCT d.collectionName
FROM DataRecord d
WHERE d.projectId = :projectId
ORDER BY d.collectionName
""")
    List<String> findCollectionNames(UUID projectId);

    List<DataRecord> findByProjectIdAndCollectionName(
            UUID projectId,
            String collectionName
    );
}