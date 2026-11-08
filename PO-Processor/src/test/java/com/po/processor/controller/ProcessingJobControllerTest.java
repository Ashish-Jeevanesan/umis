package com.po.processor.controller;

import com.po.processor.dto.PoDashboardSummaryDto;
import com.po.processor.entity.ProcessingJob;
import com.po.processor.service.PoProcessingService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageImpl;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProcessingJobController.class)
@DisplayName("Processing Job REST Controller Tests")
class ProcessingJobControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PoProcessingService processingService;

    @Test
    @DisplayName("GET /api/v1/health should return UP status")
    void testHealthEndpoint() throws Exception {
        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.service").value("PO-Processor"));
    }

    @Test
    @DisplayName("GET /api/v1/dashboard/summary should return summary metrics")
    void testDashboardSummary() throws Exception {
        PoDashboardSummaryDto summary = new PoDashboardSummaryDto(10L, 1L, 2L, 5L, 1L, 1L);
        when(processingService.getDashboardSummary()).thenReturn(summary);

        mockMvc.perform(get("/api/v1/dashboard/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalReceived").value(10))
                .andExpect(jsonPath("$.readyForExtraction").value(5))
                .andExpect(jsonPath("$.failed").value(1));
    }

    @Test
    @DisplayName("GET /api/v1/processing-jobs should return paginated jobs")
    void testListJobs() throws Exception {
        ProcessingJob job = new ProcessingJob(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), Instant.now()
        );
        job.setEmailSubject("PO-Test-1");
        job.setEmailSender("buyer@test.com");

        when(processingService.searchJobs(any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(job)));

        mockMvc.perform(get("/api/v1/processing-jobs?page=0&size=20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(job.getId().toString()))
                .andExpect(jsonPath("$.content[0].emailSubject").value("PO-Test-1"));
    }

    @Test
    @DisplayName("GET /api/v1/processing-jobs/{id} should return job when found")
    void testGetJobById() throws Exception {
        UUID jobId = UUID.randomUUID();
        ProcessingJob job = new ProcessingJob(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), Instant.now()
        );
        job.setId(jobId);
        job.setEmailSubject("Order 123");

        when(processingService.getJobById(jobId)).thenReturn(Optional.of(job));

        mockMvc.perform(get("/api/v1/processing-jobs/{id}", jobId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(jobId.toString()))
                .andExpect(jsonPath("$.emailSubject").value("Order 123"));
    }

    @Test
    @DisplayName("GET /api/v1/processing-jobs/{id} should return 404 when not found")
    void testGetJobByIdNotFound() throws Exception {
        UUID missingId = UUID.randomUUID();
        when(processingService.getJobById(missingId)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/processing-jobs/{id}", missingId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NOT_FOUND"));
    }
}
