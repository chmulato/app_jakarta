package com.caracore.hub_town.servlet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.caracore.hub_town.model.Pedido;
import com.caracore.hub_town.model.PedidoStatus;
import com.caracore.hub_town.model.Usuario;
import com.caracore.hub_town.service.PedidoService;
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
class PedidoDetalheServletTest {

    private PedidoDetalheServlet servlet;

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
        servlet = new PedidoDetalheServlet();
        Field field = PedidoDetalheServlet.class.getDeclaredField("pedidoService");
        field.setAccessible(true);
        field.set(servlet, pedidoService);
    }

    @Test
    void doGet_semIdRedirecionaLista() throws Exception {
        when(request.getParameter("id")).thenReturn(null);
        when(request.getContextPath()).thenReturn("/app");

        servlet.doGet(request, response);

        verify(response).sendRedirect("/app/pedidos/lista");
    }

    @Test
    void doGet_quandoPedidoNaoEncontradoDefineErro() throws Exception {
        when(request.getParameter("id")).thenReturn("4");
        when(pedidoService.buscarPorId(4L)).thenReturn(Optional.empty());
        when(request.getRequestDispatcher("/pedidos/detalhe.jsp")).thenReturn(dispatcher);

        servlet.doGet(request, response);

        ArgumentCaptor<String> erroCaptor = ArgumentCaptor.forClass(String.class);
        verify(request).setAttribute(eq("erro"), erroCaptor.capture());
        assertThat(erroCaptor.getValue()).containsIgnoringCase("pedido");
        verify(dispatcher).forward(request, response);
    }

    @Test
    void doGet_quandoPedidoEncontradoDefineFlags() throws Exception {
        Pedido pedido = new Pedido();
        pedido.setStatus(PedidoStatus.RECEBIDO);
        when(request.getParameter("id")).thenReturn("6");
        when(pedidoService.buscarPorId(6L)).thenReturn(Optional.of(pedido));
        when(request.getRequestDispatcher("/pedidos/detalhe.jsp")).thenReturn(dispatcher);

        servlet.doGet(request, response);

        verify(request).setAttribute("pedido", pedido);
        verify(request).setAttribute("podeMarcarPronto", true);
        verify(request).setAttribute("podeRetirar", false);
        verify(dispatcher).forward(request, response);
    }

    @Test
    void doPost_semUsuarioRedirecionaParaLogin() throws Exception {
        when(request.getSession(false)).thenReturn(null);
        when(request.getContextPath()).thenReturn("/app");

        servlet.doPost(request, response);

        verify(response).sendRedirect("/app/login");
        verifyNoInteractions(pedidoService);
    }

    @Test
    void doPost_quandoAcaoProntoAtualizaPedido() throws Exception {
        Usuario usuario = new Usuario();
        usuario.setNome("Carlos");
        when(request.getSession(false)).thenReturn(session);
        when(session.getAttribute("usuario")).thenReturn(usuario);
        when(request.getParameter("id")).thenReturn("9");
        when(request.getParameter("acao")).thenReturn("pronto");
        when(request.getContextPath()).thenReturn("/app");

        servlet.doPost(request, response);

        verify(pedidoService).marcarComoPronto(9L, "Carlos");
        verify(response).sendRedirect("/app/pedidos/detalhe?id=9&sucesso=1");
    }

    @Test
    void doPost_quandoAcaoRetirarAtualizaPedido() throws Exception {
        Usuario usuario = new Usuario();
        usuario.setNome("Ana");
        when(request.getSession(false)).thenReturn(session);
        when(session.getAttribute("usuario")).thenReturn(usuario);
        when(request.getParameter("id")).thenReturn("11");
        when(request.getParameter("acao")).thenReturn("retirar");
        when(request.getContextPath()).thenReturn("/app");

        servlet.doPost(request, response);

        verify(pedidoService).registrarRetirada(11L, "Ana");
        verify(response).sendRedirect("/app/pedidos/detalhe?id=11&sucesso=1");
    }

    @Test
    void doPost_quandoServicoLancaExcecaoEncaminhaErro() throws Exception {
        Usuario usuario = new Usuario();
        usuario.setNome("Marcos");
        when(request.getSession(false)).thenReturn(session);
        when(session.getAttribute("usuario")).thenReturn(usuario);
        when(request.getParameter("id")).thenReturn("13");
        when(request.getParameter("acao")).thenReturn("pronto");
        when(pedidoService.buscarPorId(13L)).thenReturn(Optional.of(new Pedido()));
        when(request.getRequestDispatcher("/pedidos/detalhe.jsp")).thenReturn(dispatcher);
        doThrow(new RuntimeException("Falhou"))
            .when(pedidoService).marcarComoPronto(13L, "Marcos");

        servlet.doPost(request, response);

        verify(request).setAttribute("erro", "Falhou");
        verify(dispatcher).forward(request, response);
    }
}
