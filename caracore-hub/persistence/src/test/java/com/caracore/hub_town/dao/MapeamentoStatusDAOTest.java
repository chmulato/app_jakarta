package com.caracore.hub_town.dao;

import com.caracore.hub_town.model.CanalPedido;
import com.caracore.hub_town.model.MapeamentoStatus;
import com.caracore.hub_town.model.PedidoStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MapeamentoStatusDAOTest {

    private final MapeamentoStatusDAO dao = new MapeamentoStatusDAO();

    @Mock
    EntityManager entityManager;

    @Mock
    TypedQuery<MapeamentoStatus> statusQuery;

    @Test
    void buscarAtivo_quandoExiste_retornaOptional() {
        MapeamentoStatus esperado = new MapeamentoStatus(CanalPedido.ML, 7L, "SHIPPED", PedidoStatus.PRONTO);
        when(entityManager.createQuery(anyString(), eq(MapeamentoStatus.class))).thenReturn(statusQuery);
        when(statusQuery.setParameter(eq("canal"), any(CanalPedido.class))).thenReturn(statusQuery);
        when(statusQuery.setParameter(eq("tenantId"), anyLong())).thenReturn(statusQuery);
        when(statusQuery.setParameter(eq("statusExterno"), anyString())).thenReturn(statusQuery);
        when(statusQuery.getResultList()).thenReturn(List.of(esperado));

        try (MockedStatic<JPAUtil> mocked = org.mockito.Mockito.mockStatic(JPAUtil.class)) {
            mocked.when(JPAUtil::getEntityManager).thenReturn(entityManager);

            Optional<MapeamentoStatus> encontrado = dao.buscarAtivo(CanalPedido.ML, 7L, "shipped");

            assertThat(encontrado).contains(esperado);
            verify(entityManager).close();
        }
    }

    @Test
    void listarPorTenant_quandoSemFiltroDeCanal_retornaTodos() {
        when(entityManager.createQuery(anyString(), eq(MapeamentoStatus.class))).thenReturn(statusQuery);
        when(statusQuery.setParameter("tenantId", 5L)).thenReturn(statusQuery);
        when(statusQuery.getResultList()).thenReturn(List.of());

        try (MockedStatic<JPAUtil> mocked = org.mockito.Mockito.mockStatic(JPAUtil.class)) {
            mocked.when(JPAUtil::getEntityManager).thenReturn(entityManager);

            List<MapeamentoStatus> lista = dao.listarPorTenant(5L, null, false);

            assertThat(lista).isEmpty();
            verify(entityManager).close();
        }
    }

    @Test
    void atualizarDestinoInterno_quandoEncontrado_atualizaStatusETerminal() throws Exception {
        MapeamentoStatus mapeamento = new MapeamentoStatus(CanalPedido.ML, 1L, "DELIVERED", PedidoStatus.PRONTO);
        when(entityManager.find(MapeamentoStatus.class, 12L)).thenReturn(mapeamento);

        try (MockedStatic<JPAUtil> mocked = org.mockito.Mockito.mockStatic(JPAUtil.class)) {
            mocked.when(() -> JPAUtil.executeInTransaction(org.mockito.ArgumentMatchers.<JPAUtil.TransactionAction>any()))
                .thenAnswer(invocation -> {
                    JPAUtil.TransactionAction action = invocation.getArgument(0);
                    try {
                        action.execute(entityManager);
                        return null;
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });

            dao.atualizarDestinoInterno(12L, PedidoStatus.RETIRADO, true);

            assertThat(mapeamento.getStatusInterno()).isEqualTo(PedidoStatus.RETIRADO);
            assertThat(mapeamento.getTerminal()).isTrue();
            verify(entityManager).merge(mapeamento);
        }
    }
}
