package com.caracore.hub_town.api;

import com.caracore.hub_town.model.CanalPedido;
import com.caracore.hub_town.model.Pedido;
import com.caracore.hub_town.model.PedidoStatus;
import com.caracore.hub_town.model.Posicao;
import com.caracore.hub_town.model.Volume;
import com.caracore.hub_town.model.VolumeStatus;
import com.caracore.hub_town.service.PedidoService;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PedidoResourceTest {

    @Mock
    private PedidoService pedidoService;

    private PedidoResource resource;

    @BeforeEach
    void setUp() {
        resource = new PedidoResource(pedidoService);
    }

    @Test
    void criarPedido_quandoRequestValido_retornaCreated() {
        PedidoResource.PedidoCreateRequest request = buildValidRequest();
        Pedido pedidoSalvo = buildPedidoPersistido();

        when(pedidoService.registrarPedidoManual(any(Pedido.class), anyList(), eq("cli-user")))
            .thenReturn(pedidoSalvo);

        Response response = resource.criarPedido(request);

        assertThat(response.getStatus()).isEqualTo(Response.Status.CREATED.getStatusCode());
        PedidoResource.PedidoResponse body = (PedidoResource.PedidoResponse) response.getEntity();
        assertThat(body.codigo).isEqualTo(pedidoSalvo.getCodigo());
        assertThat(body.destinatarioNome).isEqualTo(pedidoSalvo.getDestinatarioNome());
        assertThat(body.status).isEqualTo(pedidoSalvo.getStatus().name());
        assertThat(body.volumes).extracting(v -> v.posicao).containsExactly("A-01-01-01");

        ArgumentCaptor<Pedido> pedidoCaptor = ArgumentCaptor.forClass(Pedido.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Volume>> volumesCaptor = ArgumentCaptor.forClass(List.class);

        verify(pedidoService).registrarPedidoManual(pedidoCaptor.capture(), volumesCaptor.capture(), eq("cli-user"));

        Pedido pedidoEnviado = pedidoCaptor.getValue();
        assertThat(pedidoEnviado.getDestinatarioNome()).isEqualTo("Maria Oliveira");
        assertThat(pedidoEnviado.getCanal()).isEqualTo(CanalPedido.MANUAL);

        List<Volume> volumesEnviados = volumesCaptor.getValue();
        assertThat(volumesEnviados).hasSize(1);
        assertThat(volumesEnviados.get(0).getPeso()).isEqualByComparingTo("2.50");
        assertThat(volumesEnviados.get(0).getDimensoes()).isEqualTo("10x20x30");
    }

    @Test
    void criarPedido_quandoVolumesAusentes_retornaBadRequest() {
        PedidoResource.PedidoCreateRequest request = new PedidoResource.PedidoCreateRequest();
        request.destinatarioNome = "Maria Oliveira";
        request.destinatarioDocumento = "12345678900";
        request.destinatarioTelefone = "11999999999";

        Response response = resource.criarPedido(request);

        assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
        PedidoResource.ErrorResponse error = (PedidoResource.ErrorResponse) response.getEntity();
        assertThat(error.mensagem).contains("Informe ao menos um volume");
        verifyNoInteractions(pedidoService);
    }

    @Test
    void criarPedido_quandoServiceLancaIllegalArgument_retornaBadRequest() {
        PedidoResource.PedidoCreateRequest request = buildValidRequest();
        when(pedidoService.registrarPedidoManual(any(Pedido.class), anyList(), eq("cli-user")))
            .thenThrow(new IllegalArgumentException("dados invalidos"));

        Response response = resource.criarPedido(request);

        assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
        PedidoResource.ErrorResponse error = (PedidoResource.ErrorResponse) response.getEntity();
        assertThat(error.mensagem).isEqualTo("dados invalidos");
    }

    @Test
    void listarPedidos_quandoParametrosValidos_retornaLista() {
        Pedido pedido = buildPedidoPersistido();
        LocalDate inicio = LocalDate.parse("2024-01-01");
        LocalDate fim = LocalDate.parse("2024-01-31");

        when(pedidoService.consultar(PedidoStatus.RECEBIDO, inicio, fim, "Maria Oliveira", CanalPedido.MANUAL))
            .thenReturn(List.of(pedido));

        Response response = resource.listarPedidos("RECEBIDO", "2024-01-01", "2024-01-31", "Maria Oliveira", "MANUAL");

        assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
        List<?> pedidos = (List<?>) response.getEntity();
        assertThat(pedidos).hasSize(1);
        PedidoResource.PedidoResponse primeiro = (PedidoResource.PedidoResponse) pedidos.get(0);
        assertThat(primeiro.readyAt).isEqualTo(pedido.getReadyAt().toString());
        assertThat(primeiro.pickedUpAt).isEqualTo(pedido.getPickedUpAt().toString());

        verify(pedidoService).consultar(PedidoStatus.RECEBIDO, inicio, fim, "Maria Oliveira", CanalPedido.MANUAL);
    }

    @Test
    void listarPedidos_quandoParametrosInvalidos_trataComoNull() {
        when(pedidoService.consultar(null, null, null, "Joana", null)).thenReturn(List.of());

        Response response = resource.listarPedidos("desconhecido", "2024-13-99", "", "Joana", "outro");

        assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
        assertThat((List<?>) response.getEntity()).isEmpty();

        verify(pedidoService).consultar(null, null, null, "Joana", null);
    }

    @Test
    void marcarComoPronto_quandoSucesso_retornaOk() {
        Pedido pedido = buildPedidoPersistido();
        when(pedidoService.marcarComoPronto(10L, "api")).thenReturn(pedido);

        Response response = resource.marcarComoPronto(10L);

        assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
        verify(pedidoService).marcarComoPronto(10L, "api");
    }

    @Test
    void marcarComoPronto_quandoFalha_retornaBadRequest() {
        when(pedidoService.marcarComoPronto(99L, "api")).thenThrow(new RuntimeException("erro"));

        Response response = resource.marcarComoPronto(99L);

        assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
        PedidoResource.ErrorResponse error = (PedidoResource.ErrorResponse) response.getEntity();
        assertThat(error.mensagem).isEqualTo("erro");
    }

    @Test
    void confirmarRetirada_quandoSucesso_retornaOk() {
        Pedido pedido = buildPedidoPersistido();
        when(pedidoService.registrarRetirada(10L, "api")).thenReturn(pedido);

        Response response = resource.confirmarRetirada(10L);

        assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
        verify(pedidoService).registrarRetirada(10L, "api");
    }

    @Test
    void confirmarRetirada_quandoFalha_retornaBadRequest() {
        when(pedidoService.registrarRetirada(88L, "api")).thenThrow(new IllegalStateException("nao encontrado"));

        Response response = resource.confirmarRetirada(88L);

        assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
        PedidoResource.ErrorResponse error = (PedidoResource.ErrorResponse) response.getEntity();
        assertThat(error.mensagem).isEqualTo("nao encontrado");
    }

    private PedidoResource.PedidoCreateRequest buildValidRequest() {
        PedidoResource.PedidoCreateRequest request = new PedidoResource.PedidoCreateRequest();
        request.codigo = "PED-123";
        request.destinatarioNome = "Maria Oliveira";
        request.destinatarioDocumento = "12345678900";
        request.destinatarioTelefone = "11999999999";
        request.canal = "MANUAL";
        request.actor = "cli-user";

        PedidoResource.VolumeRequest volume = new PedidoResource.VolumeRequest();
        volume.etiqueta = "VOL-123";
        volume.peso = new BigDecimal("2.50");
        volume.dimensoes = "10x20x30";
        request.volumes.add(volume);
        return request;
    }

    private Pedido buildPedidoPersistido() {
        Pedido pedido = new Pedido();
        pedido.setId(10L);
        pedido.setCodigo("PED-123");
        pedido.setDestinatarioNome("Maria Oliveira");
        pedido.setDestinatarioDocumento("12345678900");
        pedido.setDestinatarioTelefone("11999999999");
        pedido.setStatus(PedidoStatus.RECEBIDO);
        pedido.setCanal(CanalPedido.MANUAL);
        pedido.setCreatedAt(LocalDateTime.of(2024, 1, 1, 10, 0));
        pedido.setReadyAt(LocalDateTime.of(2024, 1, 2, 11, 0));
        pedido.setPickedUpAt(LocalDateTime.of(2024, 1, 3, 12, 0));

        Volume volume = new Volume();
        volume.setId(5L);
        volume.setEtiqueta("VOL-123");
        volume.setPeso(new BigDecimal("2.50"));
        volume.setDimensoes("10x20x30");
        volume.setStatus(VolumeStatus.ALOCADO);
        Posicao posicao = new Posicao();
        posicao.setRua("A");
        posicao.setModulo("01");
        posicao.setNivel("01");
        posicao.setCaixa("01");
        volume.setPosicao(posicao);
        pedido.adicionarVolume(volume);
        return pedido;
    }
}
