package com.caracore.hub_town.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "usuarios", indexes = {
    @Index(name = "idx_usuario_email", columnList = "email"),
    @Index(name = "idx_usuario_ativo", columnList = "ativo")
})
public class Usuario {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @NotBlank(message = "Nome é obrigatório")
    @Size(min = 2, max = 100, message = "Nome deve ter entre 2 e 100 caracteres")
    @Column(name = "nome", nullable = false, length = 100)
    private String nome;
    @NotBlank(message = "Email é obrigatório")
    @Email(message = "Email deve ter formato válido")
    @Size(max = 150, message = "Email deve ter no máximo 150 caracteres")
    @Column(name = "email", nullable = false, unique = true, length = 150)
    private String email;
    @NotBlank(message = "Senha é obrigatória")
    @Size(min = 6, message = "Senha deve ter no mínimo 6 caracteres")
    @Column(name = "senha", nullable = false, length = 255)
    private String senha;
    @Column(name = "ativo", nullable = false)
    private Boolean ativo = true;
    @Enumerated(EnumType.STRING)
    @Column(name = "perfil", nullable = false, length = 20)
    private PerfilUsuario perfil = PerfilUsuario.USUARIO;
    @CreationTimestamp
    @Column(name = "data_criacao", nullable = false, updatable = false)
    private LocalDateTime dataCriacao;
    @UpdateTimestamp
    @Column(name = "data_atualizacao", nullable = false)
    private LocalDateTime dataAtualizacao;

    public Usuario() {}
    public Usuario(String nome, String email, String senha) { this.nome = nome; this.email = email; this.senha = senha; }
    public Usuario(String nome, String email, String senha, PerfilUsuario perfil) { this.nome = nome; this.email = email; this.senha = senha; this.perfil = perfil; }
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getSenha() { return senha; }
    public void setSenha(String senha) { this.senha = senha; }
    public Boolean getAtivo() { return ativo; }
    public void setAtivo(Boolean ativo) { this.ativo = ativo; }
    public PerfilUsuario getPerfil() { return perfil; }
    public void setPerfil(PerfilUsuario perfil) { this.perfil = perfil; }
    public LocalDateTime getDataCriacao() { return dataCriacao; }
    public void setDataCriacao(LocalDateTime dataCriacao) { this.dataCriacao = dataCriacao; }
    public LocalDateTime getDataAtualizacao() { return dataAtualizacao; }
    public void setDataAtualizacao(LocalDateTime dataAtualizacao) { this.dataAtualizacao = dataAtualizacao; }
    public boolean isAtivo() { return ativo != null && ativo; }
    public boolean isAdmin() { return PerfilUsuario.ADMIN.equals(perfil); }
    @Override public boolean equals(Object o) { if (this == o) return true; if (o == null || getClass() != o.getClass()) return false; Usuario u = (Usuario) o; return Objects.equals(id, u.id) && Objects.equals(email, u.email); }
    @Override public int hashCode() { return Objects.hash(id, email); }
    @Override public String toString() { return "Usuario{" + "id=" + id + ", nome='" + nome + '\'' + ", email='" + email + '\'' + ", ativo=" + ativo + ", perfil=" + perfil + ", dataCriacao=" + dataCriacao + '}'; }
    public enum PerfilUsuario { ADMIN("Administrador"), USUARIO("Usuário"); private final String descricao; PerfilUsuario(String descricao) { this.descricao = descricao; } public String getDescricao() { return descricao; } }
}
