package com.mindsetalliance.core.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FlywayRepairConfig {

    @Bean
    public FlywayMigrationStrategy flywayMigrationStrategy(
            @Value("${ma.flyway.repair-on-migrate:false}") boolean repairOnMigrate) {
        return flyway -> {
            if (repairOnMigrate) {
                flyway.repair();
            }
            flyway.migrate();
        };
    }
}
