package com.exemplo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

/**
 * Testes unitários para a classe App
 */
public class AppTest {
    /**
     * Teste simples para verificar se a classe App pode ser instanciada
     */
    @Test
    public void deveCriarInstanciaApp() {
        App app = new App();
        assertNotNull(app);
    }

    /**
     * Teste para o método de saudação
     */
    @Test
    public void deveRetornarSaudacaoCorreta() {
        App app = new App();
        String resultado = app.saudar("João");
        assertEquals("Olá, João! Bem-vindo ao meu projeto Java.", resultado);
    }
}