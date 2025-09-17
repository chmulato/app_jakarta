package com.exemplo.servlet;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Testes do LogoutServlet")
class LogoutServletTest {

    @Mock
    private HttpServletRequest request;
    
    @Mock
    private HttpServletResponse response;
    
    @Mock
    private HttpSession session;
    
    private LogoutServlet logoutServlet;

    @BeforeEach
    void setUp() {
        logoutServlet = new LogoutServlet();
    }

    @Test
    @DisplayName("Deve fazer logout e invalidar sessão")
    void deveFazerLogoutEInvalidarSessao() throws ServletException, IOException {
        // Given
        when(request.getSession(false)).thenReturn(session);
        when(request.getContextPath()).thenReturn("");
        
        // When
        logoutServlet.doGet(request, response);
        
        // Then
        verify(session).invalidate();
        verify(response).sendRedirect("/");
    }

    @Test
    @DisplayName("Deve redirecionar mesmo sem sessão ativa")
    void deveRedirecionarMesmoSemSessaoAtiva() throws ServletException, IOException {
        // Given
        when(request.getSession(false)).thenReturn(null);
        when(request.getContextPath()).thenReturn("");
        
        // When
        logoutServlet.doGet(request, response);
        
        // Then
        verify(response).sendRedirect("/");
        // Não deve tentar invalidar sessão nula
        verify(session, never()).invalidate();
    }

    @Test
    @DisplayName("Deve funcionar tanto para GET quanto POST")
    void deveFuncionarTantoParaGetQuantoPost() throws ServletException, IOException {
        // Given
        when(request.getSession(false)).thenReturn(session);
        when(request.getContextPath()).thenReturn("");
        
        // When - testando POST
        logoutServlet.doPost(request, response);
        
        // Then
        verify(session).invalidate();
        verify(response).sendRedirect("/");
    }

    @Test
    @DisplayName("Deve usar context path correto no redirecionamento")
    void deveUsarContextPathCorretoNoRedirecionamento() throws ServletException, IOException {
        // Given
        String contextPath = "/myapp";
        when(request.getSession(false)).thenReturn(session);
        when(request.getContextPath()).thenReturn(contextPath);
        
        // When
        logoutServlet.doGet(request, response);
        
        // Then
        verify(response).sendRedirect(contextPath + "/");
    }
}