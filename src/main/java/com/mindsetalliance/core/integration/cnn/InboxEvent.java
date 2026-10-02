package com.mindsetalliance.core.integration.cnn;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "integration_inbox")
public class InboxEvent {

    @Id
    @Column(name = "event_id", length = 64)
    private String eventId;

    @Column(nullable = false, length = 20)
    private String source;

    @Column(nullable = false, length = 100)
    private String topic;

    @Column(length = 60)
    private String type;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt = Instant.now();

    protected InboxEvent() {
    }

    public InboxEvent(String eventId, String source, String topic, String type) {
        this.eventId = eventId;
        this.source = source;
        this.topic = topic;
        this.type = type;
    }

    public String getEventId() { return eventId; }
    public String getSource() { return source; }
    public String getTopic() { return topic; }
    public String getType() { return type; }
    public Instant getReceivedAt() { return receivedAt; }
}
