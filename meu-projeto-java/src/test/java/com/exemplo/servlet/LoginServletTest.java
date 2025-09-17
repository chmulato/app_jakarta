package com.exemplo.servlet;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.Nested;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("LoginServlet - Testes Completos")
class LoginServletTest {

    @Mock
    private HttpServletRequest request;
    
    @Mock
    private HttpServletResponse response;
    
    @Mock
    private HttpSession session;
    
    @Mock
    private RequestDispatcher dispatcher;
    
    private LoginServlet loginServlet;

    @BeforeEach
    void setUp() {
        loginServlet = new LoginServlet();
    }

    @Nested
    @DisplayName("Método doGet - Exibição da tela de login")
    class DoGetTests {

        @Test
        @DisplayName("Deve exibir página de login quando usuário não está logado")
        void deveExibirPaginaDeLoginQuandoUsuarioNaoEstaLogado() throws ServletException, IOException {
            // Given
            when(request.getSession(false)).thenReturn(null);
            when(request.getRequestDispatcher("/login.jsp")).thenReturn(dispatcher);
            
            // When
            loginServlet.doGet(request, response);
            
            // Then
            verify(request).getSession(false);
            verify(request).getRequestDispatcher("/login.jsp");
            verify(dispatcher).forward(request, response);
            verify(response, never()).sendRedirect(anyString());
        }

        @Test
        @DisplayName("Deve exibir login quando sessão existe mas usuário não está definido")
        void deveExibirLoginQuandoSessaoExisteMasUsuarioNaoEstaDefinido() throws ServletException, IOException {
            // Given
            when(request.getSession(false)).thenReturn(session);
            when(session.getAttribute("usuario")).thenReturn(null);
            when(request.getRequestDispatcher("/login.jsp")).thenReturn(dispatcher);
            
            // When
            loginServlet.doGet(request, response);
            
            // Then
            verify(request).getSession(false);
            verify(session).getAttribute("usuario");
            verify(request).getRequestDispatcher("/login.jsp");
            verify(dispatcher).forward(request, response);
        }
    }

    @Nested
    @DisplayName("Método doPost - Processamento de login")
    class DoPostTests {

        @Test
        @DisplayName("Deve mostrar erro quando email está vazio")
        void deveMostrarErroQuandoEmailEstaVazio() throws ServletException, IOException {
            // Given
            when(request.getParameter("email")).thenReturn("");
            when(request.getParameter("senha")).thenReturn("senha123");
            when(request.getRequestDispatcher("/login.jsp")).thenReturn(dispatcher);
            
            // When
            loginServlet.doPost(request, response);
            
            // Then
            verify(request).setAttribute("erro", "Email é obrigatório");
            verify(request).getRequestDispatcher("/login.jsp");
            verify(dispatcher).forward(request, response);
        }

        @Test
        @DisplayName("Deve mostrar erro quando email é null")
        void deveMostrarErroQuandoEmailENull() throws ServletException, IOException {
            // Given
            when(request.getParameter("email")).thenReturn(null);
            when(request.getParameter("senha")).thenReturn("senha123");
            when(request.getRequestDispatcher("/login.jsp")).thenReturn(dispatcher);
            
            // When
            loginServlet.doPost(request, response);
            
            // Then
            verify(request).setAttribute("erro", "Email é obrigatório");
            verify(request).getRequestDispatcher("/login.jsp");
            verify(dispatcher).forward(request, response);
        }

        @Test
        @DisplayName("Deve mostrar erro quando senha está vazia")
        void deveMostrarErroQuandoSenhaEstaVazia() throws ServletException, IOException {
            // Given
            when(request.getParameter("email")).thenReturn("admin@exemplo.com");
            when(request.getParameter("senha")).thenReturn("");
            when(request.getRequestDispatcher("/login.jsp")).thenReturn(dispatcher);
            
            // When
            loginServlet.doPost(request, response);
            
            // Then
            verify(request).setAttribute("erro", "Senha é obrigatória");
            verify(request).getRequestDispatcher("/login.jsp");
            verify(dispatcher).forward(request, response);
        }

        @Test
        @DisplayName("Deve mostrar erro quando senha é null")
        void deveMostrarErroQuandoSenhaENull() throws ServletException, IOException {
            // Given
            when(request.getParameter("email")).thenReturn("admin@exemplo.com");
            when(request.getParameter("senha")).thenReturn(null);
            when(request.getRequestDispatcher("/login.jsp")).thenReturn(dispatcher);
            
            // When
            loginServlet.doPost(request, response);
            
            // Then
            verify(request).setAttribute("erro", "Senha é obrigatória");
            verify(request).getRequestDispatcher("/login.jsp");
            verify(dispatcher).forward(request, response);
        }

        @Test
        @DisplayName("Deve tratar espaços em branco no email")
        void deveTratarEspacosEmBrancoNoEmail() throws ServletException, IOException {
            // Given - email com espaços é validado como vazio
            when(request.getParameter("email")).thenReturn("   ");
            when(request.getParameter("senha")).thenReturn("senha123");
            when(request.getRequestDispatcher("/login.jsp")).thenReturn(dispatcher);
            
            // When
            loginServlet.doPost(request, response);
            
            // Then
            verify(request).setAttribute("erro", "Email é obrigatório");
            verify(request).getRequestDispatcher("/login.jsp");
            verify(dispatcher).forward(request, response);
        }

        @Test
        @DisplayName("Deve tratar espaços em branco na senha")
        void deveTratarEspacosEmBrancoNaSenha() throws ServletException, IOException {
            // Given - senha com espaços é validada como vazia
            when(request.getParameter("email")).thenReturn("admin@exemplo.com");
            when(request.getParameter("senha")).thenReturn("   ");
            when(request.getRequestDispatcher("/login.jsp")).thenReturn(dispatcher);
            
            // When
            loginServlet.doPost(request, response);
            
            // Then
            verify(request).setAttribute("erro", "Senha é obrigatória");
            verify(request).getRequestDispatcher("/login.jsp");
            verify(dispatcher).forward(request, response);
        }
    }

    @Nested
    @DisplayName("Testes de Interface - Validação da JSP")
    class InterfaceTests {

        @Test
        @DisplayName("Deve usar a JSP correta para renderizar o login")
        void deveUsarJspCorretaParaRenderizarLogin() throws ServletException, IOException {
            // Given
            when(request.getSession(false)).thenReturn(null);
            when(request.getRequestDispatcher("/login.jsp")).thenReturn(dispatcher);
            
            // When
            loginServlet.doGet(request, response);
            
            // Then
            verify(request).getRequestDispatcher("/login.jsp");
            assertNotNull(dispatcher, "RequestDispatcher não deve ser nulo");
            verify(dispatcher).forward(request, response);
        }

        @Test
        @DisplayName("Deve configurar atributos necessários para a JSP")
        void deveConfigurarAtributosNecessariosParaJsp() throws ServletException, IOException {
            // Given
            when(request.getParameter("email")).thenReturn("");
            when(request.getParameter("senha")).thenReturn("senha123");
            when(request.getRequestDispatcher("/login.jsp")).thenReturn(dispatcher);
            
            // When
            loginServlet.doPost(request, response);
            
            // Then
            verify(request).setAttribute(eq("erro"), anyString());
            verify(request).getRequestDispatcher("/login.jsp");
            verify(dispatcher).forward(request, response);
        }
    }
}