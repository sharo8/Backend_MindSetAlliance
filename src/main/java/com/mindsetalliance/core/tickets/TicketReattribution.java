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
@Table(name = "ticket_reattributions")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class TicketReattribution {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "ticket_id")
    @com.fasterxml.jackson.annotation.JsonIgnore
    private Ticket ticket;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ancien_agent_id")
    private Agent ancienAgent;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "nouvel_agent_id")
    private Agent nouvelAgent;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "auteur_id")
    private Agent auteur;

    private Instant createdAt = Instant.now();

    public Long getId() { return id; }
    public Ticket getTicket() { return ticket; }
    public void setTicket(Ticket ticket) { this.ticket = ticket; }
    public Agent getAncienAgent() { return ancienAgent; }
    public void setAncienAgent(Agent ancienAgent) { this.ancienAgent = ancienAgent; }
    public Agent getNouvelAgent() { return nouvelAgent; }
    public void setNouvelAgent(Agent nouvelAgent) { this.nouvelAgent = nouvelAgent; }
    public Agent getAuteur() { return auteur; }
    public void setAuteur(Agent auteur) { this.auteur = auteur; }
    public Instant getCreatedAt() { return createdAt; }
}
