package com.mindsetalliance.core.iam;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PermissionOverrideScopeTest {

    @Test
    void globalOverrideAppliesEverywhere() {
        assertTrue(PermissionOverrideScope.applies(null, "CNN"));
        assertTrue(PermissionOverrideScope.applies(null, "MDR"));
        assertTrue(PermissionOverrideScope.applies(null, null));
        assertTrue(PermissionOverrideScope.applies(null, "ENTREPRISE"));
    }

    @Test
    void projectOverrideDoesNotLeakToOtherProjectOrCompanyWide() {
        assertTrue(PermissionOverrideScope.applies("CNN", "CNN"));
        assertFalse(PermissionOverrideScope.applies("CNN", "MDR"));
        assertFalse(PermissionOverrideScope.applies("CNN", null));
        assertFalse(PermissionOverrideScope.applies("CNN", "ENTREPRISE"));
    }
}
