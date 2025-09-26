package com.caracore.hub_town.servlet;

import com.caracore.hub_town.model.CanalPedido;
import com.caracore.hub_town.model.Pedido;
import com.caracore.hub_town.model.PedidoStatus;
import com.caracore.hub_town.service.PedidoService;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.lang.reflect.Field;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PedidosListaServletTest {

    private PedidosListaServlet servlet;

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
        servlet = new PedidosListaServlet();
        Field field = PedidosListaServlet.class.getDeclaredField("pedidoService");
        field.setAccessible(true);
        field.set(servlet, pedidoService);
    }

    @Test
    void doGet_quandoParametrosValidosConsultaPedidos() throws Exception {
        when(request.getParameter("status")).thenReturn("pronto");
        when(request.getParameter("canal")).thenReturn("manual");
        when(request.getParameter("dataInicio")).thenReturn("2024-01-01");
        when(request.getParameter("dataFim")).thenReturn("2024-01-10");
        when(request.getParameter("destinatario")).thenReturn("Ana");
        when(request.getRequestDispatcher("/pedidos/lista.jsp")).thenReturn(dispatcher);

        List<Pedido> pedidos = List.of(new Pedido());
        when(pedidoService.consultar(any(), any(), any(), any(), any())).thenReturn(pedidos);

        servlet.doGet(request, response);

        verify(pedidoService).consultar(
            eq(PedidoStatus.PRONTO),
            eq(LocalDate.parse("2024-01-01")),
            eq(LocalDate.parse("2024-01-10")),
            eq("Ana"),
            eq(CanalPedido.MANUAL)
        );
        verify(request).setAttribute("pedidos", pedidos);
        verify(request).setAttribute("statusSelecionado", PedidoStatus.PRONTO);
        verify(request).setAttribute("canalSelecionado", CanalPedido.MANUAL);
        verify(dispatcher).forward(request, response);
    }

    @Test
    void doGet_quandoParametrosInvalidosIgnoraFiltros() throws Exception {
        when(request.getParameter("status")).thenReturn("invalido");
        when(request.getParameter("canal")).thenReturn("???");
        when(request.getParameter("dataInicio")).thenReturn("data-ruim");
        when(request.getParameter("dataFim")).thenReturn("2024-02-01");
        when(request.getParameter("destinatario")).thenReturn("Jose");
        when(request.getRequestDispatcher("/pedidos/lista.jsp")).thenReturn(dispatcher);

        when(pedidoService.consultar(any(), any(), any(), any(), any())).thenReturn(List.of());

        servlet.doGet(request, response);

        ArgumentCaptor<PedidoStatus> statusCaptor = ArgumentCaptor.forClass(PedidoStatus.class);
        ArgumentCaptor<LocalDate> dataInicio = ArgumentCaptor.forClass(LocalDate.class);
        ArgumentCaptor<LocalDate> dataFim = ArgumentCaptor.forClass(LocalDate.class);
        ArgumentCaptor<String> destinatario = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<CanalPedido> canalCaptor = ArgumentCaptor.forClass(CanalPedido.class);

        verify(pedidoService).consultar(statusCaptor.capture(), dataInicio.capture(), dataFim.capture(), destinatario.capture(), canalCaptor.capture());
        assertThat(statusCaptor.getValue()).isNull();
        assertThat(dataInicio.getValue()).isNull();
        assertThat(dataFim.getValue()).isEqualTo(LocalDate.parse("2024-02-01"));
        assertThat(destinatario.getValue()).isEqualTo("Jose");
        assertThat(canalCaptor.getValue()).isNull();
        verify(dispatcher).forward(request, response);
    }
}
