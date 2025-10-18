package com.caracore.hub_town.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Entidade para controle de idempotência no processamento de eventos.
 * Evita o processamento duplicado de eventos com a mesma chave.
 */
@Entity
@Table(name = "idempotencia")
public class Idempotencia {
    
    @Id
    @NotBlank
    @Size(max = 256)
    @Column(name = "chave", length = 256)
    private String chave;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // Constructors
    public Idempotencia() {}

    public Idempotencia(String chave) {
        this.chave = chave;
    }

    /**
     * Gera a chave de idempotência para um evento.
     * Formato: {canal}|{tenantId}|{tipo}|{externalId}|v{versao}
     */
    public static String gerarChave(CanalPedido canal, Long tenantId, String tipo, String externalId, int versao) {
        return String.format("%s|%d|%s|%s|v%d", canal.name(), tenantId, tipo, externalId, versao);
    }

    /**
     * Gera a chave de idempotência com versão 1 (padrão).
     */
    public static String gerarChave(CanalPedido canal, Long tenantId, String tipo, String externalId) {
        return gerarChave(canal, tenantId, tipo, externalId, 1);
    }

    // Getters and Setters
    public String getChave() { return chave; }
    public void setChave(String chave) { this.chave = chave; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Idempotencia that = (Idempotencia) o;
        return Objects.equals(chave, that.chave);
    }

    @Override
    public int hashCode() {
        return Objects.hash(chave);
    }

    @Override
    public String toString() {
        return "Idempotencia{" +
                "chave='" + chave + '\'' +
                ", createdAt=" + createdAt +
                '}';
    }
}