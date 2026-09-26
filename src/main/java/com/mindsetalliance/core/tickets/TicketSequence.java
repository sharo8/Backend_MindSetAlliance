package com.mindsetalliance.core.tickets;

import com.mindsetalliance.core.iam.Project;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "ticket_sequences")
public class TicketSequence {

    @Id
    private Long projectId;

    @OneToOne
    @MapsId
    @JoinColumn(name = "project_id")
    private Project project;

    @jakarta.persistence.Column(name = "derniere_valeur")
    private int lastValue;

    public Long getProjectId() { return projectId; }
    public Project getProject() { return project; }
    public void setProject(Project project) { this.project = project; }
    public int getLastValue() { return lastValue; }
    public void setLastValue(int lastValue) { this.lastValue = lastValue; }
}
