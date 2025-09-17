package com.exemplo.servlet;

import jakarta.servlet.*;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;

/**
 * Filtro para proteger páginas que requerem autenticação
 */
@WebFilter(urlPatterns = {"/dashboard", "/dashboard/*"})
public class AuthFilter implements Filter {
    
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        
        HttpSession session = httpRequest.getSession(false);
        boolean isLoggedIn = (session != null && session.getAttribute("usuario") != null);
        
        if (!isLoggedIn) {
            // Salvar URL de destino para redirecionar após login
            String requestURL = httpRequest.getRequestURL().toString();
            String queryString = httpRequest.getQueryString();
            if (queryString != null) {
                requestURL += "?" + queryString;
            }
            
            HttpSession newSession = httpRequest.getSession(true);
            newSession.setAttribute("redirectAfterLogin", requestURL);
            
            // Redirecionar para login
            httpResponse.sendRedirect(httpRequest.getContextPath() + "/login");
        } else {
            // Usuário autenticado, continuar
            chain.doFilter(request, response);
        }
    }
}