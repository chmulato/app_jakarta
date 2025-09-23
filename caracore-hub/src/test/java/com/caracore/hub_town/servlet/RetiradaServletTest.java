package com.caracore.hub_town.servlet;

import com.caracore.hub_town.model.Pedido;
import com.caracore.hub_town.model.Usuario;
import com.caracore.hub_town.service.PedidoService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.lang.reflect.Field;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RetiradaServletTest {

    private RetiradaServlet servlet;

    private AutoCloseable mocks;

    @Mock
    private PedidoService pedidoService;
    @Mock
    private HttpServletRequest request;
    @Mock
    private HttpServletResponse response;
    @Mock
    private HttpSession session;
    @Mock
    private RequestDispatcher dispatcher;

    @BeforeEach
    void setUp() throws Exception {
        mocks = MockitoAnnotations.openMocks(this);
        servlet = new RetiradaServlet();
        Field field = RetiradaServlet.class.getDeclaredField("pedidoService");
        field.setAccessible(true);
        field.set(servlet, pedidoService);
    }

    @AfterEach
    void tearDown() throws Exception {
        mocks.close();
    }

    @Test
    void deveRedirecionarParaLoginQuandoSemSessao() throws Exception {
        when(request.getSession(false)).thenReturn(null);
        when(request.getContextPath()).thenReturn("/app");

        servlet.doPost(request, response);

        verify(response).sendRedirect("/app/login");
        verifyNoInteractions(pedidoService);
    }

    @Test
    void deveBuscarPedidoPorCodigo() throws Exception {
        Usuario usuario = new Usuario();
        when(request.getSession(false)).thenReturn(session);
        when(session.getAttribute("usuario")).thenReturn(usuario);
        when(request.getParameter("acao")).thenReturn("buscar");
        when(request.getParameter("codigo")).thenReturn("PED-100");
        when(request.getRequestDispatcher("/retirada/retirar.jsp")).thenReturn(dispatcher);

        Pedido pedido = new Pedido();
        when(pedidoService.buscarPorCodigo("PED-100")).thenReturn(Optional.of(pedido));

        servlet.doPost(request, response);

        verify(request).setAttribute("pedidoSelecionado", pedido);
        verify(dispatcher).forward(request, response);
    }

    @Test
    void deveConfirmarRetirada() throws Exception {
        Usuario usuario = new Usuario();
        usuario.setNome("Operador");
        when(request.getSession(false)).thenReturn(session);
        when(session.getAttribute("usuario")).thenReturn(usuario);
        when(request.getParameter("acao")).thenReturn("confirmar");
        when(request.getParameter("pedidoId")).thenReturn("7");
        when(request.getContextPath()).thenReturn("/app");

        servlet.doPost(request, response);

        verify(pedidoService).registrarRetirada(7L, "Operador");
        verify(response).sendRedirect("/app/retirada/retirar?sucesso=1");
    }
}
