package com.caracore.hub_town.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Objects;

@Entity
@Table(name = "posicao", uniqueConstraints = {
    @UniqueConstraint(name = "ux_posicao_codigo", columnNames = {"rua", "modulo", "nivel", "caixa"})
})
public class Posicao {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Size(max = 16)
    @Column(name = "rua", nullable = false, length = 16)
    private String rua;

    @NotBlank
    @Size(max = 16)
    @Column(name = "modulo", nullable = false, length = 16)
    private String modulo;

    @NotBlank
    @Size(max = 16)
    @Column(name = "nivel", nullable = false, length = 16)
    private String nivel;

    @NotBlank
    @Size(max = 16)
    @Column(name = "caixa", nullable = false, length = 16)
    private String caixa;

    @Column(name = "ocupada", nullable = false)
    private boolean ocupada = false;

    public String getCodigo() {
        return String.format("%s-%s-%s-%s", rua, modulo, nivel, caixa);
    }

    public void marcarOcupada() { this.ocupada = true; }
    public void liberar() { this.ocupada = false; }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getRua() { return rua; }
    public void setRua(String rua) { this.rua = rua; }
    public String getModulo() { return modulo; }
    public void setModulo(String modulo) { this.modulo = modulo; }
    public String getNivel() { return nivel; }
    public void setNivel(String nivel) { this.nivel = nivel; }
    public String getCaixa() { return caixa; }
    public void setCaixa(String caixa) { this.caixa = caixa; }
    public boolean isOcupada() { return ocupada; }
    public void setOcupada(boolean ocupada) { this.ocupada = ocupada; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Posicao posicao = (Posicao) o;
        return Objects.equals(id, posicao.id) && Objects.equals(getCodigo(), posicao.getCodigo());
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, getCodigo());
    }
}
