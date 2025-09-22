package com.caracore.hub_town.servlet;

import com.caracore.hub_town.dao.UsuarioDAO;
import com.caracore.hub_town.model.Usuario;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.Optional;

@WebServlet("/login")
public class LoginServlet extends HttpServlet {
    private static final Logger logger = LogManager.getLogger(LoginServlet.class);
    private UsuarioDAO usuarioDAO;
    @Override
    public void init() throws ServletException { usuarioDAO = new UsuarioDAO(); }
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        HttpSession session = request.getSession(false);
        if (session != null && session.getAttribute("usuario") != null) {
            response.sendRedirect(request.getContextPath() + "/dashboard");
            return;
        }
        request.getRequestDispatcher("/login.jsp").forward(request, response);
    }
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        String email = request.getParameter("email");
        String senha = request.getParameter("senha");
        if (email == null || email.trim().isEmpty()) {
            request.setAttribute("erro", "Email é obrigatório");
            request.getRequestDispatcher("/login.jsp").forward(request, response);
            return;
        }
        if (senha == null || senha.trim().isEmpty()) {
            request.setAttribute("erro", "Senha é obrigatória");
            request.getRequestDispatcher("/login.jsp").forward(request, response);
            return;
        }
        Optional<Usuario> usuarioOpt = usuarioDAO.autenticar(email.trim(), senha);
        if (usuarioOpt.isPresent()) {
            HttpSession session = request.getSession(true);
            session.setAttribute("usuario", usuarioOpt.get());
            response.sendRedirect(request.getContextPath() + "/dashboard");
        } else {
            request.setAttribute("erro", "Email ou senha inválidos");
            request.setAttribute("email", email);
            request.getRequestDispatcher("/login.jsp").forward(request, response);
        }
    }
}
