package com.caracore.hub_town.servlet;

import com.caracore.hub_town.dao.UsuarioDAO;
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
import java.time.LocalDate;
import java.util.List;

@WebServlet("/dashboard")
public class DashboardServlet extends HttpServlet {
    private UsuarioDAO usuarioDAO;
    private PedidoService pedidoService;

    @Override
    public void init() throws ServletException {
        usuarioDAO = new UsuarioDAO();
        pedidoService = new PedidoService();
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("usuario") == null) {
            session = request.getSession(true);
            session.setAttribute("redirectAfterLogin", request.getRequestURL().toString());
            response.sendRedirect(request.getContextPath() + "/login");
            return;
        }
        try {
            Usuario usuarioLogado = (Usuario) session.getAttribute("usuario");
            Long totalUsuarios = usuarioDAO.contarUsuarios();
            List<Usuario> usuariosRecentes = usuarioDAO.listarAtivos();
            if (usuariosRecentes.size() > 5) {
                usuariosRecentes = usuariosRecentes.subList(0, 5);
            }
            LocalDate hoje = LocalDate.now();
            long pedidosRecebidos = pedidoService.contarEventosDoDia(PedidoStatus.RECEBIDO, hoje);
            long pedidosProntos = pedidoService.contarEventosDoDia(PedidoStatus.PRONTO, hoje);
            long pedidosRetirados = pedidoService.contarEventosDoDia(PedidoStatus.RETIRADO, hoje);
            List<Pedido> pedidosRecentes = pedidoService.consultar(null, hoje.minusDays(7), null, null, null);
            if (pedidosRecentes.size() > 5) {
                pedidosRecentes = pedidosRecentes.subList(0, 5);
            }
            request.setAttribute("totalUsuarios", totalUsuarios);
            request.setAttribute("usuariosRecentes", usuariosRecentes);
            request.setAttribute("usuarioLogado", usuarioLogado);
            request.setAttribute("recebidosHoje", pedidosRecebidos);
            request.setAttribute("prontos", pedidosProntos);
            request.setAttribute("retirados", pedidosRetirados);
            request.setAttribute("pedidosRecentes", pedidosRecentes);
            request.getRequestDispatcher("/dashboard.jsp").forward(request, response);
        } catch (Exception e) {
            request.setAttribute("erro", "Erro ao carregar dashboard");
            request.getRequestDispatcher("/erro.jsp").forward(request, response);
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        doGet(request, response);
    }
}
