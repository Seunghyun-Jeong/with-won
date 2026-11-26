package com.withwon.domain.evidence;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EvidenceQueryServiceTest {

    private EvidenceFileRepository evidenceFileRepository;
    private EvidenceVersionRepository evidenceVersionRepository;
    private EvidenceQueryService evidenceQueryService;

    @BeforeEach
    void setUp() {
        evidenceFileRepository = mock(EvidenceFileRepository.class);
        evidenceVersionRepository = mock(EvidenceVersionRepository.class);
        evidenceQueryService = new EvidenceQueryService(evidenceFileRepository, evidenceVersionRepository);
    }

    @Test
    void findsEvidenceFilesOwnedByCustomer() {
        List<EvidenceFile> files = List.of(mock(EvidenceFile.class));
        when(evidenceFileRepository.findAllByIncident_IdAndIncident_Customer_IdOrderByCreatedAtDesc(3L, 7L))
                .thenReturn(files);

        assertEquals(files, evidenceQueryService.findIncidentEvidenceFiles(7L, 3L));
        verify(evidenceFileRepository)
                .findAllByIncident_IdAndIncident_Customer_IdOrderByCreatedAtDesc(3L, 7L);
    }

    @Test
    void findsVersionsOnlyThroughCustomerOwnedEvidenceFile() {
        List<EvidenceVersion> versions = List.of(mock(EvidenceVersion.class));
        when(evidenceVersionRepository
                .findAllByEvidenceFile_IdAndEvidenceFile_Incident_Customer_IdOrderByVersionNoDesc(11L, 7L))
                .thenReturn(versions);

        assertEquals(versions, evidenceQueryService.findEvidenceVersions(7L, 11L));
        verify(evidenceVersionRepository)
                .findAllByEvidenceFile_IdAndEvidenceFile_Incident_Customer_IdOrderByVersionNoDesc(11L, 7L);
    }

    @Test
    void rejectsMissingOwnershipId() {
        assertThrows(IllegalArgumentException.class,
                () -> evidenceQueryService.findCustomerEvidenceFiles(null));
        assertThrows(IllegalArgumentException.class,
                () -> evidenceQueryService.findIncidentEvidenceFiles(7L, null));
    }
}
