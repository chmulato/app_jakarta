package com.caracore.hub_town.servlet;

import com.caracore.hub_town.model.CanalPedido;
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
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@WebServlet("/recebimento/entrada")
public class RecebimentoEntradaServlet extends HttpServlet {
    private final PedidoService pedidoService = new PedidoService();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        pedidoService.sugerirPosicao().map(Posicao::getCodigo)
            .ifPresent(codigo -> request.setAttribute("posicaoSugerida", codigo));
        request.getRequestDispatcher("/recebimento/entrada.jsp").forward(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        HttpSession session = request.getSession(false);
        Usuario usuario = session != null ? (Usuario) session.getAttribute("usuario") : null;
        if (usuario == null) {
            response.sendRedirect(request.getContextPath() + "/login");
            return;
        }
        String destinatarioNome = request.getParameter("destinatarioNome");
        String destinatarioDocumento = request.getParameter("destinatarioDocumento");
        String destinatarioTelefone = request.getParameter("destinatarioTelefone");
        String canalParam = request.getParameter("canal");
        String codigoPedido = request.getParameter("codigoPedido");

        if (isBlank(destinatarioNome) || isBlank(destinatarioDocumento) || isBlank(destinatarioTelefone)) {
            request.setAttribute("erro", "Preencha todos os dados do destinatário");
            request.getRequestDispatcher("/recebimento/entrada.jsp").forward(request, response);
            return;
        }

        String[] etiquetas = request.getParameterValues("volumeEtiqueta");
        String[] pesos = request.getParameterValues("volumePeso");
        String[] dimensoes = request.getParameterValues("volumeDimensao");

        if (etiquetas == null || etiquetas.length == 0) {
            request.setAttribute("erro", "Informe ao menos um volume");
            request.getRequestDispatcher("/recebimento/entrada.jsp").forward(request, response);
            return;
        }

        List<Volume> volumes = new ArrayList<>();
        for (int i = 0; i < etiquetas.length; i++) {
            String etiqueta = etiquetas[i];
            if (isBlank(etiqueta)) {
                etiqueta = null; // será gerada automaticamente
            }
            Volume volume = new Volume();
            volume.setEtiqueta(etiqueta);
            if (pesos != null && i < pesos.length && !isBlank(pesos[i])) {
                try {
                    volume.setPeso(new BigDecimal(pesos[i].replace(',', '.')));
                } catch (NumberFormatException ignored) {}
            }
            if (dimensoes != null && i < dimensoes.length) {
                volume.setDimensoes(dimensoes[i]);
            }
            volumes.add(volume);
        }

        try {
            Pedido pedido = new Pedido();
            pedido.setCodigo(codigoPedido);
            pedido.setCanal(CanalPedido.MANUAL);
            pedido.setDestinatarioNome(destinatarioNome);
            pedido.setDestinatarioDocumento(destinatarioDocumento);
            pedido.setDestinatarioTelefone(destinatarioTelefone);
            Pedido pedidoSalvo = pedidoService.registrarPedidoManual(pedido, volumes, usuario.getNome());
            response.sendRedirect(request.getContextPath() + "/pedidos/detalhe?id=" + pedidoSalvo.getId());
        } catch (Exception e) {
            request.setAttribute("erro", e.getMessage());
            request.getRequestDispatcher("/recebimento/entrada.jsp").forward(request, response);
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
