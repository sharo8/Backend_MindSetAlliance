package com.mindsetalliance.core.config;

import com.fasterxml.jackson.datatype.hibernate6.Hibernate6Module;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class HibernateJsonConfig {

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer hibernateJackson() {
        return builder -> {
            Hibernate6Module module = new Hibernate6Module();
            module.disable(Hibernate6Module.Feature.USE_TRANSIENT_ANNOTATION);
            module.enable(Hibernate6Module.Feature.SERIALIZE_IDENTIFIER_FOR_LAZY_NOT_LOADED_OBJECTS);
            builder.modulesToInstall(module);
        };
    }
}
