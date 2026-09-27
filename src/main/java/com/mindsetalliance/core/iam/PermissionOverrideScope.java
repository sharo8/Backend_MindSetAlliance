package com.mindsetalliance.core.iam;

/**
 * Une dérogation globale (sans projet) s’applique partout.
 * Une dérogation liée à un projet ne s’applique que pour ce code société,
 * jamais au périmètre ENTREPRISE / null.
 */
public final class PermissionOverrideScope {

    private PermissionOverrideScope() {
    }

    public static boolean applies(String overrideProjectCode, String requestProjectCode) {
        if (overrideProjectCode == null || overrideProjectCode.isBlank()) {
            return true;
        }
        if (requestProjectCode == null || requestProjectCode.isBlank() || "ENTREPRISE".equalsIgnoreCase(requestProjectCode)) {
            return false;
        }
        return requestProjectCode.equalsIgnoreCase(overrideProjectCode);
    }
}
