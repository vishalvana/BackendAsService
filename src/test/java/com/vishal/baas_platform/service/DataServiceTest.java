package com.vishal.baas_platform.service;

import com.vishal.baas_platform.entity.DataRecord;
import com.vishal.baas_platform.repository.DataRecordRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class DataServiceTest {

    private final DataRecordRepository repository = mock(DataRecordRepository.class);
    private final DataService dataService = new DataService(repository);

    @Test
    void createRecordStoresDataUnderProjectAndCollection() {
        UUID projectId = UUID.randomUUID();
        when(repository.save(any())).thenAnswer(call -> call.getArgument(0));

        DataRecord record = dataService.createRecord(projectId, "todos", Map.of("title", "ship week 1"));

        ArgumentCaptor<DataRecord> saved = ArgumentCaptor.forClass(DataRecord.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getProjectId()).isEqualTo(projectId);
        assertThat(saved.getValue().getCollectionName()).isEqualTo("todos");
        assertThat(saved.getValue().getData()).containsEntry("title", "ship week 1");
        assertThat(record.getCreatedAt()).isNotNull();
    }

    @Test
    void getRecordsOnlyQueriesTheRequestedProjectAndCollection() {
        UUID projectId = UUID.randomUUID();
        DataRecord record = DataRecord.builder().projectId(projectId).collectionName("todos").build();
        when(repository.findByProjectIdAndCollectionName(projectId, "todos")).thenReturn(List.of(record));

        assertThat(dataService.getRecords(projectId, "todos")).containsExactly(record);
        verify(repository).findByProjectIdAndCollectionName(projectId, "todos");
    }
}
