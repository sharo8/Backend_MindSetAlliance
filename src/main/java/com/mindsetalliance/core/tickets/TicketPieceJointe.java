package com.mindsetalliance.core.tickets;

import com.mindsetalliance.core.iam.Agent;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Column;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "ticket_pieces_jointes")
public class TicketPieceJointe {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ticket_id")
    private Ticket ticket;

    private String nomFichier;
    private String typeMime;
    @Lob
    @Column(columnDefinition = "longblob")
    private byte[] contenu;
    private Instant createdAt = Instant.now();

    public Long getId() { return id; }
    public Ticket getTicket() { return ticket; }
    public void setTicket(Ticket ticket) { this.ticket = ticket; }
    public String getNomFichier() { return nomFichier; }
    public void setNomFichier(String nomFichier) { this.nomFichier = nomFichier; }
    public String getTypeMime() { return typeMime; }
    public void setTypeMime(String typeMime) { this.typeMime = typeMime; }
    @com.fasterxml.jackson.annotation.JsonIgnore
    public byte[] getContenu() { return contenu; }
    public void setContenu(byte[] contenu) { this.contenu = contenu; }
    public Instant getCreatedAt() { return createdAt; }
}
