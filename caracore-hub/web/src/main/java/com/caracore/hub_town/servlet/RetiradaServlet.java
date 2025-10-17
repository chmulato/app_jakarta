package com.caracore.hub_town.servlet;

import com.caracore.hub_town.model.Pedido;
import com.caracore.hub_town.model.Usuario;
import com.caracore.hub_town.service.PedidoService;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

@WebServlet("/retirada/retirar")
public class RetiradaServlet extends HttpServlet {
    private final PedidoService pedidoService = new PedidoService();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        request.getRequestDispatcher("/retirada/retirar.jsp").forward(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        HttpSession session = request.getSession(false);
        Usuario usuario = session != null ? (Usuario) session.getAttribute("usuario") : null;
        if (usuario == null) {
            response.sendRedirect(request.getContextPath() + "/login");
            return;
        }
        String acao = request.getParameter("acao");
        if ("buscar".equalsIgnoreCase(acao)) {
            String codigo = request.getParameter("codigo");
            String telefone = request.getParameter("telefone");
            if (codigo != null && !codigo.isBlank()) {
                Optional<Pedido> pedidoOpt = pedidoService.buscarPorCodigo(codigo.trim());
                pedidoOpt.ifPresent(p -> request.setAttribute("pedidoSelecionado", p));
                if (pedidoOpt.isEmpty()) {
                    request.setAttribute("erro", "Pedido não encontrado para o código informado");
                }
            } else if (telefone != null && !telefone.isBlank()) {
                List<Pedido> pedidos = pedidoService.buscarPorTelefone(telefone.trim());
                if (pedidos.isEmpty()) {
                    request.setAttribute("erro", "Nenhum pedido encontrado para o telefone informado");
                } else {
                    request.setAttribute("pedidosEncontrados", pedidos);
                }
            } else {
                request.setAttribute("erro", "Informe código ou telefone");
            }
            request.getRequestDispatcher("/retirada/retirar.jsp").forward(request, response);
            return;
        }
        if ("confirmar".equalsIgnoreCase(acao)) {
            Long pedidoId = Long.valueOf(request.getParameter("pedidoId"));
            try {
                pedidoService.registrarRetirada(pedidoId, usuario.getNome());
                response.sendRedirect(request.getContextPath() + "/retirada/retirar?sucesso=1");
            } catch (Exception e) {
                request.setAttribute("erro", e.getMessage());
                request.getRequestDispatcher("/retirada/retirar.jsp").forward(request, response);
            }
        } else {
            response.sendRedirect(request.getContextPath() + "/retirada/retirar");
        }
    }
}
