package com.mindsetalliance.core.vitrine;

import com.mindsetalliance.core.iam.Project;
import com.mindsetalliance.core.iam.ProjectRepository;
import com.mindsetalliance.core.tickets.TicketService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectEventListenerContractTest {

    @Mock VitrineKpiRepository kpiRepository;
    @Mock ProjectRepository projectRepository;
    @Mock TicketService ticketService;

    ProjectEventListener listener;
    Project cnn;

    @BeforeEach
    void setUp() {
        listener = new ProjectEventListener(new ProjectEventProcessor(kpiRepository, projectRepository, ticketService));
        cnn = new Project();
        cnn.setCode("CNN");
    }

    @Test
    void evenementFacticeCourseLivreeAlimenteLeKpi() {
        when(projectRepository.findByCode("CNN")).thenReturn(Optional.of(cnn));
        when(kpiRepository.findByProjectIdAndCategorieAndCle(any(), anyString(), anyString())).thenReturn(Optional.empty());
        when(kpiRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        listener.onCourseEvent(FakeExternalEventProducer.courseLivree());

        ArgumentCaptor<VitrineKpi> captor = ArgumentCaptor.forClass(VitrineKpi.class);
        verify(kpiRepository).save(captor.capture());
        assertEquals("courses_livrees", captor.getValue().getCle());
        assertEquals(0, BigDecimal.ONE.compareTo(captor.getValue().getValeur()));
        verify(ticketService, never()).createFromIncident(any(), any());
    }
}
