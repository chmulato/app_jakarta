package com.caracore.hub_town.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Entity
@Table(name = "pedido", indexes = {
    @Index(name = "ix_pedido_status", columnList = "status"),
    @Index(name = "ix_pedido_created_at", columnList = "created_at")
})
public class Pedido {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Size(max = 64)
    @Column(name = "codigo", nullable = false, unique = true, length = 64)
    private String codigo;

    @Enumerated(EnumType.STRING)
    @Column(name = "canal", nullable = false, length = 32)
    private CanalPedido canal = CanalPedido.MANUAL;

    @Size(max = 128)
    @Column(name = "external_id", length = 128)
    private String externalId;

    @NotBlank
    @Size(max = 120)
    @Column(name = "destinatario_nome", nullable = false, length = 120)
    private String destinatarioNome;

    @NotBlank
    @Size(max = 32)
    @Column(name = "destinatario_documento", nullable = false, length = 32)
    private String destinatarioDocumento;

    @NotBlank
    @Size(max = 32)
    @Column(name = "destinatario_telefone", nullable = false, length = 32)
    private String destinatarioTelefone;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PedidoStatus status = PedidoStatus.RECEBIDO;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "ready_at")
    private LocalDateTime readyAt;

    @Column(name = "picked_up_at")
    private LocalDateTime pickedUpAt;

    @Column(name = "tenant_id")
    private Long tenantId;

    @OneToMany(mappedBy = "pedido", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<Volume> volumes = new ArrayList<>();

    @OneToMany(mappedBy = "pedido", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("createdAt ASC")
    private List<EventoPedido> eventos = new ArrayList<>();

    @PrePersist
    public void prePersist() {
        if (codigo == null || codigo.isBlank()) {
            codigo = "PED-" + System.currentTimeMillis();
        }
        if (canal == null) {
            canal = CanalPedido.MANUAL;
        }
    }

    public void adicionarVolume(Volume volume) {
        volume.setPedido(this);
        volumes.add(volume);
    }

    public void removerVolume(Volume volume) {
        volumes.remove(volume);
        volume.setPedido(null);
    }

    public void adicionarEvento(EventoPedido evento) {
        evento.setPedido(this);
        eventos.add(evento);
    }

    public boolean podeSerMarcadoComoPronto() {
        return status == PedidoStatus.RECEBIDO && volumes.stream().allMatch(v -> v.getStatus() == VolumeStatus.ALOCADO || v.getStatus() == VolumeStatus.PRONTO);
    }

    public void marcarPronto() {
        if (podeSerMarcadoComoPronto()) {
            status = PedidoStatus.PRONTO;
            readyAt = LocalDateTime.now();
            volumes.forEach(v -> {
                if (v.getStatus() == VolumeStatus.ALOCADO) {
                    v.setStatus(VolumeStatus.PRONTO);
                }
            });
        }
    }

    public void marcarRetirado() {
        if (status == PedidoStatus.PRONTO) {
            status = PedidoStatus.RETIRADO;
            pickedUpAt = LocalDateTime.now();
            volumes.forEach(v -> v.setStatus(VolumeStatus.RETIRADO));
        }
    }

    // Getters & Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getCodigo() { return codigo; }
    public void setCodigo(String codigo) { this.codigo = codigo; }
    public CanalPedido getCanal() { return canal; }
    public void setCanal(CanalPedido canal) { this.canal = canal; }
    public String getExternalId() { return externalId; }
    public void setExternalId(String externalId) { this.externalId = externalId; }
    public String getDestinatarioNome() { return destinatarioNome; }
    public void setDestinatarioNome(String destinatarioNome) { this.destinatarioNome = destinatarioNome; }
    public String getDestinatarioDocumento() { return destinatarioDocumento; }
    public void setDestinatarioDocumento(String destinatarioDocumento) { this.destinatarioDocumento = destinatarioDocumento; }
    public String getDestinatarioTelefone() { return destinatarioTelefone; }
    public void setDestinatarioTelefone(String destinatarioTelefone) { this.destinatarioTelefone = destinatarioTelefone; }
    public PedidoStatus getStatus() { return status; }
    public void setStatus(PedidoStatus status) { this.status = status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getReadyAt() { return readyAt; }
    public void setReadyAt(LocalDateTime readyAt) { this.readyAt = readyAt; }
    public LocalDateTime getPickedUpAt() { return pickedUpAt; }
    public void setPickedUpAt(LocalDateTime pickedUpAt) { this.pickedUpAt = pickedUpAt; }
    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
    public List<Volume> getVolumes() { return volumes; }
    public void setVolumes(List<Volume> volumes) { this.volumes = volumes; }
    public List<EventoPedido> getEventos() { return eventos; }
    public void setEventos(List<EventoPedido> eventos) { this.eventos = eventos; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Pedido pedido = (Pedido) o;
        return Objects.equals(id, pedido.id) && Objects.equals(codigo, pedido.codigo);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, codigo);
    }
}
