package com.caracore.hub_town.servlet;

import com.caracore.hub_town.model.Pedido;
import com.caracore.hub_town.model.Usuario;
import com.caracore.hub_town.model.Volume;
import com.caracore.hub_town.service.PedidoService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.lang.reflect.Field;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RecebimentoEntradaServletTest {

    private RecebimentoEntradaServlet servlet;

    private AutoCloseable mocks;

    @Mock
    private PedidoService pedidoService;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private RequestDispatcher dispatcher;

    @BeforeEach
    void setUp() throws Exception {
        mocks = MockitoAnnotations.openMocks(this);
        servlet = new RecebimentoEntradaServlet();
        Field field = RecebimentoEntradaServlet.class.getDeclaredField("pedidoService");
        field.setAccessible(true);
        field.set(servlet, pedidoService);
    }

    @AfterEach
    void tearDown() throws Exception {
        mocks.close();
    }

    @Test
    void deveManterNaTelaQuandoDadosDoDestinatarioIncompletos() throws Exception {
        HttpSession session = mock(HttpSession.class);
        when(request.getSession(false)).thenReturn(session);
        when(session.getAttribute("usuario")).thenReturn(new Usuario());

        when(request.getParameter("destinatarioNome")).thenReturn("");
        when(request.getParameter("destinatarioDocumento")).thenReturn(null);
        when(request.getParameter("destinatarioTelefone")).thenReturn(" ");
        when(request.getRequestDispatcher("/recebimento/entrada.jsp")).thenReturn(dispatcher);

        servlet.doPost(request, response);

        verify(request).setAttribute(eq("erro"), contains("Preencha"));
        verify(dispatcher).forward(request, response);
        verifyNoInteractions(pedidoService);
    }

    @Test
    void deveRegistrarPedidoQuandoDadosValidos() throws Exception {
        HttpSession session = mock(HttpSession.class);
        Usuario usuario = new Usuario();
        usuario.setNome("Operador Teste");

        when(request.getSession(false)).thenReturn(session);
        when(session.getAttribute("usuario")).thenReturn(usuario);
        
        when(request.getParameter("destinatarioNome")).thenReturn("Cliente");
        when(request.getParameter("destinatarioDocumento")).thenReturn("12345678900");
        when(request.getParameter("destinatarioTelefone")).thenReturn("11999999999");
        when(request.getParameter("codigoPedido")).thenReturn("PED-001");
        when(request.getContextPath()).thenReturn("/app");
        when(request.getParameterValues("volumeEtiqueta")).thenReturn(new String[]{""});
        when(request.getParameterValues("volumePeso")).thenReturn(new String[]{"2.5"});
        when(request.getParameterValues("volumeDimensao")).thenReturn(new String[]{"10x10x10"});

        Pedido salvo = new Pedido();
        salvo.setId(42L);
        when(pedidoService.registrarPedidoManual(any(Pedido.class), anyList(), eq("Operador Teste"))).thenReturn(salvo);

        servlet.doPost(request, response);

        ArgumentCaptor<List<Volume>> volumesCaptor = ArgumentCaptor.forClass(List.class);
        verify(pedidoService).registrarPedidoManual(any(Pedido.class), volumesCaptor.capture(), eq("Operador Teste"));
        List<Volume> volumes = volumesCaptor.getValue();
        assertThat(volumes).hasSize(1);
        assertThat(volumes.get(0).getPeso()).isEqualByComparingTo("2.5");

        verify(response).sendRedirect("/app/pedidos/detalhe?id=42");
    }
}


