package com.mindsetalliance.core.tickets;

import java.util.Set;

public final class TicketSysteme {
    public static final String SUPPORT = "SUPPORT";
    public static final String TECHNIQUE = "TECHNIQUE";

    public static final Set<String> SUPPORT_CATEGORIES = Set.of(
            "REFUS_COURSE", "RETARD", "LITIGE_PAIEMENT", "DESTINATAIRE_INJOIGNABLE");
    public static final Set<String> TECHNIQUE_CATEGORIES = Set.of(
            "BUG", "DEPLOIEMENT", "CORRECTIF", "AUTRE");

    private TicketSysteme() {
    }

    public static boolean isSupport(String systeme) {
        return SUPPORT.equals(systeme);
    }

    public static Set<String> categoriesOf(String systeme) {
        return TECHNIQUE.equals(systeme) ? TECHNIQUE_CATEGORIES : SUPPORT_CATEGORIES;
    }
}
