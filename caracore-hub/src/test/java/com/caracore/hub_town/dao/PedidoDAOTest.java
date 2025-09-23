package com.caracore.hub_town.dao;

import com.caracore.hub_town.model.CanalPedido;
import com.caracore.hub_town.model.Pedido;
import com.caracore.hub_town.model.PedidoStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PedidoDAOTest {

    private final PedidoDAO dao = new PedidoDAO();

    @Mock
    private EntityManager entityManager;

    @Mock
    private TypedQuery<Pedido> pedidoQuery;

    @Mock
    private TypedQuery<Long> longQuery;

    @Test
    void buscarComFiltros_quandoTodosParametrosGeramJpqlComCondicoesEAtributos() {
        LocalDate inicio = LocalDate.of(2024, 1, 1);
        LocalDate fim = LocalDate.of(2024, 1, 31);

        when(entityManager.createQuery(any(String.class), eq(Pedido.class))).thenReturn(pedidoQuery);
        when(pedidoQuery.setParameter(any(String.class), any())).thenReturn(pedidoQuery);
        when(pedidoQuery.getResultList()).thenReturn(List.of(new Pedido()));

        try (MockedStatic<JPAUtil> mocked = org.mockito.Mockito.mockStatic(JPAUtil.class)) {
            mocked.when(JPAUtil::getEntityManager).thenReturn(entityManager);

            List<Pedido> pedidos = dao.buscarComFiltros(PedidoStatus.RECEBIDO, inicio, fim, "Maria", CanalPedido.MANUAL);

            assertThat(pedidos).hasSize(1);
            ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
            verify(entityManager).createQuery(captor.capture(), eq(Pedido.class));
            String jpql = captor.getValue();
            assertThat(jpql)
                .contains("p.status = :status")
                .contains("p.canal = :canal")
                .contains("LOWER(p.destinatarioNome) LIKE :destinatario")
                .contains("p.createdAt >= :dataInicio")
                .contains("p.createdAt < :dataFim")
                .endsWith("ORDER BY p.createdAt DESC");

            verify(pedidoQuery).setParameter("status", PedidoStatus.RECEBIDO);
            verify(pedidoQuery).setParameter("canal", CanalPedido.MANUAL);
            verify(pedidoQuery).setParameter("destinatario", "%maria%");
            verify(pedidoQuery).setParameter("dataInicio", inicio.atStartOfDay());
            verify(pedidoQuery).setParameter("dataFim", fim.plusDays(1).atStartOfDay());
            verify(entityManager).close();
        }
    }

    @Test
    void buscarPorCodigo_retornaOptionalQuandoEncontrado() {
        when(entityManager.createQuery(any(String.class), eq(Pedido.class))).thenReturn(pedidoQuery);
        Pedido pedido = new Pedido();
        when(pedidoQuery.getResultList()).thenReturn(List.of(pedido));

        try (MockedStatic<JPAUtil> mocked = org.mockito.Mockito.mockStatic(JPAUtil.class)) {
            mocked.when(JPAUtil::getEntityManager).thenReturn(entityManager);

            Optional<Pedido> resultado = dao.buscarPorCodigo("PED-1");

            assertThat(resultado).contains(pedido);
            verify(entityManager).close();
        }
    }

    @Test
    void contarEventosDoDia_quandoStatusPronto_utilizaCampoReadyAt() {
        when(entityManager.createQuery(any(String.class), eq(Long.class))).thenReturn(longQuery);
        when(longQuery.setParameter(any(String.class), any())).thenReturn(longQuery);
        when(longQuery.getSingleResult()).thenReturn(5L);

        try (MockedStatic<JPAUtil> mocked = org.mockito.Mockito.mockStatic(JPAUtil.class)) {
            mocked.when(JPAUtil::getEntityManager).thenReturn(entityManager);

            long total = dao.contarEventosDoDia(PedidoStatus.PRONTO, LocalDate.of(2024, 1, 1));

            assertThat(total).isEqualTo(5L);
            ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
            verify(entityManager).createQuery(captor.capture(), eq(Long.class));
            assertThat(captor.getValue()).contains("p.readyAt");
            verify(longQuery).setParameter(eq("inicio"), any(LocalDateTime.class));
            verify(longQuery).setParameter(eq("fim"), any(LocalDateTime.class));
            verify(entityManager).close();
        }
    }

    @Test
    void contarEventosDoDia_quandoStatusRecebido_utilizaCampoCreatedAt() {
        when(entityManager.createQuery(any(String.class), eq(Long.class))).thenReturn(longQuery);
        when(longQuery.setParameter(any(String.class), any())).thenReturn(longQuery);
        when(longQuery.getSingleResult()).thenReturn(2L);

        try (MockedStatic<JPAUtil> mocked = org.mockito.Mockito.mockStatic(JPAUtil.class)) {
            mocked.when(JPAUtil::getEntityManager).thenReturn(entityManager);

            long total = dao.contarEventosDoDia(PedidoStatus.RECEBIDO, LocalDate.of(2024, 2, 1));

            assertThat(total).isEqualTo(2L);
            ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
            verify(entityManager).createQuery(captor.capture(), eq(Long.class));
            assertThat(captor.getValue()).contains("p.createdAt");
        }
    }

    @Test
    void contarPorStatus_quandoCanalInformado_adicionaFiltro() {
        when(entityManager.createQuery(any(String.class), eq(Long.class))).thenReturn(longQuery);
        when(longQuery.setParameter(any(String.class), any())).thenReturn(longQuery);
        when(longQuery.getSingleResult()).thenReturn(3L);

        try (MockedStatic<JPAUtil> mocked = org.mockito.Mockito.mockStatic(JPAUtil.class)) {
            mocked.when(JPAUtil::getEntityManager).thenReturn(entityManager);

            long total = dao.contarPorStatus(PedidoStatus.RECEBIDO, CanalPedido.MANUAL);

            assertThat(total).isEqualTo(3L);
            ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
            verify(entityManager).createQuery(captor.capture(), eq(Long.class));
            assertThat(captor.getValue()).contains("p.canal = :canal");
            verify(longQuery).setParameter("status", PedidoStatus.RECEBIDO);
            verify(longQuery).setParameter("canal", CanalPedido.MANUAL);
        }
    }

    @Test
    void contarPorStatus_quandoCanalNulo_naoDefineParametro() {
        when(entityManager.createQuery(any(String.class), eq(Long.class))).thenReturn(longQuery);
        when(longQuery.setParameter(any(String.class), any())).thenReturn(longQuery);
        when(longQuery.getSingleResult()).thenReturn(1L);

        try (MockedStatic<JPAUtil> mocked = org.mockito.Mockito.mockStatic(JPAUtil.class)) {
            mocked.when(JPAUtil::getEntityManager).thenReturn(entityManager);

            long total = dao.contarPorStatus(PedidoStatus.RETIRADO, null);

            assertThat(total).isEqualTo(1L);
            ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
            verify(entityManager).createQuery(captor.capture(), eq(Long.class));
            assertThat(captor.getValue()).doesNotContain("p.canal");
            verify(longQuery).setParameter("status", PedidoStatus.RETIRADO);
            verify(longQuery, never()).setParameter(eq("canal"), any());
        }
    }

    @Test
    void atualizarStatus_quandoPedidoEncontrado_atualizaCamposETimestamp() throws Exception {
        Pedido pedido = new Pedido();
        when(entityManager.find(Pedido.class, 4L)).thenReturn(pedido);

        try (MockedStatic<JPAUtil> mocked = org.mockito.Mockito.mockStatic(JPAUtil.class)) {
            mocked.when(() -> JPAUtil.executeInTransaction(any(JPAUtil.TransactionAction.class)))
                .thenAnswer(invocation -> {
                    JPAUtil.TransactionAction action = invocation.getArgument(0);
                    action.execute(entityManager);
                    return null;
                });

            dao.atualizarStatus(4L, PedidoStatus.PRONTO);

            assertThat(pedido.getStatus()).isEqualTo(PedidoStatus.PRONTO);
            assertThat(pedido.getReadyAt()).isNotNull();
            verify(entityManager).merge(pedido);
        }
    }

    @Test
    void atualizarStatus_quandoPedidoNaoEncontrado_naoChamaMerge() throws Exception {
        when(entityManager.find(Pedido.class, 9L)).thenReturn(null);

        try (MockedStatic<JPAUtil> mocked = org.mockito.Mockito.mockStatic(JPAUtil.class)) {
            mocked.when(() -> JPAUtil.executeInTransaction(any(JPAUtil.TransactionAction.class)))
                .thenAnswer(invocation -> {
                    JPAUtil.TransactionAction action = invocation.getArgument(0);
                    action.execute(entityManager);
                    return null;
                });

            dao.atualizarStatus(9L, PedidoStatus.RETIRADO);

            verify(entityManager, never()).merge(any(Pedido.class));
        }
    }
}
