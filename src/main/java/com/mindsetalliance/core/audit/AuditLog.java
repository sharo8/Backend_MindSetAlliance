package com.mindsetalliance.core.audit;

import com.mindsetalliance.core.common.JsonMapConverter;
import com.mindsetalliance.core.iam.Agent;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;

@Entity
@Table(name = "audit_log")
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agent_id")
    private Agent agent;

    private String action;
    private String objetType;
    private Long objetId;

    /**
     * Connector/J expose MySQL JSON comme LONGVARCHAR. SqlTypes.JSON ferait
     * échouer ddl-auto=validate (found LONGVARCHAR, expecting JSON).
     */
    @Convert(converter = JsonMapConverter.class)
    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "valeur_avant", columnDefinition = "json")
    private Map<String, Object> valeurAvant;

    @Convert(converter = JsonMapConverter.class)
    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "valeur_apres", columnDefinition = "json")
    private Map<String, Object> valeurApres;

    private Instant createdAt = Instant.now();

    public Long getId() { return id; }
    public Agent getAgent() { return agent; }
    public void setAgent(Agent agent) { this.agent = agent; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public String getObjetType() { return objetType; }
    public void setObjetType(String objetType) { this.objetType = objetType; }
    public Long getObjetId() { return objetId; }
    public void setObjetId(Long objetId) { this.objetId = objetId; }
    public Map<String, Object> getValeurAvant() { return valeurAvant; }
    public void setValeurAvant(Map<String, Object> valeurAvant) { this.valeurAvant = valeurAvant; }
    public Map<String, Object> getValeurApres() { return valeurApres; }
    public void setValeurApres(Map<String, Object> valeurApres) { this.valeurApres = valeurApres; }
    public Instant getCreatedAt() { return createdAt; }
}
