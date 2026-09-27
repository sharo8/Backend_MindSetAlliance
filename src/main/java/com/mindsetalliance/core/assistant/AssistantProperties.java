package com.mindsetalliance.core.assistant;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ma.groq")
public class AssistantProperties {

    private String apiKey = "";
    private String baseUrl = "https://api.groq.com/openai/v1";
    private String model = "openai/gpt-oss-120b";
    private int timeoutSeconds = 25;

    public String getApiKey() {
        return apiKey == null ? "" : apiKey.trim();
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    public boolean configured() {
        return !getApiKey().isBlank();
    }
}
