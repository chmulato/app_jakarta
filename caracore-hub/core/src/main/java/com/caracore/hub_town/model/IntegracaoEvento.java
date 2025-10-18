package com.caracore.hub_town.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Entidade para armazenar eventos recebidos via webhook das integrações.
 * Cada evento representa uma mudança no pedido/volume no sistema externo.
 */
@Entity
@Table(name = "integracao_evento",
       indexes = {
           @Index(name = "ix_evento_tenant_canal", columnList = "tenant_id, canal, received_at"),
           @Index(name = "ix_evento_status_received", columnList = "status, received_at"),
           @Index(name = "ix_evento_trace_id", columnList = "trace_id")
       })
public class IntegracaoEvento {
    
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
    @Column(name = "external_id", nullable = false, length = 128)
    private String externalId;

    @NotBlank
    @Size(max = 64)
    @Column(name = "tipo", nullable = false, length = 64)
    private String tipo;

    @NotNull
    @Column(name = "payload_json", nullable = false, columnDefinition = "JSONB")
    private String payloadJson;

    @CreationTimestamp
    @Column(name = "received_at", nullable = false, updatable = false)
    private LocalDateTime receivedAt;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private StatusEvento status = StatusEvento.RECEBIDO;

    @Column(name = "error_msg", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "reprocess_count", nullable = false)
    private Integer reprocessCount = 0;

    @NotNull
    @Column(name = "trace_id", nullable = false, columnDefinition = "UUID")
    private UUID traceId;

    // Constructors
    public IntegracaoEvento() {
        this.traceId = UUID.randomUUID();
    }

    public IntegracaoEvento(CanalPedido canal, Long tenantId, String externalId, String tipo, String payloadJson) {
        this();
        this.canal = canal;
        this.tenantId = tenantId;
        this.externalId = externalId;
        this.tipo = tipo;
        this.payloadJson = payloadJson;
    }

    // Business methods
    public boolean isProcessado() {
        return status == StatusEvento.PROCESSADO;
    }

    public boolean temErro() {
        return status == StatusEvento.ERRO;
    }

    public void marcarProcessando() {
        this.status = StatusEvento.PROCESSANDO;
    }

    public void marcarProcessado() {
        this.status = StatusEvento.PROCESSADO;
        this.processedAt = LocalDateTime.now();
        this.errorMessage = null;
    }

    public void marcarErro(String errorMessage) {
        this.status = StatusEvento.ERRO;
        this.errorMessage = errorMessage;
        this.reprocessCount++;
    }

    public void marcarIgnorado() {
        this.status = StatusEvento.IGNORADO;
        this.processedAt = LocalDateTime.now();
    }

    public boolean podeReprocessar() {
        return (status == StatusEvento.ERRO || status == StatusEvento.IGNORADO) && reprocessCount < 3;
    }

    public void resetarParaReprocessamento() {
        if (!podeReprocessar()) {
            throw new IllegalStateException("Evento não pode ser reprocessado");
        }
        this.status = StatusEvento.RECEBIDO;
        this.processedAt = null;
        this.errorMessage = null;
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public CanalPedido getCanal() { return canal; }
    public void setCanal(CanalPedido canal) { this.canal = canal; }

    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }

    public String getExternalId() { return externalId; }
    public void setExternalId(String externalId) { this.externalId = externalId; }

    public String getTipo() { return tipo; }
    public void setTipo(String tipo) { this.tipo = tipo; }

    public String getPayloadJson() { return payloadJson; }
    public void setPayloadJson(String payloadJson) { this.payloadJson = payloadJson; }

    public LocalDateTime getReceivedAt() { return receivedAt; }
    public void setReceivedAt(LocalDateTime receivedAt) { this.receivedAt = receivedAt; }

    public LocalDateTime getProcessedAt() { return processedAt; }
    public void setProcessedAt(LocalDateTime processedAt) { this.processedAt = processedAt; }

    public StatusEvento getStatus() { return status; }
    public void setStatus(StatusEvento status) { this.status = status; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public Integer getReprocessCount() { return reprocessCount; }
    public void setReprocessCount(Integer reprocessCount) { this.reprocessCount = reprocessCount; }

    public UUID getTraceId() { return traceId; }
    public void setTraceId(UUID traceId) { this.traceId = traceId; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        IntegracaoEvento that = (IntegracaoEvento) o;
        return Objects.equals(id, that.id) && Objects.equals(traceId, that.traceId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, traceId);
    }

    @Override
    public String toString() {
        return "IntegracaoEvento{" +
                "id=" + id +
                ", canal=" + canal +
                ", tenantId=" + tenantId +
                ", externalId='" + externalId + '\'' +
                ", tipo='" + tipo + '\'' +
                ", status=" + status +
                ", receivedAt=" + receivedAt +
                ", processedAt=" + processedAt +
                ", traceId=" + traceId +
                '}';
    }
}