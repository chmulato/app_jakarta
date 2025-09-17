package com.exemplo.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

import java.util.Set;

import static org.assertj.core.api.Assertions.*;

@DisplayName("Testes da entidade Usuario")
class UsuarioTest {

    private Validator validator;

    @BeforeEach
    void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @Test
    @DisplayName("Deve criar usuário válido")
    void deveCriarUsuarioValido() {
        // Given
        Usuario usuario = new Usuario();
        usuario.setNome("João Silva");
        usuario.setEmail("joao@exemplo.com");
        usuario.setSenha("senha123");
        usuario.setPerfil(Usuario.PerfilUsuario.USUARIO);

        // When
        Set<ConstraintViolation<Usuario>> violations = validator.validate(usuario);

        // Then
        assertThat(violations).isEmpty();
        assertThat(usuario.getNome()).isEqualTo("João Silva");
        assertThat(usuario.getEmail()).isEqualTo("joao@exemplo.com");
        assertThat(usuario.getSenha()).isEqualTo("senha123");
        assertThat(usuario.getPerfil()).isEqualTo(Usuario.PerfilUsuario.USUARIO);
        assertThat(usuario.isAtivo()).isTrue(); // valor padrão
    }

    @Test
    @DisplayName("Não deve aceitar nome nulo")
    void naoDeveAceitarNomeNulo() {
        // Given
        Usuario usuario = new Usuario();
        usuario.setEmail("joao@exemplo.com");
        usuario.setSenha("senha123");
        usuario.setPerfil(Usuario.PerfilUsuario.USUARIO);

        // When
        Set<ConstraintViolation<Usuario>> violations = validator.validate(usuario);

        // Then
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getMessage())
                .isEqualTo("Nome é obrigatório");
    }

    @Test
    @DisplayName("Não deve aceitar nome vazio")
    void naoDeveAceitarNomeVazio() {
        // Given
        Usuario usuario = new Usuario();
        usuario.setNome("");
        usuario.setEmail("joao@exemplo.com");
        usuario.setSenha("senha123");
        usuario.setPerfil(Usuario.PerfilUsuario.USUARIO);

        // When
        Set<ConstraintViolation<Usuario>> violations = validator.validate(usuario);

        // Then
        assertThat(violations).hasSizeGreaterThanOrEqualTo(1);
        // Verifica se há violação para nome obrigatório ou tamanho mínimo
        boolean temViolacaoNome = violations.stream()
                .anyMatch(v -> v.getMessage().contains("Nome") && 
                             (v.getMessage().contains("obrigatório") || v.getMessage().contains("caracteres")));
        assertThat(temViolacaoNome).isTrue();
    }

    @Test
    @DisplayName("Não deve aceitar email inválido")
    void naoDeveAceitarEmailInvalido() {
        // Given
        Usuario usuario = new Usuario();
        usuario.setNome("João Silva");
        usuario.setEmail("email-invalido");
        usuario.setSenha("senha123");
        usuario.setPerfil(Usuario.PerfilUsuario.USUARIO);

        // When
        Set<ConstraintViolation<Usuario>> violations = validator.validate(usuario);

        // Then
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getMessage())
                .isEqualTo("Email deve ter formato válido");
    }

    @Test
    @DisplayName("Não deve aceitar senha muito curta")
    void naoDeveAceitarSenhaMuitoCurta() {
        // Given
        Usuario usuario = new Usuario();
        usuario.setNome("João Silva");
        usuario.setEmail("joao@exemplo.com");
        usuario.setSenha("123");
        usuario.setPerfil(Usuario.PerfilUsuario.USUARIO);

        // When
        Set<ConstraintViolation<Usuario>> violations = validator.validate(usuario);

        // Then
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getMessage())
                .isEqualTo("Senha deve ter no mínimo 6 caracteres");
    }

    @Test
    @DisplayName("Deve ter construtores funcionais")
    void deveTerConstrutoresFuncionais() {
        // Given & When
        Usuario usuario1 = new Usuario();
        Usuario usuario2 = new Usuario("João", "joao@exemplo.com", "senha123");
        Usuario usuario3 = new Usuario("Admin", "admin@exemplo.com", "admin123", Usuario.PerfilUsuario.ADMIN);

        // Then
        assertThat(usuario1.getPerfil()).isEqualTo(Usuario.PerfilUsuario.USUARIO);
        assertThat(usuario1.isAtivo()).isTrue();
        
        assertThat(usuario2.getNome()).isEqualTo("João");
        assertThat(usuario2.getEmail()).isEqualTo("joao@exemplo.com");
        assertThat(usuario2.getSenha()).isEqualTo("senha123");
        
        assertThat(usuario3.getPerfil()).isEqualTo(Usuario.PerfilUsuario.ADMIN);
    }

    @Test
    @DisplayName("Deve testar método equals")
    void deveTestarMetodoEquals() {
        // Given
        Usuario usuario1 = new Usuario();
        usuario1.setId(1L);
        usuario1.setEmail("joao@exemplo.com");

        Usuario usuario2 = new Usuario();
        usuario2.setId(1L);
        usuario2.setEmail("joao@exemplo.com");

        Usuario usuario3 = new Usuario();
        usuario3.setId(2L);
        usuario3.setEmail("maria@exemplo.com");

        // Then
        assertThat(usuario1).isEqualTo(usuario2);
        assertThat(usuario1).isNotEqualTo(usuario3);
        assertThat(usuario1).isNotEqualTo(null);
        assertThat(usuario1).isNotEqualTo("string");
    }

    @Test
    @DisplayName("Deve testar método hashCode")
    void deveTestarMetodoHashCode() {
        // Given
        Usuario usuario1 = new Usuario();
        usuario1.setId(1L);
        usuario1.setEmail("joao@exemplo.com");

        Usuario usuario2 = new Usuario();
        usuario2.setId(1L);
        usuario2.setEmail("joao@exemplo.com");

        // Then
        assertThat(usuario1.hashCode()).isEqualTo(usuario2.hashCode());
    }

    @Test
    @DisplayName("Deve testar método toString")
    void deveTestarMetodoToString() {
        // Given
        Usuario usuario = new Usuario();
        usuario.setId(1L);
        usuario.setNome("João Silva");
        usuario.setEmail("joao@exemplo.com");

        // When
        String toString = usuario.toString();

        // Then
        assertThat(toString).contains("João Silva");
        assertThat(toString).contains("joao@exemplo.com");
    }

    @Test
    @DisplayName("Deve testar perfil ADMIN")
    void deveTestarPerfilAdmin() {
        // Given
        Usuario admin = new Usuario();
        admin.setNome("Admin User");
        admin.setEmail("admin@exemplo.com");
        admin.setSenha("admin123");
        admin.setPerfil(Usuario.PerfilUsuario.ADMIN);

        // Then
        assertThat(admin.getPerfil()).isEqualTo(Usuario.PerfilUsuario.ADMIN);
    }
}