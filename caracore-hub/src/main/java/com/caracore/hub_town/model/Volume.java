package com.caracore.hub_town.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.Objects;

@Entity
@Table(name = "volume", indexes = {
    @Index(name = "ix_volume_status", columnList = "status"),
    @Index(name = "ix_volume_etiqueta", columnList = "etiqueta")
})
public class Volume {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pedido_id", nullable = false)
    private Pedido pedido;

    @NotBlank
    @Size(max = 64)
    @Column(name = "etiqueta", nullable = false, length = 64, unique = true)
    private String etiqueta;

    @Column(name = "peso", precision = 10, scale = 2)
    private BigDecimal peso;

    @Size(max = 64)
    @Column(name = "dimensoes", length = 64)
    private String dimensoes;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private VolumeStatus status = VolumeStatus.RECEBIDO;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "posicao_id")
    private Posicao posicao;

    public void atribuirPosicao(Posicao novaPosicao) {
        this.posicao = novaPosicao;
        if (novaPosicao != null) {
            this.status = VolumeStatus.ALOCADO;
        }
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Pedido getPedido() { return pedido; }
    public void setPedido(Pedido pedido) { this.pedido = pedido; }
    public String getEtiqueta() { return etiqueta; }
    public void setEtiqueta(String etiqueta) { this.etiqueta = etiqueta; }
    public BigDecimal getPeso() { return peso; }
    public void setPeso(BigDecimal peso) { this.peso = peso; }
    public String getDimensoes() { return dimensoes; }
    public void setDimensoes(String dimensoes) { this.dimensoes = dimensoes; }
    public VolumeStatus getStatus() { return status; }
    public void setStatus(VolumeStatus status) { this.status = status; }
    public Posicao getPosicao() { return posicao; }
    public void setPosicao(Posicao posicao) { this.posicao = posicao; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Volume volume = (Volume) o;
        return Objects.equals(id, volume.id) && Objects.equals(etiqueta, volume.etiqueta);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, etiqueta);
    }
}
