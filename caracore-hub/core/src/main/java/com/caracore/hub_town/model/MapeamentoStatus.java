package com.caracore.hub_town.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Entidade para mapeamento entre status externos (do canal) e status internos do Hub.
 */
@Entity
@Table(name = "mapeamento_status",
       uniqueConstraints = @UniqueConstraint(name = "ux_mapeamento_status", columnNames = {"canal", "tenant_id", "status_externo"}))
public class MapeamentoStatus {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "canal", nullable = false, length = 32)
    private CanalPedido canal;

    @NotNull
    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @NotBlank
    @Size(max = 64)
    @Column(name = "status_externo", nullable = false, length = 64)
    private String statusExterno;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status_interno", nullable = false, length = 32)
    private PedidoStatus statusInterno;

    @Column(name = "terminal", nullable = false)
    private Boolean terminal = false;

    @Column(name = "ativo", nullable = false)
    private Boolean ativo = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // Constructors
    public MapeamentoStatus() {}

    public MapeamentoStatus(CanalPedido canal, Long tenantId, String statusExterno, PedidoStatus statusInterno) {
        this.canal = canal;
        this.tenantId = tenantId;
        this.statusExterno = statusExterno;
        this.statusInterno = statusInterno;
    }

    // Business methods
    public boolean isAtivo() {
        return Boolean.TRUE.equals(ativo);
    }

    public boolean isTerminal() {
        return Boolean.TRUE.equals(terminal);
    }

    public void ativar() {
        this.ativo = true;
    }

    public void desativar() {
        this.ativo = false;
    }

    public void marcarTerminal() {
        this.terminal = true;
    }

    public void desmarcarTerminal() {
        this.terminal = false;
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public CanalPedido getCanal() { return canal; }
    public void setCanal(CanalPedido canal) { this.canal = canal; }

    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }

    public String getStatusExterno() { return statusExterno; }
    public void setStatusExterno(String statusExterno) { this.statusExterno = statusExterno; }

    public PedidoStatus getStatusInterno() { return statusInterno; }
    public void setStatusInterno(PedidoStatus statusInterno) { this.statusInterno = statusInterno; }

    public Boolean getTerminal() { return terminal; }
    public void setTerminal(Boolean terminal) { this.terminal = terminal; }

    public Boolean getAtivo() { return ativo; }
    public void setAtivo(Boolean ativo) { this.ativo = ativo; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MapeamentoStatus that = (MapeamentoStatus) o;
        return Objects.equals(id, that.id) && 
               canal == that.canal && 
               Objects.equals(tenantId, that.tenantId) && 
               Objects.equals(statusExterno, that.statusExterno);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, canal, tenantId, statusExterno);
    }

    @Override
    public String toString() {
        return "MapeamentoStatus{" +
                "id=" + id +
                ", canal=" + canal +
                ", tenantId=" + tenantId +
                ", statusExterno='" + statusExterno + '\'' +
                ", statusInterno=" + statusInterno +
                ", terminal=" + terminal +
                ", ativo=" + ativo +
                '}';
    }
}
