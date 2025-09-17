package com.exemplo.servlet;

import com.exemplo.dao.UsuarioDAO;
import com.exemplo.model.Usuario;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;

import java.io.IOException;
import java.util.List;

/**
 * Servlet para dashboard principal
 */
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
        
        // Verificar se está logado
        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("usuario") == null) {
            // Salvar URL para redirecionamento após login
            session = request.getSession(true);
            session.setAttribute("redirectAfterLogin", request.getRequestURL().toString());
            response.sendRedirect(request.getContextPath() + "/login");
            return;
        }
        
        try {
            // Obter dados do usuário logado
            Usuario usuarioLogado = (Usuario) session.getAttribute("usuario");
            
            // Carregar estatísticas para o dashboard
            Long totalUsuarios = usuarioDAO.contarUsuarios();
            List<Usuario> usuariosRecentes = usuarioDAO.listarAtivos();
            
            // Limitar a 5 usuários mais recentes
            if (usuariosRecentes.size() > 5) {
                usuariosRecentes = usuariosRecentes.subList(0, 5);
            }
            
            // Definir atributos para a JSP
            request.setAttribute("totalUsuarios", totalUsuarios);
            request.setAttribute("usuariosRecentes", usuariosRecentes);
            request.setAttribute("usuarioLogado", usuarioLogado);
            
            // Forward para a página do dashboard
            request.getRequestDispatcher("/dashboard.jsp").forward(request, response);
            
        } catch (Exception e) {
            System.err.println("Erro no dashboard: " + e.getMessage());
            e.printStackTrace();
            
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