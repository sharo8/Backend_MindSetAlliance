package com.mindsetalliance.core.tickets;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "ma.tickets")
public class TicketSlaProperties {

    private int reouvertureJours = 30;
    private int critiqueHeures = 2;
    private int eleveeHeures = 8;
    private int normaleHeures = 24;
    private int faibleHeures = 72;

    public int getReouvertureJours() { return reouvertureJours; }
    public void setReouvertureJours(int reouvertureJours) { this.reouvertureJours = reouvertureJours; }
    public int getCritiqueHeures() { return critiqueHeures; }
    public void setCritiqueHeures(int critiqueHeures) { this.critiqueHeures = critiqueHeures; }
    public int getEleveeHeures() { return eleveeHeures; }
    public void setEleveeHeures(int eleveeHeures) { this.eleveeHeures = eleveeHeures; }
    public int getNormaleHeures() { return normaleHeures; }
    public void setNormaleHeures(int normaleHeures) { this.normaleHeures = normaleHeures; }
    public int getFaibleHeures() { return faibleHeures; }
    public void setFaibleHeures(int faibleHeures) { this.faibleHeures = faibleHeures; }

    public int heuresPour(String priorite) {
        if (priorite == null) {
            return normaleHeures;
        }
        return switch (priorite) {
            case "CRITIQUE" -> critiqueHeures;
            case "ELEVEE" -> eleveeHeures;
            case "FAIBLE" -> faibleHeures;
            default -> normaleHeures;
        };
    }
}
