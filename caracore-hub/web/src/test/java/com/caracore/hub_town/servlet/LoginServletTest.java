package com.caracore.hub_town.servlet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.caracore.hub_town.dao.UsuarioDAO;
import com.caracore.hub_town.model.Usuario;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LoginServletTest {

    @Mock
    private UsuarioDAO usuarioDAO;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private HttpSession session;

    @Mock
    private RequestDispatcher dispatcher;

    private LoginServlet servlet;

    @BeforeEach
    void setUp() throws Exception {
        servlet = new LoginServlet();
        Field field = LoginServlet.class.getDeclaredField("usuarioDAO");
        field.setAccessible(true);
        field.set(servlet, usuarioDAO);
    }

    @Test
    void doGet_quandoUsuarioJaLogadoRedirecionaDashboard() throws Exception {
        when(request.getSession(false)).thenReturn(session);
        when(session.getAttribute("usuario")).thenReturn(new Usuario());
        when(request.getContextPath()).thenReturn("/app");

        servlet.doGet(request, response);

        verify(response).sendRedirect("/app/dashboard");
        verify(request, never()).getRequestDispatcher(anyString());
    }

    @Test
    void doGet_quandoNaoLogadoEncaminhaParaLogin() throws Exception {
        when(request.getSession(false)).thenReturn(null);
        when(request.getRequestDispatcher("/login.jsp")).thenReturn(dispatcher);

        servlet.doGet(request, response);

        verify(dispatcher).forward(request, response);
        verify(response, never()).sendRedirect(anyString());
    }

    @Test
    void doPost_quandoEmailVazioRetornaErro() throws Exception {
        when(request.getParameter("email")).thenReturn("   ");
        when(request.getRequestDispatcher("/login.jsp")).thenReturn(dispatcher);

        servlet.doPost(request, response);

        ArgumentCaptor<String> erroCaptor = ArgumentCaptor.forClass(String.class);
        verify(request).setAttribute(eq("erro"), erroCaptor.capture());
        assertThat(erroCaptor.getValue()).containsIgnoringCase("email");
        verify(dispatcher).forward(request, response);
        verifyNoInteractions(usuarioDAO);
    }

    @Test
    void doPost_quandoSenhaVaziaRetornaErro() throws Exception {
        when(request.getParameter("email")).thenReturn("ana@ex.com");
        when(request.getParameter("senha")).thenReturn(" ");
        when(request.getRequestDispatcher("/login.jsp")).thenReturn(dispatcher);

        servlet.doPost(request, response);

        ArgumentCaptor<String> erroCaptor = ArgumentCaptor.forClass(String.class);
        verify(request).setAttribute(eq("erro"), erroCaptor.capture());
        assertThat(erroCaptor.getValue()).containsIgnoringCase("senha");
        verify(dispatcher).forward(request, response);
        verify(usuarioDAO, never()).autenticar(anyString(), anyString());
    }

    @Test
    void doPost_quandoCredenciaisValidasCriaSessaoERedireciona() throws Exception {
        Usuario usuario = new Usuario();
        when(request.getParameter("email")).thenReturn(" user@ex.com ");
        when(request.getParameter("senha")).thenReturn("segredo");
        when(usuarioDAO.autenticar("user@ex.com", "segredo")).thenReturn(Optional.of(usuario));
        when(request.getSession(true)).thenReturn(session);
        when(request.getContextPath()).thenReturn("/app");

        servlet.doPost(request, response);

        verify(session).setAttribute("usuario", usuario);
        verify(response).sendRedirect("/app/dashboard");
        verify(request, never()).getRequestDispatcher("/login.jsp");
    }

    @Test
    void doPost_quandoAutenticacaoFalhaMantemEmailENotificaErro() throws Exception {
        when(request.getParameter("email")).thenReturn("user@ex.com");
        when(request.getParameter("senha")).thenReturn("errada");
        when(usuarioDAO.autenticar("user@ex.com", "errada")).thenReturn(Optional.empty());
        when(request.getRequestDispatcher("/login.jsp")).thenReturn(dispatcher);

        servlet.doPost(request, response);

        ArgumentCaptor<String> erroCaptor = ArgumentCaptor.forClass(String.class);
        verify(request).setAttribute(eq("erro"), erroCaptor.capture());
        assertThat(erroCaptor.getValue()).containsIgnoringCase("inv");
        verify(request).setAttribute("email", "user@ex.com");
        verify(dispatcher).forward(request, response);
    }
}
