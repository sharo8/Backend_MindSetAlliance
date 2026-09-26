package com.mindsetalliance.core.tickets;

import com.mindsetalliance.core.common.BusinessException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TicketStatutTest {

    @Test
    void transitionsAutorisees() {
        assertDoesNotThrow(() -> TicketStatut.OUVERT.assertTransition(TicketStatut.EN_COURS));
        assertDoesNotThrow(() -> TicketStatut.OUVERT.assertTransition(TicketStatut.ANNULE));
        assertDoesNotThrow(() -> TicketStatut.EN_COURS.assertTransition(TicketStatut.EN_ATTENTE));
        assertDoesNotThrow(() -> TicketStatut.EN_COURS.assertTransition(TicketStatut.ANNULE));
        assertDoesNotThrow(() -> TicketStatut.EN_ATTENTE.assertTransition(TicketStatut.EN_COURS));
        assertDoesNotThrow(() -> TicketStatut.RESOLU.assertTransition(TicketStatut.EN_COURS));
        assertDoesNotThrow(() -> TicketStatut.RESOLU.assertTransition(TicketStatut.CLOTURE));
    }

    @Test
    void transitionsInterdites() {
        assertThrows(BusinessException.class, () -> TicketStatut.OUVERT.assertTransition(TicketStatut.RESOLU));
        assertThrows(BusinessException.class, () -> TicketStatut.OUVERT.assertTransition(TicketStatut.CLOTURE));
        assertThrows(BusinessException.class, () -> TicketStatut.EN_ATTENTE.assertTransition(TicketStatut.RESOLU));
        assertThrows(BusinessException.class, () -> TicketStatut.EN_COURS.assertTransition(TicketStatut.CLOTURE));
        assertThrows(BusinessException.class, () -> TicketStatut.CLOTURE.assertTransition(TicketStatut.OUVERT));
        assertThrows(BusinessException.class, () -> TicketStatut.ANNULE.assertTransition(TicketStatut.EN_COURS));
    }
}
