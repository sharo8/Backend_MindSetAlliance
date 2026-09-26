package com.mindsetalliance.core.hr;

import com.mindsetalliance.core.iam.Agent;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "presences")
public class Presence {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne
    @JoinColumn(name = "agent_id")
    private Agent agent;
    private LocalDate jour;
    private String type;
    @Column(precision = 5, scale = 2)
    private BigDecimal heures;

    public Long getId() { return id; }
    public Agent getAgent() { return agent; }
    public void setAgent(Agent agent) { this.agent = agent; }
    public LocalDate getJour() { return jour; }
    public void setJour(LocalDate jour) { this.jour = jour; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public BigDecimal getHeures() { return heures; }
    public void setHeures(BigDecimal heures) { this.heures = heures; }
}
