package com.exemplo.servlet;

import com.exemplo.dao.UsuarioDAO;
import com.exemplo.model.Usuario;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.Optional;

/**
 * Servlet para autenticação de usuários
 */
@WebServlet("/login")
public class LoginServlet extends HttpServlet {
    
    private static final Logger logger = LogManager.getLogger(LoginServlet.class);
    private UsuarioDAO usuarioDAO;
    
    @Override
    public void init() throws ServletException {
        logger.info("Inicializando LoginServlet");
        usuarioDAO = new UsuarioDAO();
        logger.debug("UsuarioDAO inicializado com sucesso");
        // JPA já foi inicializado pelo WebServer
    }
    
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        
        logger.debug("Recebida requisição GET para /login");
        
        // Se já está logado, redireciona para dashboard
        HttpSession session = request.getSession(false);
        if (session != null && session.getAttribute("usuario") != null) {
            logger.debug("Usuário já está logado, redirecionando para dashboard");
            response.sendRedirect(request.getContextPath() + "/dashboard");
            return;
        }
        
        // Mostrar página de login
        logger.debug("Exibindo página de login");
        request.getRequestDispatcher("/login.jsp").forward(request, response);
    }
    
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        
        logger.debug("Recebida requisição POST para /login");
        
        String email = request.getParameter("email");
        String senha = request.getParameter("senha");
        String lembrar = request.getParameter("lembrar");
        
        logger.debug("Tentativa de login para email: {}", email);
        
        // Validações básicas
        if (email == null || email.trim().isEmpty()) {
            logger.warn("Tentativa de login com email vazio");
            request.setAttribute("erro", "Email é obrigatório");
            request.getRequestDispatcher("/login.jsp").forward(request, response);
            return;
        }
        
        if (senha == null || senha.trim().isEmpty()) {
            logger.warn("Tentativa de login com senha vazia para email: {}", email);
            request.setAttribute("erro", "Senha é obrigatória");
            request.getRequestDispatcher("/login.jsp").forward(request, response);
            return;
        }
        
        try {
            // Tentar autenticar
            logger.debug("Tentando autenticar usuário: {}", email);
            Optional<Usuario> usuarioOpt = usuarioDAO.autenticar(email.trim(), senha);
            
            if (usuarioOpt.isPresent()) {
                Usuario usuario = usuarioOpt.get();
                logger.info("Usuário autenticado com sucesso: {} (ID: {})", usuario.getEmail(), usuario.getId());
                
                // Criar sessão
                HttpSession session = request.getSession(true);
                session.setAttribute("usuario", usuario);
                session.setAttribute("usuarioId", usuario.getId());
                session.setAttribute("usuarioNome", usuario.getNome());
                session.setAttribute("usuarioEmail", usuario.getEmail());
                session.setAttribute("usuarioPerfil", usuario.getPerfil());
                
                // Configurar timeout da sessão (30 minutos)
                session.setMaxInactiveInterval(30 * 60);
                logger.debug("Sessão criada com timeout de 30 minutos");
                
                // Cookie "lembrar-me" (opcional)
                if ("on".equals(lembrar)) {
                    logger.debug("Criando cookie 'lembrar-me' para: {}", email);
                    Cookie cookie = new Cookie("lembrar_email", email);
                    cookie.setMaxAge(7 * 24 * 60 * 60); // 7 dias
                    cookie.setPath(request.getContextPath());
                    response.addCookie(cookie);
                }
                
                // Redirecionar para dashboard
                String redirectUrl = (String) session.getAttribute("redirectAfterLogin");
                if (redirectUrl != null) {
                    logger.debug("Redirecionando para URL original após login: {}", redirectUrl);
                    session.removeAttribute("redirectAfterLogin");
                    response.sendRedirect(redirectUrl);
                } else {
                    logger.debug("Redirecionando para dashboard após login bem-sucedido");
                    response.sendRedirect(request.getContextPath() + "/dashboard");
                }
                
            } else {
                // Falha na autenticação
                logger.warn("Falha na autenticação para email: {}", email);
                request.setAttribute("erro", "Email ou senha inválidos");
                request.setAttribute("email", email); // Manter email preenchido
                request.getRequestDispatcher("/login.jsp").forward(request, response);
            }
            
        } catch (Exception e) {
            logger.error("Erro durante o processo de autenticação: {}", e.getMessage(), e);
            
            request.setAttribute("erro", "Erro interno do sistema. Tente novamente.");
            request.getRequestDispatcher("/login.jsp").forward(request, response);
        }
    }
}