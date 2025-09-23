package com.caracore.hub_town.servlet;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthFilterTest {

    private final AuthFilter filter = new AuthFilter();

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain chain;

    @Mock
    private HttpSession session;

    @Test
    void quandoUsuarioNaoAutenticado_redirecionaParaLogin() throws IOException, ServletException {
        when(request.getSession(false)).thenReturn(null);
        HttpSession newSession = mock(HttpSession.class);
        when(request.getSession(true)).thenReturn(newSession);
        when(request.getRequestURL()).thenReturn(new StringBuffer("http://localhost/app/dashboard"));
        when(request.getQueryString()).thenReturn("page=1");
        when(request.getContextPath()).thenReturn("/app");

        filter.doFilter(request, response, chain);

        verify(newSession).setAttribute("redirectAfterLogin", "http://localhost/app/dashboard?page=1");
        verify(response).sendRedirect("/app/login");
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void quandoUsuarioAutenticado_prossegueComFluxo() throws IOException, ServletException {
        when(request.getSession(false)).thenReturn(session);
        when(session.getAttribute("usuario")).thenReturn(new Object());

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verify(response, never()).sendRedirect(anyString());
    }
}
