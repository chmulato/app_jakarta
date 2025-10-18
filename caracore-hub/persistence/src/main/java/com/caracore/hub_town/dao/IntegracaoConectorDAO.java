package com.caracore.hub_town.dao;

import com.caracore.hub_town.model.CanalPedido;
import com.caracore.hub_town.model.IntegracaoConector;
import com.caracore.hub_town.model.StatusConector;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;

import java.util.List;
import java.util.Optional;

public class IntegracaoConectorDAO {

    public IntegracaoConector salvar(IntegracaoConector conector) {
        return JPAUtil.executeInTransaction(em -> {
            if (conector.getId() == null) {
                em.persist(conector);
                return conector;
            }
            return em.merge(conector);
        });
    }

    public Optional<IntegracaoConector> buscarPorId(Long id) {
        EntityManager em = JPAUtil.getEntityManager();
        try {
            return Optional.ofNullable(em.find(IntegracaoConector.class, id));
        } finally {
            em.close();
        }
    }

    public Optional<IntegracaoConector> buscarAtivo(CanalPedido canal, Long tenantId) {
        EntityManager em = JPAUtil.getEntityManager();
        try {
            TypedQuery<IntegracaoConector> query = em.createQuery(
                "SELECT c FROM IntegracaoConector c WHERE c.canal = :canal AND c.tenantId = :tenantId AND c.status = :status",
                IntegracaoConector.class);
            query.setParameter("canal", canal);
            query.setParameter("tenantId", tenantId);
            query.setParameter("status", StatusConector.ATIVO);
            List<IntegracaoConector> result = query.getResultList();
            if (result.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(result.get(0));
        } finally {
            em.close();
        }
    }

    public List<IntegracaoConector> listarPorTenant(Long tenantId) {
        EntityManager em = JPAUtil.getEntityManager();
        try {
            TypedQuery<IntegracaoConector> query = em.createQuery(
                "SELECT c FROM IntegracaoConector c WHERE c.tenantId = :tenantId ORDER BY c.createdAt DESC",
                IntegracaoConector.class);
            query.setParameter("tenantId", tenantId);
            return query.getResultList();
        } finally {
            em.close();
        }
    }

    public void atualizarStatus(Long id, StatusConector status) {
        JPAUtil.executeInTransaction(em -> {
            IntegracaoConector conector = em.find(IntegracaoConector.class, id);
            if (conector != null) {
                conector.setStatus(status);
                em.merge(conector);
            }
        });
    }
}
