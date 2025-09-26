package com.caracore.hub_town.service;

import com.caracore.hub_town.dao.JPAUtil;
import com.caracore.hub_town.dao.PedidoDAO;
import com.caracore.hub_town.dao.PosicaoDAO;
import com.caracore.hub_town.model.CanalPedido;
import com.caracore.hub_town.model.EventoPedido;
import com.caracore.hub_town.model.Pedido;
import com.caracore.hub_town.model.PedidoStatus;
import com.caracore.hub_town.model.Posicao;
import com.caracore.hub_town.model.TipoEvento;
import com.caracore.hub_town.model.Volume;
import com.caracore.hub_town.model.VolumeStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PedidoServiceTest {

    @Mock
    private PedidoDAO pedidoDAO;

    @Mock
    private PosicaoDAO posicaoDAO;

    private PedidoService service;

    @BeforeEach
    void setUp() {
        service = new PedidoService(pedidoDAO, posicaoDAO);
    }

    @Test
    void registrarPedidoManual_quandoDadosValidos_persistePedidoComVolumesEEvento() {
        Pedido pedido = buildPedidoBase();
        Volume volume = new Volume();
        volume.setPeso(new BigDecimal("2.30"));
        volume.setDimensoes("10x10x10");
        List<Volume> volumes = List.of(volume);

        when(pedidoDAO.buscarPorCodigo(any())).thenReturn(Optional.empty());
        when(pedidoDAO.salvar(any(Pedido.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Pedido salvo = service.registrarPedidoManual(pedido, volumes, "web-app");

        assertThat(salvo.getCodigo()).isNotBlank();
        assertThat(salvo.getStatus()).isEqualTo(PedidoStatus.RECEBIDO);
        assertThat(salvo.getVolumes()).hasSize(1);
        Volume volumePersistido = salvo.getVolumes().get(0);
        assertThat(volumePersistido.getEtiqueta()).isNotBlank();
        assertThat(volumePersistido.getStatus()).isEqualTo(VolumeStatus.RECEBIDO);
        assertThat(volumePersistido.getPedido()).isSameAs(salvo);
        assertThat(salvo.getEventos())
            .extracting(EventoPedido::getTipo)
            .containsExactly(TipoEvento.CRIACAO);

        ArgumentCaptor<Pedido> captor = ArgumentCaptor.forClass(Pedido.class);
        verify(pedidoDAO).salvar(captor.capture());
        assertThat(captor.getValue().getVolumes()).hasSize(1);
    }

    @Test
    void registrarPedidoManual_quandoCodigoDuplicado_lancaExcecao() {
        Pedido pedido = buildPedidoBase();
        pedido.setCodigo("PED-001");

        when(pedidoDAO.buscarPorCodigo("PED-001")).thenReturn(Optional.of(new Pedido()));

        assertThatThrownBy(() -> service.registrarPedidoManual(pedido, List.of(new Volume()), "api"))
            .isInstanceOf(IllegalArgumentException.class);

        verify(pedidoDAO, never()).salvar(any());
    }

    @Test
    void registrarPedidoManual_quandoSemVolumes_lancaExcecao() {
        Pedido pedido = buildPedidoBase();

        assertThatThrownBy(() -> service.registrarPedidoManual(pedido, List.of(), "api"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("volume");
    }

    @Test
    void marcarComoPronto_quandoPedidoExiste_atualizaStatusERegistraEvento() throws Exception {
        Pedido pedido = buildPedidoComVolume(VolumeStatus.ALOCADO);
        EntityManager em = org.mockito.Mockito.mock(EntityManager.class);
        when(em.find(Pedido.class, 42L)).thenReturn(pedido);
        when(em.merge(pedido)).thenAnswer(invocation -> invocation.getArgument(0));

        try (MockedStatic<JPAUtil> mocked = org.mockito.Mockito.mockStatic(JPAUtil.class)) {
            mocked.when(() -> JPAUtil.executeInTransaction(any(JPAUtil.TransactionCallback.class)))
                .thenAnswer(invocation -> {
                    JPAUtil.TransactionCallback<?> callback = invocation.getArgument(0);
                    return callback.execute(em);
                });

            Pedido atualizado = service.marcarComoPronto(42L, "analista");

            assertThat(atualizado.getStatus()).isEqualTo(PedidoStatus.PRONTO);
            assertThat(atualizado.getReadyAt()).isNotNull();
            assertThat(atualizado.getVolumes())
                .allMatch(v -> v.getStatus() == VolumeStatus.PRONTO);
            assertThat(atualizado.getEventos())
                .extracting(EventoPedido::getTipo)
                .contains(TipoEvento.PRONTO);
            verify(em).merge(pedido);
        }
    }

    @Test
    void marcarComoPronto_quandoPedidoNaoExiste_lancaEntityNotFound() {
        EntityManager em = org.mockito.Mockito.mock(EntityManager.class);
        when(em.find(Pedido.class, 7L)).thenReturn(null);

        try (MockedStatic<JPAUtil> mocked = org.mockito.Mockito.mockStatic(JPAUtil.class)) {
            mocked.when(() -> JPAUtil.executeInTransaction(any(JPAUtil.TransactionCallback.class)))
                .thenAnswer(invocation -> {
                    JPAUtil.TransactionCallback<?> callback = invocation.getArgument(0);
                    return callback.execute(em);
                });

            assertThatThrownBy(() -> service.marcarComoPronto(7L, "cli"))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("Pedido");
        }
    }

    @Test
    void registrarRetirada_quandoPedidoExiste_marcaRetirado() throws Exception {
        Pedido pedido = buildPedidoComVolume(VolumeStatus.PRONTO);
        pedido.setStatus(PedidoStatus.PRONTO);
        EntityManager em = org.mockito.Mockito.mock(EntityManager.class);
        when(em.find(Pedido.class, 12L)).thenReturn(pedido);
        when(em.merge(pedido)).thenAnswer(invocation -> invocation.getArgument(0));

        try (MockedStatic<JPAUtil> mocked = org.mockito.Mockito.mockStatic(JPAUtil.class)) {
            mocked.when(() -> JPAUtil.executeInTransaction(any(JPAUtil.TransactionCallback.class)))
                .thenAnswer(invocation -> {
                    JPAUtil.TransactionCallback<?> callback = invocation.getArgument(0);
                    return callback.execute(em);
                });

            Pedido atualizado = service.registrarRetirada(12L, "cli");

            assertThat(atualizado.getStatus()).isEqualTo(PedidoStatus.RETIRADO);
            assertThat(atualizado.getPickedUpAt()).isNotNull();
            assertThat(atualizado.getVolumes())
                .allMatch(v -> v.getStatus() == VolumeStatus.RETIRADO);
            assertThat(atualizado.getEventos())
                .extracting(EventoPedido::getTipo)
                .contains(TipoEvento.RETIRADA);
        }
    }

    @Test
    void registrarRetirada_quandoPedidoNaoExiste_lancaEntityNotFound() throws Exception {
        EntityManager em = org.mockito.Mockito.mock(EntityManager.class);
        when(em.find(Pedido.class, 55L)).thenReturn(null);

        try (MockedStatic<JPAUtil> mocked = org.mockito.Mockito.mockStatic(JPAUtil.class)) {
            mocked.when(() -> JPAUtil.executeInTransaction(any(JPAUtil.TransactionCallback.class)))
                .thenAnswer(invocation -> {
                    JPAUtil.TransactionCallback<?> callback = invocation.getArgument(0);
                    return callback.execute(em);
                });

            assertThatThrownBy(() -> service.registrarRetirada(55L, "cli"))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("Pedido");

            verify(em, never()).merge(any(Pedido.class));
        }
    }

    @Test
    void atualizarPosicao_quandoPosicaoInformada_atualizaVolumeEPosicao() throws Exception {
        Pedido pedido = buildPedidoBase();
        Volume volume = new Volume();
        volume.setEtiqueta("VOL-1");
        volume.setStatus(VolumeStatus.RECEBIDO);
        pedido.adicionarVolume(volume);

        Posicao nova = new Posicao();
        nova.setRua("A");
        nova.setModulo("1");
        nova.setNivel("1");
        nova.setCaixa("1");

        EntityManager em = org.mockito.Mockito.mock(EntityManager.class);
        when(em.find(Volume.class, 5L)).thenReturn(volume);
        when(em.find(Posicao.class, 9L)).thenReturn(nova);
        when(em.merge(volume)).thenAnswer(invocation -> invocation.getArgument(0));

        try (MockedStatic<JPAUtil> mocked = org.mockito.Mockito.mockStatic(JPAUtil.class)) {
            mocked.when(() -> JPAUtil.executeInTransaction(any(JPAUtil.TransactionCallback.class)))
                .thenAnswer(invocation -> {
                    JPAUtil.TransactionCallback<?> callback = invocation.getArgument(0);
                    return callback.execute(em);
                });

            Volume atualizado = service.atualizarPosicao(5L, 9L, "cli");

            assertThat(atualizado.getPosicao()).isEqualTo(nova);
            assertThat(atualizado.getStatus()).isEqualTo(VolumeStatus.ALOCADO);
            assertThat(nova.isOcupada()).isTrue();
            assertThat(pedido.getEventos())
                .extracting(EventoPedido::getTipo)
                .contains(TipoEvento.ALOCACAO);
        }
    }

    @Test
    void atualizarPosicao_quandoVolumeNaoExiste_lancaEntityNotFound() throws Exception {
        EntityManager em = org.mockito.Mockito.mock(EntityManager.class);
        when(em.find(Volume.class, 88L)).thenReturn(null);

        try (MockedStatic<JPAUtil> mocked = org.mockito.Mockito.mockStatic(JPAUtil.class)) {
            mocked.when(() -> JPAUtil.executeInTransaction(any(JPAUtil.TransactionCallback.class)))
                .thenAnswer(invocation -> {
                    JPAUtil.TransactionCallback<?> callback = invocation.getArgument(0);
                    return callback.execute(em);
                });

            assertThatThrownBy(() -> service.atualizarPosicao(88L, 10L, "cli"))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("Volume");

            verify(em, never()).merge(any(Volume.class));
        }
    }

    @Test
    void atualizarPosicao_quandoRemoverPosicao_liberaPosicaoAnterior() throws Exception {
        Pedido pedido = buildPedidoBase();
        Posicao antiga = new Posicao();
        antiga.setRua("B");
        antiga.setModulo("1");
        antiga.setNivel("1");
        antiga.setCaixa("1");
        antiga.marcarOcupada();

        Volume volume = new Volume();
        volume.setEtiqueta("VOL-2");
        volume.setStatus(VolumeStatus.ALOCADO);
        volume.setPosicao(antiga);
        pedido.adicionarVolume(volume);

        EntityManager em = org.mockito.Mockito.mock(EntityManager.class);
        when(em.find(Volume.class, 8L)).thenReturn(volume);
        when(em.merge(volume)).thenAnswer(invocation -> invocation.getArgument(0));

        try (MockedStatic<JPAUtil> mocked = org.mockito.Mockito.mockStatic(JPAUtil.class)) {
            mocked.when(() -> JPAUtil.executeInTransaction(any(JPAUtil.TransactionCallback.class)))
                .thenAnswer(invocation -> {
                    JPAUtil.TransactionCallback<?> callback = invocation.getArgument(0);
                    return callback.execute(em);
                });

            Volume atualizado = service.atualizarPosicao(8L, null, "cli");

            assertThat(atualizado.getPosicao()).isNull();
            assertThat(antiga.isOcupada()).isFalse();
            assertThat(pedido.getEventos())
                .extracting(EventoPedido::getTipo)
                .contains(TipoEvento.ALOCACAO);
        }
    }

    @Test
    void listarPosicoesLivres_filtraSomenteNaoOcupadas() {
        Posicao livre = new Posicao();
        livre.setRua("A");
        livre.setModulo("1");
        livre.setNivel("1");
        livre.setCaixa("1");
        Posicao ocupada = new Posicao();
        ocupada.setRua("B");
        ocupada.setModulo("1");
        ocupada.setNivel("1");
        ocupada.setCaixa("1");
        ocupada.marcarOcupada();
        when(posicaoDAO.listarTodas()).thenReturn(List.of(livre, ocupada));

        assertThat(service.listarPosicoesLivres())
            .containsExactly(livre);
    }

    @Test
    void sugerirPosicao_delegaParaDAO() {
        Posicao posicao = new Posicao();
        when(posicaoDAO.sugerirDisponivel()).thenReturn(Optional.of(posicao));

        assertThat(service.sugerirPosicao()).contains(posicao);
        verify(posicaoDAO).sugerirDisponivel();
    }

    @Test
    void contarEventosDoDia_delegaParaDAO() {
        when(pedidoDAO.contarEventosDoDia(eq(PedidoStatus.RETIRADO), any(LocalDate.class)))
            .thenReturn(3L);

        assertThat(service.contarEventosDoDia(PedidoStatus.RETIRADO, LocalDate.of(2024, 1, 1)))
            .isEqualTo(3L);
    }

    @Test
    void metodosDelegados_buscamValoresNosDAOs() {
        Pedido pedido = new Pedido();
        when(pedidoDAO.buscarPorCodigo("PED-9")).thenReturn(Optional.of(pedido));
        when(pedidoDAO.buscarPorId(10L)).thenReturn(Optional.of(pedido));
        when(pedidoDAO.buscarPorTelefone("11")).thenReturn(List.of(pedido));

        assertThat(service.buscarPorCodigo("PED-9")).contains(pedido);
        assertThat(service.buscarPorId(10L)).contains(pedido);
        assertThat(service.buscarPorTelefone("11")).containsExactly(pedido);
    }

    private Pedido buildPedidoBase() {
        Pedido pedido = new Pedido();
        pedido.setDestinatarioNome("Maria");
        pedido.setDestinatarioDocumento("123");
        pedido.setDestinatarioTelefone("119999");
        pedido.setCanal(CanalPedido.MANUAL);
        pedido.setStatus(PedidoStatus.RECEBIDO);
        return pedido;
    }

    private Pedido buildPedidoComVolume(VolumeStatus volumeStatus) {
        Pedido pedido = buildPedidoBase();
        Volume volume = new Volume();
        volume.setEtiqueta("VOL-123");
        volume.setStatus(volumeStatus);
        pedido.adicionarVolume(volume);
        return pedido;
    }
}
