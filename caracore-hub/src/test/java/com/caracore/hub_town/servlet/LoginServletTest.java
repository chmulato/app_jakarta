package com.caracore.hub_town.servlet;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("LoginServlet - Testes Completos")
class LoginServletTest {

    @Mock private HttpServletRequest request;
    @Mock private HttpServletResponse response;
    @Mock private HttpSession session;
    @Mock private RequestDispatcher dispatcher;

    private LoginServlet loginServlet;

    @BeforeEach
    void setUp() { loginServlet = new LoginServlet(); }

    @Test
    @DisplayName("Deve exibir página de login quando usuário não está logado")
    void deveExibirPaginaDeLoginQuandoUsuarioNaoEstaLogado() throws ServletException, IOException {
        when(request.getSession(false)).thenReturn(null);
        when(request.getRequestDispatcher("/login.jsp")).thenReturn(dispatcher);
        loginServlet.doGet(request, response);
        verify(request).getRequestDispatcher("/login.jsp");
        verify(dispatcher).forward(request, response);
        verify(response, never()).sendRedirect(anyString());
    }
}
