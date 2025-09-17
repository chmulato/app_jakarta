package com.exemplo.dao;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.assertj.core.api.Assertions.*;

@DisplayName("Testes básicos do UsuarioDAO")
class UsuarioDAOTest {

    @Test
    @DisplayName("Deve criar instância do UsuarioDAO")
    void deveCriarInstanciaDoUsuarioDAO() {
        // When
        UsuarioDAO usuarioDAO = new UsuarioDAO();
        
        // Then
        assertThat(usuarioDAO).isNotNull();
    }
}