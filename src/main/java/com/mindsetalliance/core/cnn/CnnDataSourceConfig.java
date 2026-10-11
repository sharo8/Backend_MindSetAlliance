package com.mindsetalliance.core.cnn;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.actuate.health.HealthContributor;
import org.springframework.boot.actuate.jdbc.DataSourceHealthIndicator;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

import javax.sql.DataSource;

/**
 * Active uniquement si {@code ma.cnn.mode=db}.
 * Le bean {@code dataSource} reste la source du Core (@Primary) pour JPA et Flyway.
 * La source {@code cnn} ne porte aucune entité : SELECT via JdbcTemplate seulement.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "ma.cnn.mode", havingValue = "db")
public class CnnDataSourceConfig {

    private static final Logger log = LoggerFactory.getLogger(CnnDataSourceConfig.class);

    @Bean(name = "dataSource")
    @Primary
    @ConfigurationProperties(prefix = "spring.datasource.hikari")
    public HikariDataSource dataSource(DataSourceProperties properties) {
        HikariDataSource core = properties.initializeDataSourceBuilder()
                .type(HikariDataSource.class)
                .build();
        if (StringUtils.hasText(properties.getName())) {
            core.setPoolName(properties.getName());
        }
        return core;
    }

    @Bean
    public HealthContributor dbHealthContributor(@Qualifier("dataSource") DataSource core) {
        return new DataSourceHealthIndicator(core);
    }

    @Bean(name = "cnnDataSource")
    @Conditional(CnnDbUrlCondition.class)
    public HikariDataSource cnnDataSource(CnnProperties properties) {
        CnnProperties.Db db = properties.getDb();
        HikariConfig config = new HikariConfig();
        config.setPoolName("cnn-readonly");
        config.setJdbcUrl(db.getUrl().trim());
        config.setUsername(firstNonBlank(db.getUsername(), System.getProperty("MA_CNN_DB_USERNAME"), System.getenv("MA_CNN_DB_USERNAME")));
        String password = firstNonBlank(db.getPassword(), System.getProperty("MA_CNN_DB_PASSWORD"), System.getenv("MA_CNN_DB_PASSWORD"));
        config.setPassword(password);
        log.info("Source Colis na Nga : mot de passe {}", password.isBlank() ? "absent" : "présent");
        config.setMaximumPoolSize(3);
        config.setMinimumIdle(0);
        config.setConnectionTimeout(5_000);
        config.setValidationTimeout(1_000);
        config.setIdleTimeout(30_000);
        config.setMaxLifetime(120_000);
        config.setLeakDetectionThreshold(20_000);
        config.setInitializationFailTimeout(-1);
        config.setConnectionInitSql("SET SESSION TRANSACTION READ ONLY");
        config.setReadOnly(true);
        config.setAutoCommit(true);
        return new HikariDataSource(config);
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    @Bean(name = "cnnJdbcTemplate")
    @Conditional(CnnDbUrlCondition.class)
    public JdbcTemplate cnnJdbcTemplate(@Qualifier("cnnDataSource") DataSource cnnDataSource) {
        JdbcTemplate template = new JdbcTemplate(cnnDataSource);
        template.setQueryTimeout(5);
        return template;
    }
}
