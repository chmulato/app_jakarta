package com.caracore.hub_town.servlet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.caracore.hub_town.model.Pedido;
import com.caracore.hub_town.model.Usuario;
import com.caracore.hub_town.service.PedidoService;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RetiradaServletTest {

    private RetiradaServlet servlet;

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
        servlet = new RetiradaServlet();
        Field field = RetiradaServlet.class.getDeclaredField("pedidoService");
        field.setAccessible(true);
        field.set(servlet, pedidoService);
    }

    @Test
    void doGet_semLogicaDeveEncaminharParaPagina() throws ServletException, IOException {
        when(request.getRequestDispatcher("/retirada/retirar.jsp")).thenReturn(dispatcher);

        servlet.doGet(request, response);

        verify(dispatcher).forward(request, response);
    }

    @Test
    void doPost_semSessaoRedirecionaParaLogin() throws Exception {
        when(request.getSession(false)).thenReturn(null);
        when(request.getContextPath()).thenReturn("/app");

        servlet.doPost(request, response);

        verify(response).sendRedirect("/app/login");
        verifyNoInteractions(pedidoService);
    }

    @Test
    void doPost_buscarPorCodigoQuandoEncontradoDefinePedido() throws Exception {
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
    void doPost_buscarPorCodigoQuandoNaoEncontradoMostraErro() throws Exception {
        prepararContextoBuscarCodigo();
        when(pedidoService.buscarPorCodigo("PED-404")).thenReturn(Optional.empty());

        servlet.doPost(request, response);

        ArgumentCaptor<String> erroCaptor = ArgumentCaptor.forClass(String.class);
        verify(request).setAttribute(eq("erro"), erroCaptor.capture());
        assertThat(erroCaptor.getValue()).containsIgnoringCase("n");
        verify(dispatcher).forward(request, response);
    }

    @Test
    void doPost_buscarPorTelefoneQuandoRetornaPedidos() throws Exception {
        Usuario usuario = new Usuario();
        when(request.getSession(false)).thenReturn(session);
        when(session.getAttribute("usuario")).thenReturn(usuario);
        when(request.getParameter("acao")).thenReturn("buscar");
        when(request.getParameter("codigo")).thenReturn(" ");
        when(request.getParameter("telefone")).thenReturn("119999");
        when(request.getRequestDispatcher("/retirada/retirar.jsp")).thenReturn(dispatcher);
        List<Pedido> pedidos = List.of(new Pedido());
        when(pedidoService.buscarPorTelefone("119999")).thenReturn(pedidos);

        servlet.doPost(request, response);

        verify(request).setAttribute("pedidosEncontrados", pedidos);
        verify(dispatcher).forward(request, response);
    }

    @Test
    void doPost_buscarPorTelefoneQuandoNaoHaResultadosDefineErro() throws Exception {
        Usuario usuario = prepararContextoBuscarTelefone();
        when(pedidoService.buscarPorTelefone("119999")).thenReturn(List.of());

        servlet.doPost(request, response);

        ArgumentCaptor<String> erroCaptor = ArgumentCaptor.forClass(String.class);
        verify(request).setAttribute(eq("erro"), erroCaptor.capture());
        assertThat(erroCaptor.getValue()).containsIgnoringCase("nenhum");
        verify(dispatcher).forward(request, response);
    }

    @Test
    void doPost_buscarSemCodigoETelefoneSinalizaErro() throws Exception {
        Usuario usuario = new Usuario();
        when(request.getSession(false)).thenReturn(session);
        when(session.getAttribute("usuario")).thenReturn(usuario);
        when(request.getParameter("acao")).thenReturn("buscar");
        when(request.getRequestDispatcher("/retirada/retirar.jsp")).thenReturn(dispatcher);

        servlet.doPost(request, response);

        ArgumentCaptor<String> erroCaptor = ArgumentCaptor.forClass(String.class);
        verify(request).setAttribute(eq("erro"), erroCaptor.capture());
        assertThat(erroCaptor.getValue()).containsIgnoringCase("informe");
        verify(dispatcher).forward(request, response);
    }

    @Test
    void doPost_confirmarQuandoServicoFalhaEncaminhaComErro() throws Exception {
        Usuario usuario = new Usuario();
        usuario.setNome("Operador");
        when(request.getSession(false)).thenReturn(session);
        when(session.getAttribute("usuario")).thenReturn(usuario);
        when(request.getParameter("acao")).thenReturn("confirmar");
        when(request.getParameter("pedidoId")).thenReturn("7");
        when(request.getRequestDispatcher("/retirada/retirar.jsp")).thenReturn(dispatcher);
        doThrow(new RuntimeException("Falha"))
            .when(pedidoService).registrarRetirada(7L, "Operador");

        servlet.doPost(request, response);

        verify(request).setAttribute("erro", "Falha");
        verify(dispatcher).forward(request, response);
    }

    @Test
    void doPost_confirmarComSucessoRedireciona() throws Exception {
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

    @Test
    void doPost_acaoDesconhecidaRedirecionaParaPagina() throws Exception {
        Usuario usuario = new Usuario();
        when(request.getSession(false)).thenReturn(session);
        when(session.getAttribute("usuario")).thenReturn(usuario);
        when(request.getParameter("acao")).thenReturn("qualquer");
        when(request.getContextPath()).thenReturn("/app");

        servlet.doPost(request, response);

        verify(response).sendRedirect("/app/retirada/retirar");
        verify(pedidoService, never()).registrarRetirada(any(), any());
    }

    private void prepararContextoBuscarCodigo() {
        Usuario usuario = new Usuario();
        when(request.getSession(false)).thenReturn(session);
        when(session.getAttribute("usuario")).thenReturn(usuario);
        when(request.getParameter("acao")).thenReturn("buscar");
        when(request.getParameter("codigo")).thenReturn("PED-404");
        when(request.getRequestDispatcher("/retirada/retirar.jsp")).thenReturn(dispatcher);
    }

    private Usuario prepararContextoBuscarTelefone() {
        Usuario usuario = new Usuario();
        when(request.getSession(false)).thenReturn(session);
        when(session.getAttribute("usuario")).thenReturn(usuario);
        when(request.getParameter("acao")).thenReturn("buscar");
        when(request.getParameter("codigo")).thenReturn(" ");
        when(request.getParameter("telefone")).thenReturn("119999");
        when(request.getRequestDispatcher("/retirada/retirar.jsp")).thenReturn(dispatcher);
        return usuario;
    }
}



