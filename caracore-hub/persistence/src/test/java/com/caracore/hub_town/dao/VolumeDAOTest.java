package com.caracore.hub_town.dao;

import com.caracore.hub_town.model.Posicao;
import com.caracore.hub_town.model.Volume;
import com.caracore.hub_town.model.VolumeStatus;
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
class VolumeDAOTest {

    private final VolumeDAO dao = new VolumeDAO();

    @Mock
    private EntityManager entityManager;

    @Mock
    private TypedQuery<Volume> volumeQuery;

    @Test
    void salvar_quandoVolumeNovo_persiste() throws Exception {
        Volume volume = new Volume();

        try (MockedStatic<JPAUtil> mocked = org.mockito.Mockito.mockStatic(JPAUtil.class)) {
            mocked.when(() -> JPAUtil.executeInTransaction(any(JPAUtil.TransactionCallback.class)))
                .thenAnswer(invocation -> {
                    JPAUtil.TransactionCallback<Volume> callback = invocation.getArgument(0);
                    try {
                        return callback.execute(entityManager);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });

            Volume salvo = dao.salvar(volume);

            verify(entityManager).persist(volume);
            assertThat(salvo).isSameAs(volume);
        }
    }

    @Test
    void salvar_quandoVolumeExistente_chamaMerge() throws Exception {
        Volume volume = new Volume();
        volume.setId(3L);
        Volume gerenciado = new Volume();
        gerenciado.setId(3L);

        when(entityManager.merge(volume)).thenReturn(gerenciado);

        try (MockedStatic<JPAUtil> mocked = org.mockito.Mockito.mockStatic(JPAUtil.class)) {
            mocked.when(() -> JPAUtil.executeInTransaction(any(JPAUtil.TransactionCallback.class)))
                .thenAnswer(invocation -> {
                    JPAUtil.TransactionCallback<Volume> callback = invocation.getArgument(0);
                    try {
                        return callback.execute(entityManager);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });

            Volume salvo = dao.salvar(volume);

            verify(entityManager).merge(volume);
            assertThat(salvo).isSameAs(gerenciado);
        }
    }

    @Test
    void buscarPorId_quandoEncontrado_retornaOptional() {
        Volume volume = new Volume();
        when(entityManager.find(Volume.class, 5L)).thenReturn(volume);

        try (MockedStatic<JPAUtil> mocked = org.mockito.Mockito.mockStatic(JPAUtil.class)) {
            mocked.when(JPAUtil::getEntityManager).thenReturn(entityManager);

            Optional<Volume> resultado = dao.buscarPorId(5L);

            assertThat(resultado).contains(volume);
            verify(entityManager).close();
        }
    }

    @Test
    void buscarPorEtiqueta_quandoEncontrado_retornaVolume() {
        Volume volume = new Volume();

        when(entityManager.createQuery(anyString(), eq(Volume.class))).thenReturn(volumeQuery);
        when(volumeQuery.setParameter("etiqueta", "VOL-1")).thenReturn(volumeQuery);
        when(volumeQuery.getResultList()).thenReturn(List.of(volume));

        try (MockedStatic<JPAUtil> mocked = org.mockito.Mockito.mockStatic(JPAUtil.class)) {
            mocked.when(JPAUtil::getEntityManager).thenReturn(entityManager);

            Optional<Volume> resultado = dao.buscarPorEtiqueta("VOL-1");

            assertThat(resultado).contains(volume);
            verify(entityManager).close();
        }
    }

    @Test
    void buscarPorEtiqueta_quandoNaoEncontrado_retornaVazio() {
        when(entityManager.createQuery(anyString(), eq(Volume.class))).thenReturn(volumeQuery);
        when(volumeQuery.setParameter("etiqueta", "VOL-2")).thenReturn(volumeQuery);
        when(volumeQuery.getResultList()).thenReturn(List.of());

        try (MockedStatic<JPAUtil> mocked = org.mockito.Mockito.mockStatic(JPAUtil.class)) {
            mocked.when(JPAUtil::getEntityManager).thenReturn(entityManager);

            Optional<Volume> resultado = dao.buscarPorEtiqueta("VOL-2");

            assertThat(resultado).isEmpty();
            verify(entityManager).close();
        }
    }

    @Test
    void listarPorPedido_retornaVolumesOrdenados() {
        Volume volume = new Volume();

        when(entityManager.createQuery(anyString(), eq(Volume.class))).thenReturn(volumeQuery);
        when(volumeQuery.setParameter("pedidoId", 7L)).thenReturn(volumeQuery);
        when(volumeQuery.getResultList()).thenReturn(List.of(volume));

        try (MockedStatic<JPAUtil> mocked = org.mockito.Mockito.mockStatic(JPAUtil.class)) {
            mocked.when(JPAUtil::getEntityManager).thenReturn(entityManager);

            List<Volume> volumes = dao.listarPorPedido(7L);

            assertThat(volumes).containsExactly(volume);
            verify(entityManager).close();
        }
    }

    @Test
    void atualizarPosicao_quandoNovaPosicaoInformada_atualizaEstado() throws Exception {
        Volume volume = new Volume();
        volume.setStatus(VolumeStatus.RECEBIDO);
        Posicao posicaoParametro = new Posicao();
        posicaoParametro.setId(11L);
        Posicao posicaoGerenciada = new Posicao();
        posicaoGerenciada.setId(11L);

        when(entityManager.find(Volume.class, 4L)).thenReturn(volume);
        when(entityManager.find(Posicao.class, 11L)).thenReturn(posicaoGerenciada);

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

            dao.atualizarPosicao(4L, posicaoParametro);

            assertThat(volume.getPosicao()).isEqualTo(posicaoGerenciada);
            assertThat(volume.getStatus()).isEqualTo(VolumeStatus.ALOCADO);
            assertThat(posicaoGerenciada.isOcupada()).isTrue();
            verify(entityManager).merge(volume);
        }
    }

    @Test
    void atualizarPosicao_quandoVolumeNaoEncontrado_naoChamaMerge() throws Exception {
        Posicao posicaoParametro = new Posicao();
        posicaoParametro.setId(9L);
        when(entityManager.find(Volume.class, 8L)).thenReturn(null);

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

            dao.atualizarPosicao(8L, posicaoParametro);

            verify(entityManager, never()).merge(any(Volume.class));
        }
    }

    @Test
    void atualizarPosicao_quandoPosicaoNula_naoAlteraStatusParaAlocado() throws Exception {
        Volume volume = new Volume();
        volume.setStatus(VolumeStatus.RECEBIDO);
        when(entityManager.find(Volume.class, 6L)).thenReturn(volume);

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

            dao.atualizarPosicao(6L, null);

            assertThat(volume.getPosicao()).isNull();
            assertThat(volume.getStatus()).isEqualTo(VolumeStatus.RECEBIDO);
            verify(entityManager).merge(volume);
        }
    }
}


