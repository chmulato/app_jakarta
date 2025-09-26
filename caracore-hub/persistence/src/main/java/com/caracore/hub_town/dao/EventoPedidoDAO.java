package com.caracore.hub_town.dao;

import com.caracore.hub_town.model.EventoPedido;
import jakarta.persistence.EntityManager;

import java.util.List;

public class EventoPedidoDAO {
    public EventoPedido salvar(EventoPedido evento) {
        return JPAUtil.executeInTransaction(em -> {
            if (evento.getId() == null) {
                em.persist(evento);
                return evento;
            }
            return em.merge(evento);
        });
    }

    public List<EventoPedido> listarPorPedido(Long pedidoId) {
        EntityManager em = JPAUtil.getEntityManager();
        try {
            return em.createQuery(
                    "SELECT e FROM EventoPedido e WHERE e.pedido.id = :pedidoId ORDER BY e.createdAt",
                    EventoPedido.class)
                .setParameter("pedidoId", pedidoId)
                .getResultList();
        } finally {
            em.close();
        }
    }
}
