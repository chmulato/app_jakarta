package com.caracore.hub_town.dao;

import com.caracore.hub_town.model.CanalPedido;
import com.caracore.hub_town.model.MapeamentoStatus;
import com.caracore.hub_town.model.PedidoStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;

import java.util.List;
import java.util.Optional;

public class MapeamentoStatusDAO {

    public MapeamentoStatus salvar(MapeamentoStatus mapeamento) {
        return JPAUtil.executeInTransaction(em -> {
            if (mapeamento.getId() == null) {
                em.persist(mapeamento);
                return mapeamento;
            }
            return em.merge(mapeamento);
        });
    }

    public Optional<MapeamentoStatus> buscarAtivo(CanalPedido canal, Long tenantId, String statusExterno) {
        EntityManager em = JPAUtil.getEntityManager();
        try {
            TypedQuery<MapeamentoStatus> query = em.createQuery(
                "SELECT m FROM MapeamentoStatus m WHERE m.canal = :canal AND m.tenantId = :tenantId " +
                    "AND LOWER(m.statusExterno) = LOWER(:statusExterno) AND m.ativo = TRUE",
                MapeamentoStatus.class);
            query.setParameter("canal", canal);
            query.setParameter("tenantId", tenantId);
            query.setParameter("statusExterno", statusExterno);
            List<MapeamentoStatus> result = query.getResultList();
            if (result.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(result.get(0));
        } finally {
            em.close();
        }
    }

    public List<MapeamentoStatus> listarPorTenant(Long tenantId, CanalPedido canal, boolean apenasAtivos) {
        EntityManager em = JPAUtil.getEntityManager();
        try {
            StringBuilder jpql = new StringBuilder("SELECT m FROM MapeamentoStatus m WHERE m.tenantId = :tenantId");
            if (canal != null) {
                jpql.append(" AND m.canal = :canal");
            }
            if (apenasAtivos) {
                jpql.append(" AND m.ativo = TRUE");
            }
            jpql.append(" ORDER BY m.updatedAt DESC");
            TypedQuery<MapeamentoStatus> query = em.createQuery(jpql.toString(), MapeamentoStatus.class);
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
            MapeamentoStatus mapeamento = em.find(MapeamentoStatus.class, id);
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

    public void atualizarDestinoInterno(Long id, PedidoStatus statusInterno, boolean terminal) {
        JPAUtil.executeInTransaction(em -> {
            MapeamentoStatus mapeamento = em.find(MapeamentoStatus.class, id);
            if (mapeamento != null) {
                mapeamento.setStatusInterno(statusInterno);
                mapeamento.setTerminal(terminal);
                em.merge(mapeamento);
            }
        });
    }
}
