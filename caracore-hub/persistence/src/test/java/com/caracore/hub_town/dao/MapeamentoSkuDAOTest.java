package com.caracore.hub_town.dao;

import com.caracore.hub_town.model.CanalPedido;
import com.caracore.hub_town.model.MapeamentoSku;
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
class MapeamentoSkuDAOTest {

    private final MapeamentoSkuDAO dao = new MapeamentoSkuDAO();

    @Mock
    EntityManager entityManager;

    @Mock
    TypedQuery<MapeamentoSku> skuQuery;

    @Test
    void buscarAtivo_quandoExiste_retornaOptional() {
        MapeamentoSku esperado = new MapeamentoSku(CanalPedido.ML, 7L, "SKU-EXT", "SKU-INT");
        when(entityManager.createQuery(anyString(), eq(MapeamentoSku.class))).thenReturn(skuQuery);
        when(skuQuery.setParameter(eq("canal"), any(CanalPedido.class))).thenReturn(skuQuery);
        when(skuQuery.setParameter(eq("tenantId"), anyLong())).thenReturn(skuQuery);
        when(skuQuery.setParameter(eq("skuExterno"), anyString())).thenReturn(skuQuery);
        when(skuQuery.getResultList()).thenReturn(List.of(esperado));

        try (MockedStatic<JPAUtil> mocked = org.mockito.Mockito.mockStatic(JPAUtil.class)) {
            mocked.when(JPAUtil::getEntityManager).thenReturn(entityManager);

            Optional<MapeamentoSku> encontrado = dao.buscarAtivo(CanalPedido.ML, 7L, "sku-ext");

            assertThat(encontrado).contains(esperado);
            verify(entityManager).close();
        }
    }

    @Test
    void listarPorTenant_quandoFiltraApenasAtivos() {
        when(entityManager.createQuery(anyString(), eq(MapeamentoSku.class))).thenReturn(skuQuery);
        when(skuQuery.setParameter("tenantId", 7L)).thenReturn(skuQuery);
        when(skuQuery.setParameter(eq("canal"), eq(CanalPedido.ML))).thenReturn(skuQuery);
        when(skuQuery.getResultList()).thenReturn(List.of());

        try (MockedStatic<JPAUtil> mocked = org.mockito.Mockito.mockStatic(JPAUtil.class)) {
            mocked.when(JPAUtil::getEntityManager).thenReturn(entityManager);

            List<MapeamentoSku> resultados = dao.listarPorTenant(CanalPedido.ML, 7L, true);

            assertThat(resultados).isEmpty();
            verify(entityManager).close();
        }
    }

    @Test
    void alterarStatus_quandoDesativar_aplicaMudanca() throws Exception {
        MapeamentoSku mapeamento = new MapeamentoSku(CanalPedido.ML, 9L, "EXT", "INT");
        when(entityManager.find(MapeamentoSku.class, 11L)).thenReturn(mapeamento);

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

            dao.alterarStatus(11L, false);

            assertThat(mapeamento.getAtivo()).isFalse();
            verify(entityManager).merge(mapeamento);
        }
    }
}
