package com.mindsetalliance.core;

import com.mindsetalliance.core.assistant.AssistantProperties;
import com.mindsetalliance.core.config.LocalEnvFile;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableAspectJAutoProxy
@EnableConfigurationProperties(AssistantProperties.class)
public class CoreApplication {

    public static void main(String[] args) {
        LocalEnvFile.load();
        SpringApplication.run(CoreApplication.class, args);
    }
}
