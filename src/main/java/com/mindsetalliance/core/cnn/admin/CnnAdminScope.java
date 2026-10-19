package com.mindsetalliance.core.cnn.admin;

import java.util.Locale;
import java.util.Map;

/**
 * Correspondance entre les routes d'administration Colis na Nga et les permissions MA.
 * Une route inconnue n'est jamais relayée (refus par défaut).
 */
public final class CnnAdminScope {

    public static final String PROJECT = "CNN";

    private static final Map<String, String> BY_SEGMENT = Map.ofEntries(
            Map.entry("dashboard", "VIEW_CNN_DASHBOARD"),
            Map.entry("stats", "VIEW_CNN_DASHBOARD"),
            Map.entry("orders", "MANAGE_CNN_ORDERS"),
            Map.entry("deliveries", "MANAGE_CNN_ORDERS"),
            Map.entry("agents", "MANAGE_CNN_AGENTS"),
            Map.entry("motards", "MANAGE_CNN_MOTARDS"),
            Map.entry("motard-reports", "MANAGE_CNN_MOTARDS"),
            Map.entry("associations", "MANAGE_CNN_ASSOCIATIONS"),
            Map.entry("association-commission", "MANAGE_CNN_ASSOCIATIONS"),
            Map.entry("association-payouts", "MANAGE_CNN_ASSOCIATIONS"),
            Map.entry("earnings", "MANAGE_CNN_ASSOCIATIONS"),
            Map.entry("payouts", "MANAGE_CNN_ASSOCIATIONS"),
            Map.entry("clients", "MANAGE_CNN_CLIENTS"),
            Map.entry("subscribed-clients", "MANAGE_CNN_CLIENTS"),
            Map.entry("users", "MANAGE_CNN_CLIENTS"),
            Map.entry("subscriptions", "MANAGE_CNN_SUBSCRIPTIONS"),
            Map.entry("subscription-invoices", "MANAGE_CNN_SUBSCRIPTIONS"),
            Map.entry("mobile-money", "MANAGE_CNN_PAYMENTS"),
            Map.entry("refunds", "MANAGE_CNN_PAYMENTS"),
            Map.entry("emergency-calls", "MANAGE_CNN_ORDERS"),
            Map.entry("emergency-contact", "MANAGE_CNN_ORDERS"),
            Map.entry("pricing", "MANAGE_CNN_SETTINGS"),
            Map.entry("exchange-rates", "MANAGE_CNN_SETTINGS"),
            Map.entry("admins", "MANAGE_CNN_ADMINS"),
            Map.entry("super-admins", "MANAGE_CNN_ADMINS"),
            Map.entry("audit-logs", "MANAGE_CNN_ADMINS"));

    private CnnAdminScope() {
    }

    /** Permission MA requise pour le premier segment, ou null si la route n'est pas autorisée au relais. */
    public static String permissionFor(String firstSegment) {
        return firstSegment == null ? null : BY_SEGMENT.get(firstSegment.toLowerCase(Locale.ROOT));
    }

    /**
     * Action MA déduite de la méthode HTTP. Un POST sur une collection crée ; un POST sur
     * une ressource existante (approve, cancel, activate…) la modifie.
     */
    public static String actionFor(String method, int segmentCount) {
        return switch (method.toUpperCase(Locale.ROOT)) {
            case "GET", "HEAD" -> "VIEW";
            case "POST" -> segmentCount <= 1 ? "CREATE" : "UPDATE";
            case "PUT", "PATCH" -> "UPDATE";
            case "DELETE" -> "DELETE";
            default -> null;
        };
    }
}
