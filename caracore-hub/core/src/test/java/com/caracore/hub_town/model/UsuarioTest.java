package com.caracore.hub_town.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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
        Usuario usuario = new Usuario();
        usuario.setNome("João Silva");
        usuario.setEmail("joao@exemplo.com");
        usuario.setSenha("senha123");
        usuario.setPerfil(Usuario.PerfilUsuario.OPERADOR);
        Set<ConstraintViolation<Usuario>> violations = validator.validate(usuario);
        assertThat(violations).isEmpty();
        assertThat(usuario.getNome()).isEqualTo("João Silva");
        assertThat(usuario.getEmail()).isEqualTo("joao@exemplo.com");
        assertThat(usuario.getSenha()).isEqualTo("senha123");
        assertThat(usuario.getPerfil()).isEqualTo(Usuario.PerfilUsuario.OPERADOR);
        assertThat(usuario.isAtivo()).isTrue();
    }
}
