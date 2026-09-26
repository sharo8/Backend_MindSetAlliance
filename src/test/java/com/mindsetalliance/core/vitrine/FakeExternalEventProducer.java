package com.mindsetalliance.core.vitrine;

import org.springframework.kafka.core.KafkaTemplate;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Producteur de test uniquement : publie des événements factices conformes à
 * {@code docs/contrats-evenements.md}. Aucune logique métier des backends externes.
 */
public final class FakeExternalEventProducer {

    public static final String TOPIC_COURSES = "colisnanga.courses.events";
    public static final String TOPIC_COURSIERS = "colisnanga.coursiers.events";
    public static final String TOPIC_CONTENUS = "mdr.contenus.events";

    private FakeExternalEventProducer() {
    }

    public static Map<String, Object> courseLivree() {
        Map<String, Object> event = base("COURSE_LIVREE", "CNN");
        event.put("courseId", 421);
        event.put("delta", 1);
        return event;
    }

    public static Map<String, Object> incidentSignale() {
        Map<String, Object> event = base("INCIDENT_SIGNALE", "CNN");
        event.put("courseId", 421);
        event.put("incidentId", 88);
        event.put("titre", "Colis endommagé — Gombe");
        event.put("description", "Événement factice de test");
        event.put("ticketReference", "");
        event.put("delta", 1);
        return event;
    }

    public static Map<String, Object> coursierValide() {
        Map<String, Object> event = base("COURSIER_VALIDE", "CNN");
        event.put("coursierId", 12);
        event.put("delta", 1);
        return event;
    }

    public static Map<String, Object> statistiquesMisesAJour() {
        Map<String, Object> event = base("STATISTIQUES_MISES_A_JOUR", "MDR");
        event.put("contenuId", 7);
        event.put("vues", 12500);
        event.put("abonnesGagnes", 340);
        return event;
    }

    public static void publish(KafkaTemplate<String, Object> kafka, String topic, Map<String, Object> event) {
        kafka.send(topic, event);
    }

    private static Map<String, Object> base(String type, String projectCode) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("type", type);
        event.put("projectCode", projectCode);
        event.put("horodatage", Instant.parse("2026-09-16T20:15:00Z").toString());
        return event;
    }
}
