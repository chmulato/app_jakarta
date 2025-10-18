package com.caracore.hub_town.dao;

import com.caracore.hub_town.model.CanalPedido;
import com.caracore.hub_town.model.IntegracaoEvento;
import com.caracore.hub_town.model.StatusEvento;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IntegracaoEventoDAOTest {

    private final IntegracaoEventoDAO dao = new IntegracaoEventoDAO();

    @Mock
    EntityManager entityManager;

    @Mock
    TypedQuery<IntegracaoEvento> eventoQuery;

    @Mock
    TypedQuery<Long> countQuery;

    @Test
    void buscarPendentes_quandoLimiteMaiorQueZero_aplicaSetMaxResults() {
        IntegracaoEvento evento = new IntegracaoEvento(CanalPedido.ML, 1L, "ext-1", "ORDER_CREATED", "{}");
        when(entityManager.createQuery(anyString(), eq(IntegracaoEvento.class))).thenReturn(eventoQuery);
        when(eventoQuery.setParameter("canal", CanalPedido.ML)).thenReturn(eventoQuery);
        when(eventoQuery.setParameter("status", StatusEvento.RECEBIDO)).thenReturn(eventoQuery);
        when(eventoQuery.setMaxResults(10)).thenReturn(eventoQuery);
        when(eventoQuery.getResultList()).thenReturn(List.of(evento));

        try (MockedStatic<JPAUtil> mocked = org.mockito.Mockito.mockStatic(JPAUtil.class)) {
            mocked.when(JPAUtil::getEntityManager).thenReturn(entityManager);

            List<IntegracaoEvento> pendentes = dao.buscarPendentes(CanalPedido.ML, 10);

            assertThat(pendentes).containsExactly(evento);
            verify(eventoQuery, times(1)).setMaxResults(10);
            verify(entityManager).close();
        }
    }

    @Test
    void atualizarStatus_quandoProcessado_atualizaEmerge() throws Exception {
        IntegracaoEvento evento = new IntegracaoEvento(CanalPedido.ML, 1L, "ext", "ORDER_CREATED", "{}");
        when(entityManager.find(IntegracaoEvento.class, 15L)).thenReturn(evento);

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

            dao.atualizarStatus(15L, StatusEvento.PROCESSADO, null);

            assertThat(evento.getStatus()).isEqualTo(StatusEvento.PROCESSADO);
            assertThat(evento.getProcessedAt()).isNotNull();
            verify(entityManager).merge(evento);
        }
    }

    @Test
    void existeEvento_quandoCountMaiorQueZero_retornaTrue() {
        when(entityManager.createQuery(anyString(), eq(Long.class))).thenReturn(countQuery);
        when(countQuery.setParameter(eq("canal"), any(CanalPedido.class))).thenReturn(countQuery);
        when(countQuery.setParameter(eq("tenantId"), anyLong())).thenReturn(countQuery);
        when(countQuery.setParameter(eq("externalId"), anyString())).thenReturn(countQuery);
        when(countQuery.setParameter(eq("tipo"), anyString())).thenReturn(countQuery);
        when(countQuery.getSingleResult()).thenReturn(1L);

        try (MockedStatic<JPAUtil> mocked = org.mockito.Mockito.mockStatic(JPAUtil.class)) {
            mocked.when(JPAUtil::getEntityManager).thenReturn(entityManager);

            boolean existe = dao.existeEvento(CanalPedido.ML, 1L, "ext", "ORDER_CREATED");

            assertThat(existe).isTrue();
            verify(entityManager).close();
        }
    }
}
