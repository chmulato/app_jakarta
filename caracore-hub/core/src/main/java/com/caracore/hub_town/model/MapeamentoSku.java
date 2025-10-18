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
 * Entidade para mapeamento entre SKUs externos (do canal) e SKUs internos do Hub.
 */
@Entity
@Table(name = "mapeamento_sku",
       uniqueConstraints = @UniqueConstraint(name = "ux_mapeamento_sku", columnNames = {"canal", "tenant_id", "sku_externo"}))
public class MapeamentoSku {
    
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
    @Column(name = "sku_externo", nullable = false, length = 128)
    private String skuExterno;

    @NotBlank
    @Size(max = 128)
    @Column(name = "sku_interno", nullable = false, length = 128)
    private String skuInterno;

    @Column(name = "ativo", nullable = false)
    private Boolean ativo = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // Constructors
    public MapeamentoSku() {}

    public MapeamentoSku(CanalPedido canal, Long tenantId, String skuExterno, String skuInterno) {
        this.canal = canal;
        this.tenantId = tenantId;
        this.skuExterno = skuExterno;
        this.skuInterno = skuInterno;
    }

    // Business methods
    public boolean isAtivo() {
        return Boolean.TRUE.equals(ativo);
    }

    public void ativar() {
        this.ativo = true;
    }

    public void desativar() {
        this.ativo = false;
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public CanalPedido getCanal() { return canal; }
    public void setCanal(CanalPedido canal) { this.canal = canal; }

    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }

    public String getSkuExterno() { return skuExterno; }
    public void setSkuExterno(String skuExterno) { this.skuExterno = skuExterno; }

    public String getSkuInterno() { return skuInterno; }
    public void setSkuInterno(String skuInterno) { this.skuInterno = skuInterno; }

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
        MapeamentoSku that = (MapeamentoSku) o;
        return Objects.equals(id, that.id) && 
               canal == that.canal && 
               Objects.equals(tenantId, that.tenantId) && 
               Objects.equals(skuExterno, that.skuExterno);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, canal, tenantId, skuExterno);
    }

    @Override
    public String toString() {
        return "MapeamentoSku{" +
                "id=" + id +
                ", canal=" + canal +
                ", tenantId=" + tenantId +
                ", skuExterno='" + skuExterno + '\'' +
                ", skuInterno='" + skuInterno + '\'' +
                ", ativo=" + ativo +
                '}';
    }
}