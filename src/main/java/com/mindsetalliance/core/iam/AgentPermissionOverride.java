package com.mindsetalliance.core.iam;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "agent_permission_overrides")
public class AgentPermissionOverride {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agent_id", nullable = false)
    private Agent agent;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "permission_id", nullable = false)
    private Permission permission;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id")
    private Project project;

    @Column(nullable = false, length = 10)
    private String type;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String motif;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "accorde_par", nullable = false)
    private Agent accordePar;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public Long getId() { return id; }
    public Agent getAgent() { return agent; }
    public void setAgent(Agent agent) { this.agent = agent; }
    public Permission getPermission() { return permission; }
    public void setPermission(Permission permission) { this.permission = permission; }
    public Project getProject() { return project; }
    public void setProject(Project project) { this.project = project; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getMotif() { return motif; }
    public void setMotif(String motif) { this.motif = motif; }
    public Agent getAccordePar() { return accordePar; }
    public void setAccordePar(Agent accordePar) { this.accordePar = accordePar; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
