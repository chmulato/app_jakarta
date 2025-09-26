package com.caracore.hub_town.servlet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LogoutServletTest {

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private HttpSession session;

    private LogoutServlet servlet;

    @BeforeEach
    void setUp() {
        servlet = new LogoutServlet();
    }

    @Test
    void doPost_quandoHaSessaoEcookieRemoveDadosEInvalida() throws ServletException, IOException {
        Cookie lembrar = new Cookie("lembrar_email", "teste@ex.com");
        Cookie outro = new Cookie("outro", "valor");
        when(request.getSession(false)).thenReturn(session);
        when(request.getCookies()).thenReturn(new Cookie[] { lembrar, outro });
        when(request.getContextPath()).thenReturn("/app");

        servlet.doPost(request, response);

        verify(session).invalidate();
        ArgumentCaptor<Cookie> cookieCaptor = ArgumentCaptor.forClass(Cookie.class);
        verify(response).addCookie(cookieCaptor.capture());
        Cookie atualizado = cookieCaptor.getValue();
        assertThat(atualizado.getName()).isEqualTo("lembrar_email");
        assertThat(atualizado.getValue()).isEmpty();
        assertThat(atualizado.getMaxAge()).isZero();
        assertThat(atualizado.getPath()).isEqualTo("/app");
        verify(response).sendRedirect("/app/");
    }

    @Test
    void doPost_quandoNaoHaSessaoOuCookiesApenasRedireciona() throws ServletException, IOException {
        when(request.getSession(false)).thenReturn(null);
        when(request.getCookies()).thenReturn(null);
        when(request.getContextPath()).thenReturn("/app");

        servlet.doPost(request, response);

        verify(response).sendRedirect("/app/");
        verify(response, never()).addCookie(any());
    }

    @Test
    void doGet_deveDelegarParaDoPost() throws ServletException, IOException {
        when(request.getSession(false)).thenReturn(session);
        when(request.getCookies()).thenReturn(null);
        when(request.getContextPath()).thenReturn("");

        servlet.doGet(request, response);

        verify(session).invalidate();
        verify(response).sendRedirect("/");
    }
}

