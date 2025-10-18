package com.caracore.hub_town.dao;

import com.caracore.hub_town.model.CanalPedido;
import com.caracore.hub_town.model.IntegracaoEvento;
import com.caracore.hub_town.model.StatusEvento;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;

import java.util.List;
import java.util.Optional;

public class IntegracaoEventoDAO {

    public IntegracaoEvento salvar(IntegracaoEvento evento) {
        return JPAUtil.executeInTransaction(em -> {
            if (evento.getId() == null) {
                em.persist(evento);
                return evento;
            }
            return em.merge(evento);
        });
    }

    public Optional<IntegracaoEvento> buscarPorId(Long id) {
        EntityManager em = JPAUtil.getEntityManager();
        try {
            return Optional.ofNullable(em.find(IntegracaoEvento.class, id));
        } finally {
            em.close();
        }
    }

    public List<IntegracaoEvento> buscarPendentes(CanalPedido canal, int limite) {
        EntityManager em = JPAUtil.getEntityManager();
        try {
            TypedQuery<IntegracaoEvento> query = em.createQuery(
                "SELECT e FROM IntegracaoEvento e WHERE e.canal = :canal AND e.status = :status ORDER BY e.receivedAt ASC",
                IntegracaoEvento.class);
            query.setParameter("canal", canal);
            query.setParameter("status", StatusEvento.RECEBIDO);
            if (limite > 0) {
                query.setMaxResults(limite);
            }
            return query.getResultList();
        } finally {
            em.close();
        }
    }

    public boolean existeEvento(CanalPedido canal, Long tenantId, String externalId, String tipo) {
        EntityManager em = JPAUtil.getEntityManager();
        try {
            TypedQuery<Long> query = em.createQuery(
                "SELECT COUNT(e) FROM IntegracaoEvento e WHERE e.canal = :canal AND e.tenantId = :tenantId " +
                    "AND e.externalId = :externalId AND e.tipo = :tipo",
                Long.class);
            query.setParameter("canal", canal);
            query.setParameter("tenantId", tenantId);
            query.setParameter("externalId", externalId);
            query.setParameter("tipo", tipo);
            return query.getSingleResult() > 0;
        } finally {
            em.close();
        }
    }

    public void atualizarStatus(Long id, StatusEvento status, String erro) {
        JPAUtil.executeInTransaction(em -> {
            IntegracaoEvento evento = em.find(IntegracaoEvento.class, id);
            if (evento == null) {
                return;
            }
            switch (status) {
                case PROCESSANDO:
                    evento.marcarProcessando();
                    break;
                case PROCESSADO:
                    evento.marcarProcessado();
                    break;
                case ERRO:
                    evento.marcarErro(erro);
                    break;
                case IGNORADO:
                    evento.marcarIgnorado();
                    break;
                case RECEBIDO:
                    evento.resetarParaReprocessamento();
                    break;
            }
            em.merge(evento);
        });
    }
}
