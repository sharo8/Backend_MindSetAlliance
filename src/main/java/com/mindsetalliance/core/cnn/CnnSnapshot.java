package com.mindsetalliance.core.cnn;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Agrégats Colis na Nga. Un champ {@code null} signifie « pas de table correspondante »,
 * distinct d'un comptage réel à zéro.
 */
public record CnnSnapshot(
        boolean available,
        String source,
        String message,
        Long coursesCreeesJour,
        Long coursesLivreesJour,
        Long coursesLivreesPeriode,
        Long coursesLivreesTotal,
        Long coursesTotal,
        Long coursesAnnulees,
        List<Map<String, Object>> parStatut,
        List<Map<String, Object>> volume30j,
        String volumeMessage,
        Long coursiersActifs,
        Long coursiersExpires,
        String coursiersExpiresMessage,
        Long abonnementsRegles,
        BigDecimal abonnementsMontant,
        Long abonnementsExpires,
        Long incidentsOuverts,
        Long urgences,
        String derniereCourse,
        List<Map<String, Object>> incidents,
        Long motardsAttente,
        Long agentsActifs,
        String derniereLivraison,
        List<Map<String, Object>> activite7j,
        String activiteMessage,
        List<Map<String, Object>> delaiBins,
        boolean delaiDisponible,
        String delaiMessage,
        BigDecimal recettesMois,
        List<Map<String, Object>> encaissements6m,
        List<Map<String, Object>> parCanal,
        String dernierReglement,
        java.time.Instant calculeLe,
        List<Map<String, Object>> recettesParDevise,
        List<Map<String, Object>> serie6m,
        List<Map<String, Object>> remboursements,
        Map<String, Object> volumeAffaires,
        List<Map<String, Object>> coursesParMois
) {
    public static CnnSnapshot unavailable(String message) {
        return new CnnSnapshot(
                false,
                "INDISPONIBLE",
                message,
                null, null, null, null, null, null,
                List.of(),
                List.of(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                List.of(),
                null,
                null,
                null,
                List.of(),
                null,
                List.of(),
                false,
                null,
                null,
                List.of(),
                List.of(),
                null,
                java.time.Instant.now(),
                null,
                List.of(),
                null,
                null,
                List.of()
        );
    }
}
