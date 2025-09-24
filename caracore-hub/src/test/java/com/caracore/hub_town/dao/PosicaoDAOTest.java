package com.caracore.hub_town.dao;

import com.caracore.hub_town.model.Posicao;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PosicaoDAOTest {

    private final PosicaoDAO dao = new PosicaoDAO();

    @Mock
    private EntityManager entityManager;

    @Mock
    private TypedQuery<Posicao> posicaoQuery;

    @Test
    void salvar_quandoIdNulo_persistePosicao() throws Exception {
        Posicao posicao = new Posicao();

        try (MockedStatic<JPAUtil> mocked = org.mockito.Mockito.mockStatic(JPAUtil.class)) {
            mocked.when(() -> JPAUtil.executeInTransaction(any(JPAUtil.TransactionCallback.class)))
                .thenAnswer(invocation -> {
                    JPAUtil.TransactionCallback<Posicao> callback = invocation.getArgument(0);
                    try {
                        return callback.execute(entityManager);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });

            Posicao salvo = dao.salvar(posicao);

            verify(entityManager).persist(posicao);
            assertThat(salvo).isSameAs(posicao);
        }
    }

    @Test
    void salvar_quandoIdExiste_chamaMergeERetornaResultado() throws Exception {
        Posicao posicao = new Posicao();
        posicao.setId(10L);
        Posicao gerenciada = new Posicao();
        gerenciada.setId(10L);

        when(entityManager.merge(posicao)).thenReturn(gerenciada);

        try (MockedStatic<JPAUtil> mocked = org.mockito.Mockito.mockStatic(JPAUtil.class)) {
            mocked.when(() -> JPAUtil.executeInTransaction(any(JPAUtil.TransactionCallback.class)))
                .thenAnswer(invocation -> {
                    JPAUtil.TransactionCallback<Posicao> callback = invocation.getArgument(0);
                    try {
                        return callback.execute(entityManager);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });

            Posicao salvo = dao.salvar(posicao);

            verify(entityManager).merge(posicao);
            assertThat(salvo).isSameAs(gerenciada);
        }
    }

    @Test
    void sugerirDisponivel_quandoHaPosicao_retornaPrimeira() {
        Posicao posicao = new Posicao();

        when(entityManager.createQuery(anyString(), eq(Posicao.class))).thenReturn(posicaoQuery);
        when(posicaoQuery.setMaxResults(1)).thenReturn(posicaoQuery);
        when(posicaoQuery.getResultList()).thenReturn(List.of(posicao));

        try (MockedStatic<JPAUtil> mocked = org.mockito.Mockito.mockStatic(JPAUtil.class)) {
            mocked.when(JPAUtil::getEntityManager).thenReturn(entityManager);

            Optional<Posicao> resultado = dao.sugerirDisponivel();

            assertThat(resultado).contains(posicao);
            verify(posicaoQuery).setMaxResults(1);
            verify(entityManager).close();
        }
    }

    @Test
    void sugerirDisponivel_quandoNaoHaPosicao_retornaVazio() {
        when(entityManager.createQuery(anyString(), eq(Posicao.class))).thenReturn(posicaoQuery);
        when(posicaoQuery.setMaxResults(1)).thenReturn(posicaoQuery);
        when(posicaoQuery.getResultList()).thenReturn(List.of());

        try (MockedStatic<JPAUtil> mocked = org.mockito.Mockito.mockStatic(JPAUtil.class)) {
            mocked.when(JPAUtil::getEntityManager).thenReturn(entityManager);

            Optional<Posicao> resultado = dao.sugerirDisponivel();

            assertThat(resultado).isEmpty();
            verify(entityManager).close();
        }
    }

    @Test
    void buscarPorId_quandoEncontrada_retornaOptional() {
        Posicao posicao = new Posicao();
        when(entityManager.find(Posicao.class, 7L)).thenReturn(posicao);

        try (MockedStatic<JPAUtil> mocked = org.mockito.Mockito.mockStatic(JPAUtil.class)) {
            mocked.when(JPAUtil::getEntityManager).thenReturn(entityManager);

            Optional<Posicao> resultado = dao.buscarPorId(7L);

            assertThat(resultado).contains(posicao);
            verify(entityManager).close();
        }
    }

    @Test
    void listarTodas_retornarListaCompletaEFechaEntityManager() {
        Posicao primeira = new Posicao();
        when(entityManager.createQuery(anyString(), eq(Posicao.class))).thenReturn(posicaoQuery);
        when(posicaoQuery.getResultList()).thenReturn(List.of(primeira));

        try (MockedStatic<JPAUtil> mocked = org.mockito.Mockito.mockStatic(JPAUtil.class)) {
            mocked.when(JPAUtil::getEntityManager).thenReturn(entityManager);

            List<Posicao> posicoes = dao.listarTodas();

            assertThat(posicoes).containsExactly(primeira);
            verify(entityManager).close();
        }
    }

    @Test
    void marcarOcupada_quandoEncontrada_atualizaEstado() throws Exception {
        Posicao posicao = new Posicao();
        when(entityManager.find(Posicao.class, 4L)).thenReturn(posicao);

        try (MockedStatic<JPAUtil> mocked = org.mockito.Mockito.mockStatic(JPAUtil.class)) {
            mocked.when(() -> JPAUtil.executeInTransaction(any(JPAUtil.TransactionAction.class)))
                .thenAnswer(invocation -> {
                    JPAUtil.TransactionAction action = invocation.getArgument(0);
                    try {
                        action.execute(entityManager);
                        return null;
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });

            dao.marcarOcupada(4L);

            assertThat(posicao.isOcupada()).isTrue();
            verify(entityManager).merge(posicao);
        }
    }

    @Test
    void marcarOcupada_quandoNaoEncontrada_naoRealizaMerge() throws Exception {
        when(entityManager.find(Posicao.class, 9L)).thenReturn(null);

        try (MockedStatic<JPAUtil> mocked = org.mockito.Mockito.mockStatic(JPAUtil.class)) {
            mocked.when(() -> JPAUtil.executeInTransaction(any(JPAUtil.TransactionAction.class)))
                .thenAnswer(invocation -> {
                    JPAUtil.TransactionAction action = invocation.getArgument(0);
                    try {
                        action.execute(entityManager);
                        return null;
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });

            dao.marcarOcupada(9L);

            verify(entityManager, never()).merge(any(Posicao.class));
        }
    }

    @Test
    void liberar_quandoEncontrada_liberaPosicao() throws Exception {
        Posicao posicao = new Posicao();
        posicao.marcarOcupada();
        when(entityManager.find(Posicao.class, 2L)).thenReturn(posicao);

        try (MockedStatic<JPAUtil> mocked = org.mockito.Mockito.mockStatic(JPAUtil.class)) {
            mocked.when(() -> JPAUtil.executeInTransaction(any(JPAUtil.TransactionAction.class)))
                .thenAnswer(invocation -> {
                    JPAUtil.TransactionAction action = invocation.getArgument(0);
                    try {
                        action.execute(entityManager);
                        return null;
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });

            dao.liberar(2L);

            assertThat(posicao.isOcupada()).isFalse();
            verify(entityManager).merge(posicao);
        }
    }

    @Test
    void liberar_quandoNaoEncontrada_naoRealizaMerge() throws Exception {
        when(entityManager.find(Posicao.class, 5L)).thenReturn(null);

        try (MockedStatic<JPAUtil> mocked = org.mockito.Mockito.mockStatic(JPAUtil.class)) {
            mocked.when(() -> JPAUtil.executeInTransaction(any(JPAUtil.TransactionAction.class)))
                .thenAnswer(invocation -> {
                    JPAUtil.TransactionAction action = invocation.getArgument(0);
                    try {
                        action.execute(entityManager);
                        return null;
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });

            dao.liberar(5L);

            verify(entityManager, never()).merge(any(Posicao.class));
        }
    }
}
