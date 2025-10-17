package com.caracore.hub_town.servlet;

import com.caracore.hub_town.model.CanalPedido;
import com.caracore.hub_town.model.Pedido;
import com.caracore.hub_town.model.PedidoStatus;
import com.caracore.hub_town.service.PedidoService;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

@WebServlet("/pedidos/lista")
public class PedidosListaServlet extends HttpServlet {
    private final PedidoService pedidoService = new PedidoService();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        PedidoStatus status = parseStatus(request.getParameter("status"));
        CanalPedido canal = parseCanal(request.getParameter("canal"));
        LocalDate dataInicio = parseData(request.getParameter("dataInicio"));
        LocalDate dataFim = parseData(request.getParameter("dataFim"));
        String destinatario = request.getParameter("destinatario");

        List<Pedido> pedidos = pedidoService.consultar(status, dataInicio, dataFim, destinatario, canal);
        request.setAttribute("pedidos", pedidos);
        request.setAttribute("statusSelecionado", status);
        request.setAttribute("canalSelecionado", canal);
        request.setAttribute("dataInicio", request.getParameter("dataInicio"));
        request.setAttribute("dataFim", request.getParameter("dataFim"));
        request.setAttribute("destinatario", destinatario);
        request.setAttribute("statusList", Arrays.asList(PedidoStatus.values()));
        request.setAttribute("canais", Arrays.asList(CanalPedido.values()));
        request.getRequestDispatcher("/pedidos/lista.jsp").forward(request, response);
    }

    private PedidoStatus parseStatus(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return PedidoStatus.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private CanalPedido parseCanal(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return CanalPedido.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private LocalDate parseData(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return LocalDate.parse(value);
        } catch (Exception e) {
            return null;
        }
    }
}
