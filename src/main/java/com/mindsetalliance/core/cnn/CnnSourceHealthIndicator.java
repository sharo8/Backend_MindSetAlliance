package com.mindsetalliance.core.cnn;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

/**
 * L'application reste disponible si le proxy est arrêté.
 * Le détail readOnlyVerified ne contient ni URL ni identifiant.
 */
@Component("cnnSource")
@ConditionalOnProperty(name = "ma.cnn.mode", havingValue = "db")
@Conditional(CnnDbUrlCondition.class)
public class CnnSourceHealthIndicator implements HealthIndicator {

    private final CnnSourceStatus status;

    public CnnSourceHealthIndicator(CnnSourceStatus status) {
        this.status = status;
    }

    @Override
    public Health health() {
        return Health.up()
                .withDetail("readOnlyVerified", status.readOnlyVerified())
                .withDetail("source", status.reachable() ? "CNN_DB" : "INDISPONIBLE")
                .build();
    }
}
