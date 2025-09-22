package com.caracore.hub_town.servlet;

import com.caracore.hub_town.dao.UsuarioDAO;
import com.caracore.hub_town.model.Usuario;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;

import java.io.IOException;
import java.util.List;

@WebServlet("/dashboard")
public class DashboardServlet extends HttpServlet {
    private UsuarioDAO usuarioDAO;

    @Override
    public void init() throws ServletException {
        usuarioDAO = new UsuarioDAO();
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
            request.setAttribute("totalUsuarios", totalUsuarios);
            request.setAttribute("usuariosRecentes", usuariosRecentes);
            request.setAttribute("usuarioLogado", usuarioLogado);
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
