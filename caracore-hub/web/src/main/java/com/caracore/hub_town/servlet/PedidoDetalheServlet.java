package com.caracore.hub_town.servlet;

import com.caracore.hub_town.model.Pedido;
import com.caracore.hub_town.model.PedidoStatus;
import com.caracore.hub_town.model.Usuario;
import com.caracore.hub_town.service.PedidoService;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.util.Optional;

@WebServlet("/pedidos/detalhe")
public class PedidoDetalheServlet extends HttpServlet {
    private final PedidoService pedidoService = new PedidoService();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        String idParam = request.getParameter("id");
        if (idParam == null) {
            response.sendRedirect(request.getContextPath() + "/pedidos/lista");
            return;
        }
        Long pedidoId = Long.valueOf(idParam);
        Optional<Pedido> pedidoOpt = pedidoService.buscarPorId(pedidoId);
        if (pedidoOpt.isEmpty()) {
            request.setAttribute("erro", "Pedido não encontrado");
        } else {
            Pedido pedido = pedidoOpt.get();
            request.setAttribute("pedido", pedido);
            request.setAttribute("podeMarcarPronto", pedido.getStatus() == PedidoStatus.RECEBIDO);
            request.setAttribute("podeRetirar", pedido.getStatus() == PedidoStatus.PRONTO);
        }
        request.getRequestDispatcher("/pedidos/detalhe.jsp").forward(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        HttpSession session = request.getSession(false);
        Usuario usuario = session != null ? (Usuario) session.getAttribute("usuario") : null;
        if (usuario == null) {
            response.sendRedirect(request.getContextPath() + "/login");
            return;
        }
        Long pedidoId = Long.valueOf(request.getParameter("id"));
        String acao = request.getParameter("acao");
        try {
            if ("pronto".equalsIgnoreCase(acao)) {
                pedidoService.marcarComoPronto(pedidoId, usuario.getNome());
            } else if ("retirar".equalsIgnoreCase(acao)) {
                pedidoService.registrarRetirada(pedidoId, usuario.getNome());
            }
            response.sendRedirect(request.getContextPath() + "/pedidos/detalhe?id=" + pedidoId + "&sucesso=1");
        } catch (Exception e) {
            request.setAttribute("erro", e.getMessage());
            doGet(request, response);
        }
    }
}
