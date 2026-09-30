package com.mindsetalliance.core.vitrine;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Transport Kafka (optionnel). La logique vit dans {@link ProjectEventProcessor},
 * partagée avec l'ingestion HTTP signée de Colis na Nga.
 */
@Component
@ConditionalOnProperty(name = "ma.kafka.enabled", havingValue = "true", matchIfMissing = true)
public class ProjectEventListener {

    private final ProjectEventProcessor processor;

    public ProjectEventListener(ProjectEventProcessor processor) {
        this.processor = processor;
    }

    @KafkaListener(topics = "${ma.kafka.topics.courses}", groupId = "ma-core")
    public void onCourseEvent(Map<String, Object> event) {
        processor.onCourseEvent(event);
    }

    @KafkaListener(topics = "${ma.kafka.topics.coursiers}", groupId = "ma-core")
    public void onCoursierEvent(Map<String, Object> event) {
        processor.onCoursierEvent(event);
    }

    @KafkaListener(topics = "${ma.kafka.topics.contenus}", groupId = "ma-core")
    public void onContenuEvent(Map<String, Object> event) {
        processor.onContenuEvent(event);
    }
}
