package com.mindsetalliance.core.tickets;

import com.mindsetalliance.core.common.BusinessException;

import java.util.Map;
import java.util.Set;

public enum TicketStatut {
    OUVERT, PRIS_EN_CHARGE, EN_COURS, EN_ATTENTE, RESOLU, CLOTURE, ANNULE;

    private static final Map<TicketStatut, Set<TicketStatut>> TRANSITIONS = Map.of(
            OUVERT, Set.of(EN_COURS, EN_ATTENTE, ANNULE),
            EN_COURS, Set.of(EN_ATTENTE, RESOLU, ANNULE),
            EN_ATTENTE, Set.of(EN_COURS, ANNULE),
            RESOLU, Set.of(CLOTURE, EN_COURS),
            CLOTURE, Set.of(),
            ANNULE, Set.of(),
            PRIS_EN_CHARGE, Set.of(EN_COURS, EN_ATTENTE, RESOLU, ANNULE)
    );

    public static TicketStatut from(String value) {
        try {
            TicketStatut statut = TicketStatut.valueOf(value);
            return statut == PRIS_EN_CHARGE ? EN_COURS : statut;
        } catch (Exception e) {
            throw new BusinessException("Statut de ticket inconnu : " + value);
        }
    }

    public void assertTransition(TicketStatut cible) {
        if (!TRANSITIONS.getOrDefault(this, Set.of()).contains(cible)) {
            throw new BusinessException("Transition interdite : " + this + " → " + cible);
        }
    }
}
