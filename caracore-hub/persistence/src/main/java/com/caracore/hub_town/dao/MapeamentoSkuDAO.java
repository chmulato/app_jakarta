package com.caracore.hub_town.dao;

import com.caracore.hub_town.model.CanalPedido;
import com.caracore.hub_town.model.MapeamentoSku;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;

import java.util.List;
import java.util.Optional;

public class MapeamentoSkuDAO {

    public MapeamentoSku salvar(MapeamentoSku mapeamento) {
        return JPAUtil.executeInTransaction(em -> {
            if (mapeamento.getId() == null) {
                em.persist(mapeamento);
                return mapeamento;
            }
            return em.merge(mapeamento);
        });
    }

    public Optional<MapeamentoSku> buscarAtivo(CanalPedido canal, Long tenantId, String skuExterno) {
        EntityManager em = JPAUtil.getEntityManager();
        try {
            TypedQuery<MapeamentoSku> query = em.createQuery(
                "SELECT m FROM MapeamentoSku m WHERE m.canal = :canal AND m.tenantId = :tenantId " +
                    "AND LOWER(m.skuExterno) = LOWER(:skuExterno) AND m.ativo = TRUE",
                MapeamentoSku.class);
            query.setParameter("canal", canal);
            query.setParameter("tenantId", tenantId);
            query.setParameter("skuExterno", skuExterno);
            List<MapeamentoSku> result = query.getResultList();
            if (result.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(result.get(0));
        } finally {
            em.close();
        }
    }

    public List<MapeamentoSku> listarPorTenant(CanalPedido canal, Long tenantId, boolean apenasAtivos) {
        EntityManager em = JPAUtil.getEntityManager();
        try {
            StringBuilder jpql = new StringBuilder("SELECT m FROM MapeamentoSku m WHERE m.tenantId = :tenantId");
            if (canal != null) {
                jpql.append(" AND m.canal = :canal");
            }
            if (apenasAtivos) {
                jpql.append(" AND m.ativo = TRUE");
            }
            jpql.append(" ORDER BY m.updatedAt DESC");
            TypedQuery<MapeamentoSku> query = em.createQuery(jpql.toString(), MapeamentoSku.class);
            query.setParameter("tenantId", tenantId);
            if (canal != null) {
                query.setParameter("canal", canal);
            }
            return query.getResultList();
        } finally {
            em.close();
        }
    }

    public void alterarStatus(Long id, boolean ativo) {
        JPAUtil.executeInTransaction(em -> {
            MapeamentoSku mapeamento = em.find(MapeamentoSku.class, id);
            if (mapeamento != null) {
                if (ativo) {
                    mapeamento.ativar();
                } else {
                    mapeamento.desativar();
                }
                em.merge(mapeamento);
            }
        });
    }
}
