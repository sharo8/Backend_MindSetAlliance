package com.mindsetalliance.core.tickets;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.mindsetalliance.core.iam.Agent;
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
@Table(name = "ticket_statut_historique")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class TicketStatutHistorique {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "ticket_id")
    @com.fasterxml.jackson.annotation.JsonIgnore
    private Ticket ticket;

    private String statutAvant;
    private String statutApres;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agent_id")
    private Agent agent;

    private Instant createdAt = Instant.now();

    public Long getId() { return id; }
    public Ticket getTicket() { return ticket; }
    public void setTicket(Ticket ticket) { this.ticket = ticket; }
    public String getStatutAvant() { return statutAvant; }
    public void setStatutAvant(String statutAvant) { this.statutAvant = statutAvant; }
    public String getStatutApres() { return statutApres; }
    public void setStatutApres(String statutApres) { this.statutApres = statutApres; }
    public Agent getAgent() { return agent; }
    public void setAgent(Agent agent) { this.agent = agent; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
