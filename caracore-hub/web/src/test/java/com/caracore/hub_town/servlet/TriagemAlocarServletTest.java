package com.caracore.hub_town.servlet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.caracore.hub_town.model.Pedido;
import com.caracore.hub_town.model.Posicao;
import com.caracore.hub_town.model.Usuario;
import com.caracore.hub_town.model.Volume;
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
class TriagemAlocarServletTest {

    private TriagemAlocarServlet servlet;

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
        servlet = new TriagemAlocarServlet();
        Field field = TriagemAlocarServlet.class.getDeclaredField("pedidoService");
        field.setAccessible(true);
        field.set(servlet, pedidoService);
    }

    @Test
    void doGet_semPedidoIdRedirecionaParaLista() throws Exception {
        when(request.getParameter("pedidoId")).thenReturn(null);
        when(request.getContextPath()).thenReturn("/app");

        servlet.doGet(request, response);

        verify(response).sendRedirect("/app/pedidos/lista");
        verifyNoInteractions(pedidoService);
    }

    @Test
    void doGet_quandoPedidoNaoEncontradoMostraErro() throws Exception {
        when(request.getParameter("pedidoId")).thenReturn("5");
        when(pedidoService.buscarPorId(5L)).thenReturn(Optional.empty());
        when(request.getRequestDispatcher("/pedidos/lista.jsp")).thenReturn(dispatcher);

        servlet.doGet(request, response);

        ArgumentCaptor<String> erroCaptor = ArgumentCaptor.forClass(String.class);
        verify(request).setAttribute(eq("erro"), erroCaptor.capture());
        assertThat(erroCaptor.getValue()).containsIgnoringCase("pedido");
        verify(dispatcher).forward(request, response);
    }

    @Test
    void doGet_quandoPedidoEncontradoEncaminhaParaPaginaTriagem() throws Exception {
        Pedido pedido = new Pedido();
        Volume volume = new Volume();
        volume.setEtiqueta("VOL-1");
        pedido.adicionarVolume(volume);
        when(request.getParameter("pedidoId")).thenReturn("8");
        when(pedidoService.buscarPorId(8L)).thenReturn(Optional.of(pedido));
        Posicao posicaoLivre = new Posicao();
        when(pedidoService.listarPosicoesLivres()).thenReturn(List.of(posicaoLivre));
        Posicao sugerida = new Posicao();
        when(pedidoService.sugerirPosicao()).thenReturn(Optional.of(sugerida));
        when(request.getRequestDispatcher("/triagem/alocar.jsp")).thenReturn(dispatcher);

        servlet.doGet(request, response);

        verify(request).setAttribute("pedido", pedido);
        verify(request).setAttribute(eq("volumes"), eq(pedido.getVolumes()));
        verify(request).setAttribute("posicoes", List.of(posicaoLivre));
        verify(request).setAttribute("posicaoSugerida", sugerida.getCodigo());
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
    void doPost_atualizaPosicaoERedireciona() throws Exception {
        Usuario usuario = new Usuario();
        usuario.setNome("Maria");
        when(request.getSession(false)).thenReturn(session);
        when(session.getAttribute("usuario")).thenReturn(usuario);
        when(request.getParameter("pedidoId")).thenReturn("9");
        when(request.getParameter("volumeId")).thenReturn("11");
        when(request.getParameter("posicaoId")).thenReturn("3");
        when(request.getContextPath()).thenReturn("/app");

        servlet.doPost(request, response);

        verify(pedidoService).atualizarPosicao(11L, 3L, "Maria");
        verify(response).sendRedirect("/app/triagem/alocar?pedidoId=9&sucesso=1");
    }

    @Test
    void doPost_quandoErroNoServicoMostraErroENovoCarregamento() throws Exception {
        Usuario usuario = new Usuario();
        usuario.setNome("Carlos");
        when(request.getSession(false)).thenReturn(session);
        when(session.getAttribute("usuario")).thenReturn(usuario);
        when(request.getParameter("pedidoId")).thenReturn("10");
        when(request.getParameter("volumeId")).thenReturn("40");
        when(request.getParameter("posicaoId")).thenReturn("7");
        when(pedidoService.buscarPorId(10L)).thenReturn(Optional.of(new Pedido()));
        when(pedidoService.listarPosicoesLivres()).thenReturn(List.of(new Posicao()));
        when(pedidoService.sugerirPosicao()).thenReturn(Optional.empty());
        when(request.getRequestDispatcher("/triagem/alocar.jsp")).thenReturn(dispatcher);
        doThrow(new RuntimeException("Falha"))
            .when(pedidoService).atualizarPosicao(40L, 7L, "Carlos");

        servlet.doPost(request, response);

        verify(request).setAttribute("erro", "Falha");
        verify(dispatcher).forward(request, response);
    }
}
