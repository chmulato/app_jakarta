package com.caracore.hub_town.dao;

import com.caracore.hub_town.model.EventoPedido;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EventoPedidoDAOTest {

    private final EventoPedidoDAO dao = new EventoPedidoDAO();

    @Mock
    private EntityManager entityManager;

    @Mock
    private TypedQuery<EventoPedido> eventoQuery;

    @Test
    void salvar_quandoEventoNovo_persisteEretornaInstancia() throws Exception {
        EventoPedido evento = new EventoPedido();

        try (MockedStatic<JPAUtil> mocked = org.mockito.Mockito.mockStatic(JPAUtil.class)) {
            mocked.when(() -> JPAUtil.executeInTransaction(any(JPAUtil.TransactionCallback.class)))
                .thenAnswer(invocation -> {
                    JPAUtil.TransactionCallback<EventoPedido> callback = invocation.getArgument(0);
                    try {
                        return callback.execute(entityManager);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });

            EventoPedido salvo = dao.salvar(evento);

            verify(entityManager).persist(evento);
            assertThat(salvo).isSameAs(evento);
        }
    }

    @Test
    void salvar_quandoEventoExistente_chamaMerge() throws Exception {
        EventoPedido evento = new EventoPedido();
        evento.setId(4L);
        EventoPedido gerenciado = new EventoPedido();
        gerenciado.setId(4L);

        when(entityManager.merge(evento)).thenReturn(gerenciado);

        try (MockedStatic<JPAUtil> mocked = org.mockito.Mockito.mockStatic(JPAUtil.class)) {
            mocked.when(() -> JPAUtil.executeInTransaction(any(JPAUtil.TransactionCallback.class)))
                .thenAnswer(invocation -> {
                    JPAUtil.TransactionCallback<EventoPedido> callback = invocation.getArgument(0);
                    try {
                        return callback.execute(entityManager);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });

            EventoPedido salvo = dao.salvar(evento);

            verify(entityManager).merge(evento);
            assertThat(salvo).isSameAs(gerenciado);
        }
    }

    @Test
    void listarPorPedido_retornaEventosOrdenados() {
        EventoPedido evento = new EventoPedido();

        when(entityManager.createQuery(anyString(), eq(EventoPedido.class))).thenReturn(eventoQuery);
        when(eventoQuery.setParameter("pedidoId", 12L)).thenReturn(eventoQuery);
        when(eventoQuery.getResultList()).thenReturn(List.of(evento));

        try (MockedStatic<JPAUtil> mocked = org.mockito.Mockito.mockStatic(JPAUtil.class)) {
            mocked.when(JPAUtil::getEntityManager).thenReturn(entityManager);

            List<EventoPedido> eventos = dao.listarPorPedido(12L);

            assertThat(eventos).containsExactly(evento);
            verify(entityManager).close();
        }
    }
}

