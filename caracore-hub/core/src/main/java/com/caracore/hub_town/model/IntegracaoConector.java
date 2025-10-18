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
 * Entidade para configuração de conectores de integração.
 * Armazena credenciais e configurações para conectar com canais externos (ML, Shopee, etc.)
 */
@Entity
@Table(name = "integracao_conector", 
       uniqueConstraints = @UniqueConstraint(name = "ux_conector_canal_tenant", columnNames = {"canal", "tenant_id"}),
       indexes = {
           @Index(name = "ix_conector_tenant_status", columnList = "tenant_id, status")
       })
public class IntegracaoConector {
    
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
    @Size(max = 128)
    @Column(name = "client_id", nullable = false, length = 128)
    private String clientId;

    @NotBlank
    @Column(name = "client_secret_enc", nullable = false, columnDefinition = "TEXT")
    private String clientSecretEncrypted;

    @Column(name = "refresh_token_enc", columnDefinition = "TEXT")
    private String refreshTokenEncrypted;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private StatusConector status = StatusConector.ATIVO;

    @Column(name = "last_sync_at")
    private LocalDateTime lastSyncAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // Constructors
    public IntegracaoConector() {}

    public IntegracaoConector(CanalPedido canal, Long tenantId, String clientId, String clientSecretEncrypted) {
        this.canal = canal;
        this.tenantId = tenantId;
        this.clientId = clientId;
        this.clientSecretEncrypted = clientSecretEncrypted;
    }

    // Business methods
    public boolean isAtivo() {
        return status == StatusConector.ATIVO;
    }

    public void ativar() {
        this.status = StatusConector.ATIVO;
    }

    public void desativar() {
        this.status = StatusConector.INATIVO;
    }

    public void marcarErro() {
        this.status = StatusConector.ERRO;
    }

    public void atualizarSincronizacao() {
        this.lastSyncAt = LocalDateTime.now();
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public CanalPedido getCanal() { return canal; }
    public void setCanal(CanalPedido canal) { this.canal = canal; }

    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }

    public String getClientId() { return clientId; }
    public void setClientId(String clientId) { this.clientId = clientId; }

    public String getClientSecretEncrypted() { return clientSecretEncrypted; }
    public void setClientSecretEncrypted(String clientSecretEncrypted) { this.clientSecretEncrypted = clientSecretEncrypted; }

    public String getRefreshTokenEncrypted() { return refreshTokenEncrypted; }
    public void setRefreshTokenEncrypted(String refreshTokenEncrypted) { this.refreshTokenEncrypted = refreshTokenEncrypted; }

    public StatusConector getStatus() { return status; }
    public void setStatus(StatusConector status) { this.status = status; }

    public LocalDateTime getLastSyncAt() { return lastSyncAt; }
    public void setLastSyncAt(LocalDateTime lastSyncAt) { this.lastSyncAt = lastSyncAt; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        IntegracaoConector that = (IntegracaoConector) o;
        return Objects.equals(id, that.id) && 
               canal == that.canal && 
               Objects.equals(tenantId, that.tenantId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, canal, tenantId);
    }

    @Override
    public String toString() {
        return "IntegracaoConector{" +
                "id=" + id +
                ", canal=" + canal +
                ", tenantId=" + tenantId +
                ", clientId='" + clientId + '\'' +
                ", status=" + status +
                ", lastSyncAt=" + lastSyncAt +
                '}';
    }
}