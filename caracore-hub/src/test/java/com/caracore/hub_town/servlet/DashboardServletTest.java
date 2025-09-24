package com.caracore.hub_town.servlet;

import com.caracore.hub_town.dao.UsuarioDAO;
import com.caracore.hub_town.model.Pedido;
import com.caracore.hub_town.model.PedidoStatus;
import com.caracore.hub_town.model.Usuario;
import com.caracore.hub_town.service.PedidoService;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.lang.reflect.Field;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DashboardServletTest {

    private DashboardServlet servlet;

    @Mock
    private UsuarioDAO usuarioDAO;

    @Mock
    private PedidoService pedidoService;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private HttpSession session;

    @Mock
    private HttpSession createdSession;

    @Mock
    private RequestDispatcher dispatcher;

    @BeforeEach
    void setUp() throws Exception {
        servlet = new DashboardServlet();
        injectPrivateField("usuarioDAO", usuarioDAO);
        injectPrivateField("pedidoService", pedidoService);
    }

    @Test
    void doGet_quandoSemSessaoRedirecionaLogin() throws Exception {
        when(request.getSession(false)).thenReturn(null);
        when(request.getSession(true)).thenReturn(createdSession);
        when(request.getRequestURL()).thenReturn(new StringBuffer("http://localhost/app/dashboard"));
        when(request.getContextPath()).thenReturn("/app");

        servlet.doGet(request, response);

        verify(createdSession).setAttribute("redirectAfterLogin", "http://localhost/app/dashboard");
        verify(response).sendRedirect("/app/login");
        verifyNoInteractions(usuarioDAO, pedidoService);
    }

    @Test
    void doGet_quandoAutenticadoCarregaIndicadoresELista() throws Exception {
        Usuario usuario = new Usuario();
        usuario.setNome("Ana");
        when(request.getSession(false)).thenReturn(session);
        when(session.getAttribute("usuario")).thenReturn(usuario);
        when(request.getRequestDispatcher("/dashboard.jsp")).thenReturn(dispatcher);

        List<Usuario> usuariosAtivos = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            Usuario u = new Usuario();
            u.setNome("Usu" + i);
            usuariosAtivos.add(u);
        }
        List<Pedido> pedidos = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            Pedido p = new Pedido();
            p.setCodigo("PED-" + i);
            pedidos.add(p);
        }

        when(usuarioDAO.contarUsuarios()).thenReturn(12L);
        when(usuarioDAO.listarAtivos()).thenReturn(usuariosAtivos);
        when(pedidoService.contarEventosDoDia(eq(PedidoStatus.RECEBIDO), any(LocalDate.class))).thenReturn(4L);
        when(pedidoService.contarEventosDoDia(eq(PedidoStatus.PRONTO), any(LocalDate.class))).thenReturn(3L);
        when(pedidoService.contarEventosDoDia(eq(PedidoStatus.RETIRADO), any(LocalDate.class))).thenReturn(2L);
        when(pedidoService.consultar(isNull(), any(LocalDate.class), isNull(), isNull(), isNull())).thenReturn(pedidos);

        LocalDate hoje = LocalDate.of(2024, 1, 15);
        try (MockedStatic<LocalDate> mocked = mockStatic(LocalDate.class, CALLS_REAL_METHODS)) {
            mocked.when(LocalDate::now).thenReturn(hoje);
            mocked.when(() -> LocalDate.of(anyInt(), anyInt(), anyInt())).thenCallRealMethod();

            servlet.doGet(request, response);
        }

        verify(request).setAttribute("totalUsuarios", 12L);
        ArgumentCaptor<List<Usuario>> usuariosCaptor = ArgumentCaptor.forClass(List.class);
        verify(request).setAttribute(eq("usuariosRecentes"), usuariosCaptor.capture());
        assertThat(usuariosCaptor.getValue()).hasSize(5);
        ArgumentCaptor<List<Pedido>> pedidosCaptor = ArgumentCaptor.forClass(List.class);
        verify(request).setAttribute(eq("pedidosRecentes"), pedidosCaptor.capture());
        assertThat(pedidosCaptor.getValue()).hasSize(5);
        verify(request).setAttribute("usuarioLogado", usuario);
        verify(request).setAttribute("recebidosHoje", 4L);
        verify(request).setAttribute("prontos", 3L);
        verify(request).setAttribute("retirados", 2L);
        verify(dispatcher).forward(request, response);
    }

    @Test
    void doGet_quandoOcorreErroEncaminhaParaPaginaErro() throws Exception {
        when(request.getSession(false)).thenReturn(session);
        when(session.getAttribute("usuario")).thenReturn(new Usuario());
        when(usuarioDAO.contarUsuarios()).thenThrow(new RuntimeException("falha"));
        when(request.getRequestDispatcher("/erro.jsp")).thenReturn(dispatcher);

        servlet.doGet(request, response);

        verify(request).setAttribute("erro", "Erro ao carregar dashboard");
        verify(dispatcher).forward(request, response);
    }

    @Test
    void doPost_deveDelegarParaDoGet() throws Exception {
        when(request.getSession(false)).thenReturn(null);
        when(request.getSession(true)).thenReturn(createdSession);
        when(request.getRequestURL()).thenReturn(new StringBuffer("http://localhost/app/dashboard"));
        when(request.getContextPath()).thenReturn("/app");

        servlet.doPost(request, response);

        verify(createdSession).setAttribute(eq("redirectAfterLogin"), any());
        verify(response).sendRedirect("/app/login");
    }

    private void injectPrivateField(String fieldName, Object value) throws Exception {
        Field field = DashboardServlet.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(servlet, value);
    }
}

