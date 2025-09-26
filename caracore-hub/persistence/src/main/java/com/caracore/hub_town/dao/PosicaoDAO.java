package com.caracore.hub_town.dao;

import com.caracore.hub_town.model.Posicao;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;

import java.util.List;
import java.util.Optional;

public class PosicaoDAO {
    public Posicao salvar(Posicao posicao) {
        return JPAUtil.executeInTransaction(em -> {
            if (posicao.getId() == null) {
                em.persist(posicao);
                return posicao;
            }
            return em.merge(posicao);
        });
    }

    public Optional<Posicao> sugerirDisponivel() {
        EntityManager em = JPAUtil.getEntityManager();
        try {
            TypedQuery<Posicao> query = em.createQuery(
                "SELECT p FROM Posicao p WHERE p.ocupada = false ORDER BY p.rua, p.modulo, p.nivel, p.caixa", Posicao.class);
            query.setMaxResults(1);
            List<Posicao> result = query.getResultList();
            return result.isEmpty() ? Optional.empty() : Optional.of(result.get(0));
        } finally {
            em.close();
        }
    }

    public Optional<Posicao> buscarPorId(Long id) {
        EntityManager em = JPAUtil.getEntityManager();
        try {
            return Optional.ofNullable(em.find(Posicao.class, id));
        } finally {
            em.close();
        }
    }

    public List<Posicao> listarTodas() {
        EntityManager em = JPAUtil.getEntityManager();
        try {
            TypedQuery<Posicao> query = em.createQuery(
                "SELECT p FROM Posicao p ORDER BY p.rua, p.modulo, p.nivel, p.caixa", Posicao.class);
            return query.getResultList();
        } finally {
            em.close();
        }
    }

    public void marcarOcupada(Long id) {
        JPAUtil.executeInTransaction(em -> {
            Posicao posicao = em.find(Posicao.class, id);
            if (posicao != null) {
                posicao.marcarOcupada();
                em.merge(posicao);
            }
        });
    }

    public void liberar(Long id) {
        JPAUtil.executeInTransaction(em -> {
            Posicao posicao = em.find(Posicao.class, id);
            if (posicao != null) {
                posicao.liberar();
                em.merge(posicao);
            }
        });
    }
}
