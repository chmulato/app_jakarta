package com.caracore.hub_town.dao;

import com.caracore.hub_town.model.Usuario;
import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import jakarta.persistence.TypedQuery;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mindrot.jbcrypt.BCrypt;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UsuarioDAOTest {

    private final UsuarioDAO dao = new UsuarioDAO();

    @Mock
    private EntityManager entityManager;

    @Mock
    private TypedQuery<Usuario> usuarioQuery;

    @Mock
    private TypedQuery<Long> longQuery;

    @Test
    void salvar_quandoSenhaEmClaro_geraHashAntesDePersistir() throws Exception {
        Usuario usuario = new Usuario();
        usuario.setSenha("segredo");

        try (MockedStatic<JPAUtil> mocked = org.mockito.Mockito.mockStatic(JPAUtil.class)) {
            mocked.when(() -> JPAUtil.executeInTransaction(any(JPAUtil.TransactionCallback.class)))
                .thenAnswer(invocation -> {
                    JPAUtil.TransactionCallback<Usuario> callback = invocation.getArgument(0);
                    try {
                        return callback.execute(entityManager);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });

            Usuario salvo = dao.salvar(usuario);

            assertThat(usuario.getSenha()).startsWith("$2a$");
            assertThat(usuario.getSenha()).isNotEqualTo("segredo");
            verify(entityManager).persist(usuario);
            assertThat(salvo).isSameAs(usuario);
        }
    }

    @Test
    void salvar_quandoSenhaJaHash_naoReaplicaHash() throws Exception {
        String hash = BCrypt.hashpw("senha", BCrypt.gensalt());
        Usuario usuario = new Usuario();
        usuario.setSenha(hash);

        try (MockedStatic<JPAUtil> mocked = org.mockito.Mockito.mockStatic(JPAUtil.class)) {
            mocked.when(() -> JPAUtil.executeInTransaction(any(JPAUtil.TransactionCallback.class)))
                .thenAnswer(invocation -> {
                    JPAUtil.TransactionCallback<Usuario> callback = invocation.getArgument(0);
                    try {
                        return callback.execute(entityManager);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });

            dao.salvar(usuario);

            assertThat(usuario.getSenha()).isEqualTo(hash);
            verify(entityManager).persist(usuario);
        }
    }

    @Test
    void buscarPorEmail_quandoEncontrado_retornaOptional() {
        Usuario usuario = new Usuario();

        when(entityManager.createQuery(anyString(), eq(Usuario.class))).thenReturn(usuarioQuery);
        when(usuarioQuery.setParameter("email", "teste@ex.com")).thenReturn(usuarioQuery);
        when(usuarioQuery.getSingleResult()).thenReturn(usuario);

        try (MockedStatic<JPAUtil> mocked = org.mockito.Mockito.mockStatic(JPAUtil.class)) {
            mocked.when(JPAUtil::getEntityManager).thenReturn(entityManager);

            Optional<Usuario> resultado = dao.buscarPorEmail("teste@ex.com");

            assertThat(resultado).contains(usuario);
            verify(entityManager).close();
        }
    }

    @Test
    void buscarPorEmail_quandoNaoExiste_retornaEmpty() {
        when(entityManager.createQuery(anyString(), eq(Usuario.class))).thenReturn(usuarioQuery);
        when(usuarioQuery.setParameter("email", "nao@ex.com")).thenReturn(usuarioQuery);
        when(usuarioQuery.getSingleResult()).thenThrow(new NoResultException("not found"));

        try (MockedStatic<JPAUtil> mocked = org.mockito.Mockito.mockStatic(JPAUtil.class)) {
            mocked.when(JPAUtil::getEntityManager).thenReturn(entityManager);

            Optional<Usuario> resultado = dao.buscarPorEmail("nao@ex.com");

            assertThat(resultado).isEmpty();
            verify(entityManager).close();
        }
    }

    @Test
    void desativar_quandoUsuarioExiste_marcaComoInativo() throws Exception {
        Usuario usuario = new Usuario();
        usuario.setAtivo(true);
        when(entityManager.find(Usuario.class, 8L)).thenReturn(usuario);

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

            dao.desativar(8L);

            assertThat(usuario.getAtivo()).isFalse();
            verify(entityManager).merge(usuario);
        }
    }

    @Test
    void desativar_quandoUsuarioNaoExiste_naoChamaMerge() throws Exception {
        when(entityManager.find(Usuario.class, 9L)).thenReturn(null);

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

            dao.desativar(9L);

            verify(entityManager, never()).merge(any(Usuario.class));
        }
    }

    @Test
    void alterarSenha_quandoUsuarioExiste_atualizaHash() throws Exception {
        Usuario usuario = new Usuario();
        usuario.setSenha("antiga");
        when(entityManager.find(Usuario.class, 3L)).thenReturn(usuario);

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

            dao.alterarSenha(3L, "novaSenha");

            assertThat(usuario.getSenha()).startsWith("$2a$");
            verify(entityManager).merge(usuario);
        }
    }

    @Test
    void emailExiste_quandoOutroUsuarioPossuiEmail_retornaVerdadeiro() {
        when(entityManager.createQuery(anyString(), eq(Long.class))).thenReturn(longQuery);
        when(longQuery.setParameter("email", "teste@ex.com")).thenReturn(longQuery);
        when(longQuery.setParameter("id", 5L)).thenReturn(longQuery);
        when(longQuery.getSingleResult()).thenReturn(1L);

        try (MockedStatic<JPAUtil> mocked = org.mockito.Mockito.mockStatic(JPAUtil.class)) {
            mocked.when(JPAUtil::getEntityManager).thenReturn(entityManager);

            boolean existe = dao.emailExiste("teste@ex.com", 5L);

            assertThat(existe).isTrue();
            verify(entityManager).close();
        }
    }

    @Test
    void autenticar_quandoSenhaCorretaRetornaUsuario() {
        Usuario usuario = new Usuario();
        usuario.setSenha(BCrypt.hashpw("segredo", BCrypt.gensalt()));
        usuario.setAtivo(true);

        UsuarioDAO spyDao = org.mockito.Mockito.spy(new UsuarioDAO());
        doReturn(Optional.of(usuario)).when(spyDao).buscarPorEmail("user@ex.com");

        Optional<Usuario> autenticado = spyDao.autenticar("user@ex.com", "segredo");

        assertThat(autenticado).contains(usuario);
    }

    @Test
    void autenticar_quandoSenhaIncorreta_retornaEmpty() {
        Usuario usuario = new Usuario();
        usuario.setSenha(BCrypt.hashpw("segredo", BCrypt.gensalt()));
        usuario.setAtivo(true);

        UsuarioDAO spyDao = org.mockito.Mockito.spy(new UsuarioDAO());
        doReturn(Optional.of(usuario)).when(spyDao).buscarPorEmail("user@ex.com");

        Optional<Usuario> autenticado = spyDao.autenticar("user@ex.com", "errada");

        assertThat(autenticado).isEmpty();
    }

    @Test
    void autenticar_quandoUsuarioInativo_retornaEmpty() {
        Usuario usuario = new Usuario();
        usuario.setSenha(BCrypt.hashpw("segredo", BCrypt.gensalt()));
        usuario.setAtivo(false);

        UsuarioDAO spyDao = org.mockito.Mockito.spy(new UsuarioDAO());
        doReturn(Optional.of(usuario)).when(spyDao).buscarPorEmail("user@ex.com");

        Optional<Usuario> autenticado = spyDao.autenticar("user@ex.com", "segredo");

        assertThat(autenticado).isEmpty();
    }

    @Test
    void atualizar_quandoChamaExecuteInTransaction_retornaEntidadeMesclada() throws Exception {
        Usuario usuario = new Usuario();
        usuario.setId(1L);
        Usuario gerenciado = new Usuario();
        gerenciado.setId(1L);

        try (MockedStatic<JPAUtil> mocked = org.mockito.Mockito.mockStatic(JPAUtil.class)) {
            mocked.when(() -> JPAUtil.executeInTransaction(any(JPAUtil.TransactionCallback.class)))
                .thenAnswer(invocation -> {
                    JPAUtil.TransactionCallback<Usuario> callback = invocation.getArgument(0);
                    try {
                        return callback.execute(entityManager);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });

            when(entityManager.merge(usuario)).thenReturn(gerenciado);

            Usuario resultado = dao.atualizar(usuario);

            assertThat(resultado).isSameAs(gerenciado);
            verify(entityManager).merge(usuario);
        }
    }

    @Test
    void listarAtivos_retornaSomenteUsuariosAtivos() {
        Usuario usuario = new Usuario();
        when(entityManager.createQuery(anyString(), eq(Usuario.class))).thenReturn(usuarioQuery);
        when(usuarioQuery.getResultList()).thenReturn(List.of(usuario));

        try (MockedStatic<JPAUtil> mocked = org.mockito.Mockito.mockStatic(JPAUtil.class)) {
            mocked.when(JPAUtil::getEntityManager).thenReturn(entityManager);

            assertThat(dao.listarAtivos()).containsExactly(usuario);
            verify(entityManager).close();
        }
    }

    @Test
    void listarTodos_retornaEmOrdem() {
        Usuario primeiro = new Usuario();
        when(entityManager.createQuery(anyString(), eq(Usuario.class))).thenReturn(usuarioQuery);
        when(usuarioQuery.getResultList()).thenReturn(List.of(primeiro));

        try (MockedStatic<JPAUtil> mocked = org.mockito.Mockito.mockStatic(JPAUtil.class)) {
            mocked.when(JPAUtil::getEntityManager).thenReturn(entityManager);

            assertThat(dao.listarTodos()).containsExactly(primeiro);
            verify(entityManager).close();
        }
    }

    @Test
    void contarUsuarios_retornaTotal() {
        when(entityManager.createQuery(anyString(), eq(Long.class))).thenReturn(longQuery);
        when(longQuery.getSingleResult()).thenReturn(7L);

        try (MockedStatic<JPAUtil> mocked = org.mockito.Mockito.mockStatic(JPAUtil.class)) {
            mocked.when(JPAUtil::getEntityManager).thenReturn(entityManager);

            assertThat(dao.contarUsuarios()).isEqualTo(7L);
            verify(entityManager).close();
        }
    }

    @Test
    void remover_quandoUsuarioExiste_removeEntidade() throws Exception {
        Usuario usuario = new Usuario();
        when(entityManager.find(Usuario.class, 11L)).thenReturn(usuario);

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

            dao.remover(11L);

            verify(entityManager).remove(usuario);
        }
    }

    @Test
    void remover_quandoUsuarioNaoExiste_naoChamaRemove() throws Exception {
        when(entityManager.find(Usuario.class, 12L)).thenReturn(null);

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

            dao.remover(12L);

            verify(entityManager, never()).remove(any(Usuario.class));
        }
    }

    @Test
    void emailExiste_quandoBuscaPorEmailRetornaUsuario_retornaVerdadeiro() {
        UsuarioDAO spyDao = org.mockito.Mockito.spy(new UsuarioDAO());
        doReturn(Optional.of(new Usuario())).when(spyDao).buscarPorEmail("email@dominio.com");

        assertThat(spyDao.emailExiste("email@dominio.com")).isTrue();
    }

    @Test
    void emailExiste_quandoBuscaPorEmailNaoAcha_retornaFalso() {
        UsuarioDAO spyDao = org.mockito.Mockito.spy(new UsuarioDAO());
        doReturn(Optional.empty()).when(spyDao).buscarPorEmail("email@dominio.com");

        assertThat(spyDao.emailExiste("email@dominio.com")).isFalse();
    }

    @Test
    void buscarPorNome_delegaParaQuery() {
        Usuario usuario = new Usuario();
        when(entityManager.createQuery(anyString(), eq(Usuario.class))).thenReturn(usuarioQuery);
        when(usuarioQuery.setParameter("nome", "%Ana%")).thenReturn(usuarioQuery);
        when(usuarioQuery.getResultList()).thenReturn(List.of(usuario));

        try (MockedStatic<JPAUtil> mocked = org.mockito.Mockito.mockStatic(JPAUtil.class)) {
            mocked.when(JPAUtil::getEntityManager).thenReturn(entityManager);

            assertThat(dao.buscarPorNome("Ana")).containsExactly(usuario);
            verify(usuarioQuery).setParameter("nome", "%Ana%");
            verify(entityManager).close();
        }
    }

    @Test
    void autenticar_quandoSenhaEmTextoPlanoEResgataUsuario() {
        Usuario usuario = new Usuario();
        usuario.setSenha("segredo");
        usuario.setAtivo(true);

        UsuarioDAO spyDao = org.mockito.Mockito.spy(new UsuarioDAO());
        doReturn(Optional.of(usuario)).when(spyDao).buscarPorEmail("user@ex.com");

        Optional<Usuario> autenticado = spyDao.autenticar("user@ex.com", "segredo");

        assertThat(autenticado).contains(usuario);
    }

    @Test
    void buscarPorId_quandoEncontrado_fechaEntityManagerERetorna() {
        Usuario usuario = new Usuario();
        when(entityManager.find(Usuario.class, 15L)).thenReturn(usuario);

        try (MockedStatic<JPAUtil> mocked = org.mockito.Mockito.mockStatic(JPAUtil.class)) {
            mocked.when(JPAUtil::getEntityManager).thenReturn(entityManager);

            assertThat(dao.buscarPorId(15L)).contains(usuario);
            verify(entityManager).close();
        }
    }

}




