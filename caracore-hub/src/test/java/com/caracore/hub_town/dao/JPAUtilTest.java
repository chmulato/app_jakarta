package com.caracore.hub_town.dao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.EntityTransaction;
import java.lang.reflect.Field;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class JPAUtilTest {

    @Mock
    private EntityManagerFactory entityManagerFactory;

    @Mock
    private EntityManager entityManager;

    @Mock
    private EntityTransaction transaction;

    private EntityManagerFactory originalFactory;
    private boolean originalInitialized;

    @BeforeEach
    void configureStaticState() throws Exception {
        originalFactory = (EntityManagerFactory) getStaticField("entityManagerFactory");
        originalInitialized = (boolean) getStaticField("initialized");
        setStaticField("entityManagerFactory", entityManagerFactory);
        setStaticField("initialized", true);

        when(entityManagerFactory.createEntityManager()).thenReturn(entityManager);
        when(entityManager.getTransaction()).thenReturn(transaction);
        when(entityManager.isOpen()).thenReturn(true);
    }

    @AfterEach
    void restoreStaticState() throws Exception {
        setStaticField("entityManagerFactory", originalFactory);
        setStaticField("initialized", originalInitialized);
    }

    @Test
    void executeInTransaction_quandoSucesso_commitaERetornaValor() {
        String resultado = JPAUtil.executeInTransaction((JPAUtil.TransactionCallback<String>) em -> {
            assertThat(em).isSameAs(entityManager);
            return "ok";
        });

        assertThat(resultado).isEqualTo("ok");
        InOrder ordem = inOrder(entityManager, transaction);
        ordem.verify(transaction).begin();
        ordem.verify(transaction).commit();
        ordem.verify(entityManager).close();
    }

    @Test
    void executeInTransaction_quandoCallbackLanca_efetuaRollback() {
        when(transaction.isActive()).thenReturn(true);

        assertThatThrownBy(() -> JPAUtil.executeInTransaction((JPAUtil.TransactionCallback<String>) em -> { throw new IllegalStateException("falhou"); }))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("falhou");

        InOrder ordem = inOrder(transaction, entityManager);
        ordem.verify(transaction).begin();
        ordem.verify(transaction).rollback();
        ordem.verify(entityManager).close();
    }

    @Test
    void executeInTransactionAction_quandoSucesso_commita() throws Exception {
        JPAUtil.executeInTransaction((JPAUtil.TransactionAction) em -> assertThat(em).isSameAs(entityManager));

        InOrder ordem = inOrder(transaction, entityManager);
        ordem.verify(transaction).begin();
        ordem.verify(transaction).commit();
        ordem.verify(entityManager).close();
    }

    private Object getStaticField(String fieldName) throws Exception {
        Field field = JPAUtil.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(null);
    }

    private void setStaticField(String fieldName, Object value) throws Exception {
        Field field = JPAUtil.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(null, value);
    }
}



