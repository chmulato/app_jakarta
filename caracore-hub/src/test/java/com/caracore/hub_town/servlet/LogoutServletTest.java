package com.caracore.hub_town.servlet;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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
    @Mock private HttpServletRequest request;
    @Mock private HttpServletResponse response;
    @Mock private HttpSession session;

    private LogoutServlet logoutServlet;

    @BeforeEach
    void setUp() { logoutServlet = new LogoutServlet(); }

    @Test
    @DisplayName("Deve fazer logout e invalidar sessão")
    void deveFazerLogoutEInvalidarSessao() throws ServletException, IOException {
        when(request.getSession(false)).thenReturn(session);
        when(request.getContextPath()).thenReturn("");
        logoutServlet.doGet(request, response);
        verify(session).invalidate();
        verify(response).sendRedirect("/");
    }
}
