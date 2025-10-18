package com.caracore.hub_town.dao;

import com.caracore.hub_town.model.CanalPedido;
import com.caracore.hub_town.model.IntegracaoConector;
import com.caracore.hub_town.model.StatusConector;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
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
class IntegracaoConectorDAOTest {

    private final IntegracaoConectorDAO dao = new IntegracaoConectorDAO();

    @Mock
    EntityManager entityManager;

    @Mock
    TypedQuery<IntegracaoConector> conectorQuery;

    @Test
    void salvar_quandoNovo_persisteEntidade() throws Exception {
        IntegracaoConector conector = new IntegracaoConector(CanalPedido.ML, 22L, "client", "secret");

        try (MockedStatic<JPAUtil> mocked = org.mockito.Mockito.mockStatic(JPAUtil.class)) {
            mocked.when(() -> JPAUtil.executeInTransaction(org.mockito.ArgumentMatchers.<JPAUtil.TransactionCallback<IntegracaoConector>>any()))
                .thenAnswer(invocation -> {
                    JPAUtil.TransactionCallback<IntegracaoConector> callback = invocation.getArgument(0);
                    try {
                        return callback.execute(entityManager);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });

            IntegracaoConector salvo = dao.salvar(conector);

            verify(entityManager).persist(conector);
            assertThat(salvo).isSameAs(conector);
        }
    }

    @Test
    void buscarAtivo_quandoNaoExiste_retornaEmpty() {
        when(entityManager.createQuery(anyString(), eq(IntegracaoConector.class))).thenReturn(conectorQuery);
        when(conectorQuery.setParameter(eq("canal"), any(CanalPedido.class))).thenReturn(conectorQuery);
        when(conectorQuery.setParameter(eq("tenantId"), anyLong())).thenReturn(conectorQuery);
        when(conectorQuery.setParameter(eq("status"), eq(StatusConector.ATIVO))).thenReturn(conectorQuery);
        when(conectorQuery.getResultList()).thenReturn(Collections.emptyList());

        try (MockedStatic<JPAUtil> mocked = org.mockito.Mockito.mockStatic(JPAUtil.class)) {
            mocked.when(JPAUtil::getEntityManager).thenReturn(entityManager);

            Optional<IntegracaoConector> encontrado = dao.buscarAtivo(CanalPedido.ML, 99L);

            assertThat(encontrado).isEmpty();
            verify(entityManager).close();
        }
    }

    @Test
    void listarPorTenant_quandoExistemRetornaLista() {
        IntegracaoConector conector = new IntegracaoConector(CanalPedido.ML, 5L, "client", "secret");
        when(entityManager.createQuery(anyString(), eq(IntegracaoConector.class))).thenReturn(conectorQuery);
        when(conectorQuery.setParameter("tenantId", 5L)).thenReturn(conectorQuery);
        when(conectorQuery.getResultList()).thenReturn(List.of(conector));

        try (MockedStatic<JPAUtil> mocked = org.mockito.Mockito.mockStatic(JPAUtil.class)) {
            mocked.when(JPAUtil::getEntityManager).thenReturn(entityManager);

            List<IntegracaoConector> conectores = dao.listarPorTenant(5L);

            assertThat(conectores).containsExactly(conector);
            verify(entityManager).close();
        }
    }
}
