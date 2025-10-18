package com.caracore.hub_town.dao;

import com.caracore.hub_town.model.Idempotencia;
import jakarta.persistence.EntityManager;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.sql.SQLException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IdempotenciaDAOTest {

    private final IdempotenciaDAO dao = new IdempotenciaDAO();

    @Mock
    EntityManager entityManager;

    @Test
    void registrar_quandoPrimeiraVez_salvaENotificaSucesso() throws Exception {
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

            boolean registrado = dao.registrar("ML|1|ORDER|123|v1");

            assertThat(registrado).isTrue();
            verify(entityManager).persist(new Idempotencia("ML|1|ORDER|123|v1"));
        }
    }

    @Test
    void registrar_quandoDuplicado_retornaFalse() {
        ConstraintViolationException violation = new ConstraintViolationException("dup", new SQLException(), "idempotencia_pk");

        try (MockedStatic<JPAUtil> mocked = org.mockito.Mockito.mockStatic(JPAUtil.class)) {
            mocked.when(() -> JPAUtil.executeInTransaction(org.mockito.ArgumentMatchers.<JPAUtil.TransactionAction>any()))
                .thenThrow(new RuntimeException(violation));

            boolean registrado = dao.registrar("ML|1|ORDER|123|v1");

            assertThat(registrado).isFalse();
        }
    }

    @Test
    void buscar_quandoEncontrado_retornaOptional() {
        Idempotencia esperado = new Idempotencia("ML|2|ORDER|999|v1");
        when(entityManager.find(Idempotencia.class, esperado.getChave())).thenReturn(esperado);

        try (MockedStatic<JPAUtil> mocked = org.mockito.Mockito.mockStatic(JPAUtil.class)) {
            mocked.when(JPAUtil::getEntityManager).thenReturn(entityManager);

            Optional<Idempotencia> encontrado = dao.buscar(esperado.getChave());

            assertThat(encontrado).contains(esperado);
            verify(entityManager).close();
        }
    }
}
