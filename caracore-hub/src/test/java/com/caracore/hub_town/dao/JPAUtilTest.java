package com.caracore.hub_town.dao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.caracore.hub_town.config.DatabaseConfig;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.EntityTransaction;
import jakarta.persistence.Persistence;
import java.lang.reflect.Field;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class JPAUtilTest {

    @Mock
    private EntityManagerFactory entityManagerFactory;

    @Mock
    private EntityManager entityManager;

    @Mock
    private EntityTransaction transaction;

    @Mock
    private DatabaseConfig databaseConfig;

    private EntityManagerFactory originalFactory;
    private boolean originalInitialized;

    @BeforeEach
    void configureStaticState() throws Exception {
        originalFactory = (EntityManagerFactory) getStaticField("entityManagerFactory");
        originalInitialized = (boolean) getStaticField("initialized");
        setStaticField("entityManagerFactory", entityManagerFactory);
        setStaticField("initialized", true);

        lenient().when(entityManagerFactory.createEntityManager()).thenReturn(entityManager);
        lenient().when(entityManager.getTransaction()).thenReturn(transaction);
        lenient().when(entityManager.isOpen()).thenReturn(true);
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
        lenient().when(transaction.isActive()).thenReturn(true);

        assertThatThrownBy(() -> JPAUtil.executeInTransaction((JPAUtil.TransactionCallback<String>) em -> {
            throw new IllegalStateException("falhou");
        }))
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

    @Test
    void initialize_quandoJaInicializado_naoRecriaFabrica() throws Exception {
        try (MockedStatic<Persistence> persistence = org.mockito.Mockito.mockStatic(Persistence.class)) {
            JPAUtil.initialize();
            persistence.verifyNoInteractions();
        }
    }

    @Test
    void initialize_quandoStandaloneCriaFactoryComHikari() throws Exception {
        setStaticField("initialized", false);
        setStaticField("entityManagerFactory", null);

        try (MockedStatic<DatabaseConfig> dbMock = org.mockito.Mockito.mockStatic(DatabaseConfig.class);
             MockedStatic<Persistence> persistenceMock = org.mockito.Mockito.mockStatic(Persistence.class)) {
            dbMock.when(DatabaseConfig::getInstance).thenReturn(databaseConfig);
            when(databaseConfig.testConnection()).thenReturn(true);

            ArgumentCaptor<Map<String, Object>> propsCaptor = ArgumentCaptor.forClass(Map.class);
            persistenceMock.when(() -> Persistence.createEntityManagerFactory(eq("meuAppPU"), anyMap()))
                .thenReturn(entityManagerFactory);

            JPAUtil.initialize();

            assertThat((EntityManagerFactory) getStaticField("entityManagerFactory")).isSameAs(entityManagerFactory);
            assertThat((Boolean) getStaticField("initialized")).isTrue();

            persistenceMock.verify(() -> Persistence.createEntityManagerFactory(eq("meuAppPU"), propsCaptor.capture()));
            Map<String, Object> props = propsCaptor.getValue();
            assertThat(props).containsEntry("hibernate.hikari.jdbcUrl", "jdbc:postgresql://localhost:5432/meu_app_db");
            assertThat(props.get("hibernate.hikari.username")).isEqualTo("meu_app_user");
            assertThat(props.get("hibernate.hikari.password")).isEqualTo("meu_app_password");
            verify(databaseConfig).testConnection();
        }
    }

    @Test
    void getEntityManager_quandoFactoryDisponivel_retornaInstancia() {
        EntityManager em = JPAUtil.getEntityManager();

        assertThat(em).isSameAs(entityManager);
        verify(entityManagerFactory).createEntityManager();
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
