package com.caracore.hub_town.dao;

import com.caracore.hub_town.model.Usuario;
import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import jakarta.persistence.TypedQuery;
import org.mindrot.jbcrypt.BCrypt;

import java.util.List;
import java.util.Optional;

/**
 * DAO para operações com usuários
 */
public class UsuarioDAO {
    public Usuario salvar(Usuario usuario) {
        if (usuario.getSenha() != null && !usuario.getSenha().startsWith("$2a$")) {
            usuario.setSenha(BCrypt.hashpw(usuario.getSenha(), BCrypt.gensalt()));
        }
        return JPAUtil.executeInTransaction((EntityManager em) -> {
            em.persist(usuario);
            return usuario;
        });
    }

    public Usuario atualizar(Usuario usuario) {
    return JPAUtil.executeInTransaction((EntityManager em) -> em.merge(usuario));
    }

    public Optional<Usuario> buscarPorId(Long id) {
        EntityManager em = JPAUtil.getEntityManager();
        try {
            Usuario usuario = em.find(Usuario.class, id);
            return Optional.ofNullable(usuario);
        } finally {
            em.close();
        }
    }

    public Optional<Usuario> buscarPorEmail(String email) {
        EntityManager em = JPAUtil.getEntityManager();
        try {
            TypedQuery<Usuario> query = em.createQuery(
                "SELECT u FROM Usuario u WHERE u.email = :email", Usuario.class);
            query.setParameter("email", email);
            try {
                Usuario usuario = query.getSingleResult();
                return Optional.of(usuario);
            } catch (NoResultException e) {
                return Optional.empty();
            }
        } finally {
            em.close();
        }
    }

    public List<Usuario> listarAtivos() {
        EntityManager em = JPAUtil.getEntityManager();
        try {
            TypedQuery<Usuario> query = em.createQuery(
                "SELECT u FROM Usuario u WHERE u.ativo = true ORDER BY u.nome", Usuario.class);
            return query.getResultList();
        } finally {
            em.close();
        }
    }

    public List<Usuario> listarTodos() {
        EntityManager em = JPAUtil.getEntityManager();
        try {
            TypedQuery<Usuario> query = em.createQuery(
                "SELECT u FROM Usuario u ORDER BY u.nome", Usuario.class);
            return query.getResultList();
        } finally {
            em.close();
        }
    }

    public Long contarUsuarios() {
        EntityManager em = JPAUtil.getEntityManager();
        try {
            TypedQuery<Long> query = em.createQuery(
                "SELECT COUNT(u) FROM Usuario u", Long.class);
            return query.getSingleResult();
        } finally {
            em.close();
        }
    }

    public void desativar(Long id) {
        JPAUtil.executeInTransaction((EntityManager em) -> {
            Usuario usuario = em.find(Usuario.class, id);
            if (usuario != null) {
                usuario.setAtivo(false);
                em.merge(usuario);
            }
        });
    }

    public void remover(Long id) {
        JPAUtil.executeInTransaction((EntityManager em) -> {
            Usuario usuario = em.find(Usuario.class, id);
            if (usuario != null) {
                em.remove(usuario);
            }
        });
    }

    public Optional<Usuario> autenticar(String email, String senha) {
        Optional<Usuario> usuarioOpt = buscarPorEmail(email);
        if (usuarioOpt.isPresent()) {
            Usuario usuario = usuarioOpt.get();
            if (!usuario.isAtivo()) {
                return Optional.empty();
            }
            try {
                if (usuario.getSenha().startsWith("$2a$") || usuario.getSenha().startsWith("$2b$") || usuario.getSenha().startsWith("$2y$")) {
                    if (BCrypt.checkpw(senha, usuario.getSenha())) {
                        return Optional.of(usuario);
                    }
                } else if (usuario.getSenha().equals(senha)) {
                    return Optional.of(usuario);
                }
            } catch (Exception e) {
                if (usuario.getSenha().equals(senha)) {
                    return Optional.of(usuario);
                }
            }
        }
        return Optional.empty();
    }

    public boolean emailExiste(String email) {
        return buscarPorEmail(email).isPresent();
    }

    public boolean emailExiste(String email, Long idUsuario) {
        EntityManager em = JPAUtil.getEntityManager();
        try {
            TypedQuery<Long> query = em.createQuery(
                "SELECT COUNT(u) FROM Usuario u WHERE u.email = :email AND u.id != :id", Long.class);
            query.setParameter("email", email);
            query.setParameter("id", idUsuario);
            return query.getSingleResult() > 0;
        } finally {
            em.close();
        }
    }

    public void alterarSenha(Long id, String novaSenha) {
        JPAUtil.executeInTransaction((EntityManager em) -> {
            Usuario usuario = em.find(Usuario.class, id);
            if (usuario != null) {
                usuario.setSenha(BCrypt.hashpw(novaSenha, BCrypt.gensalt()));
                em.merge(usuario);
            }
        });
    }

    public List<Usuario> buscarPorNome(String nome) {
        EntityManager em = JPAUtil.getEntityManager();
        try {
            TypedQuery<Usuario> query = em.createQuery(
                "SELECT u FROM Usuario u WHERE LOWER(u.nome) LIKE LOWER(:nome) ORDER BY u.nome", Usuario.class);
            query.setParameter("nome", "%" + nome + "%");
            return query.getResultList();
        } finally {
            em.close();
        }
    }
}
