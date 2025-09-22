package com.caracore.hub_town.servlet;

import com.caracore.hub_town.model.Pedido;
import com.caracore.hub_town.model.Posicao;
import com.caracore.hub_town.model.Usuario;
import com.caracore.hub_town.model.Volume;
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

@WebServlet("/triagem/alocar")
public class TriagemAlocarServlet extends HttpServlet {
    private final PedidoService pedidoService = new PedidoService();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        String idParam = request.getParameter("pedidoId");
        if (idParam == null) {
            response.sendRedirect(request.getContextPath() + "/pedidos/lista");
            return;
        }
        Long pedidoId = Long.valueOf(idParam);
        Optional<Pedido> pedidoOpt = pedidoService.buscarPorId(pedidoId);
        if (pedidoOpt.isEmpty()) {
            request.setAttribute("erro", "Pedido não encontrado");
            request.getRequestDispatcher("/pedidos/lista.jsp").forward(request, response);
            return;
        }
        Pedido pedido = pedidoOpt.get();
        List<Posicao> posicoes = pedidoService.listarPosicoesLivres();
        Optional<Posicao> sugerida = pedidoService.sugerirPosicao();
        request.setAttribute("pedido", pedido);
        request.setAttribute("volumes", pedido.getVolumes());
        request.setAttribute("posicoes", posicoes);
        sugerida.ifPresent(posicao -> request.setAttribute("posicaoSugerida", posicao.getCodigo()));
        request.getRequestDispatcher("/triagem/alocar.jsp").forward(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        HttpSession session = request.getSession(false);
        Usuario usuario = session != null ? (Usuario) session.getAttribute("usuario") : null;
        if (usuario == null) {
            response.sendRedirect(request.getContextPath() + "/login");
            return;
        }
        Long pedidoId = Long.valueOf(request.getParameter("pedidoId"));
        Long volumeId = Long.valueOf(request.getParameter("volumeId"));
        String posicaoIdParam = request.getParameter("posicaoId");
        Long posicaoId = (posicaoIdParam != null && !posicaoIdParam.isBlank()) ? Long.valueOf(posicaoIdParam) : null;
        try {
            pedidoService.atualizarPosicao(volumeId, posicaoId, usuario.getNome());
            response.sendRedirect(request.getContextPath() + "/triagem/alocar?pedidoId=" + pedidoId + "&sucesso=1");
        } catch (Exception e) {
            request.setAttribute("erro", e.getMessage());
            doGet(request, response);
        }
    }
}
