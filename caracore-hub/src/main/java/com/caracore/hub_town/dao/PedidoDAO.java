package com.caracore.hub_town.dao;

import com.caracore.hub_town.model.CanalPedido;
import com.caracore.hub_town.model.Pedido;
import com.caracore.hub_town.model.PedidoStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public class PedidoDAO {
    public Pedido salvar(Pedido pedido) {
        return JPAUtil.executeInTransaction(em -> {
            if (pedido.getId() == null) {
                em.persist(pedido);
                return pedido;
            }
            return em.merge(pedido);
        });
    }

    public Optional<Pedido> buscarPorId(Long id) {
        EntityManager em = JPAUtil.getEntityManager();
        try {
            TypedQuery<Pedido> query = em.createQuery(
                "SELECT DISTINCT p FROM Pedido p " +
                    "LEFT JOIN FETCH p.volumes " +
                    "LEFT JOIN FETCH p.eventos " +
                    "WHERE p.id = :id",
                Pedido.class);
            query.setParameter("id", id);
            List<Pedido> result = query.getResultList();
            return result.isEmpty() ? Optional.empty() : Optional.of(result.get(0));
        } finally {
            em.close();
        }
    }

    public Optional<Pedido> buscarPorCodigo(String codigo) {
        EntityManager em = JPAUtil.getEntityManager();
        try {
            TypedQuery<Pedido> query = em.createQuery(
                "SELECT DISTINCT p FROM Pedido p " +
                    "LEFT JOIN FETCH p.volumes " +
                    "LEFT JOIN FETCH p.eventos " +
                    "WHERE p.codigo = :codigo",
                Pedido.class);
            query.setParameter("codigo", codigo);
            List<Pedido> result = query.getResultList();
            return result.isEmpty() ? Optional.empty() : Optional.of(result.get(0));
        } finally {
            em.close();
        }
    }

    public List<Pedido> buscarComFiltros(PedidoStatus status, LocalDate dataInicio, LocalDate dataFim,
                                         String destinatario, CanalPedido canal) {
        EntityManager em = JPAUtil.getEntityManager();
        try {
            StringBuilder jpql = new StringBuilder("SELECT DISTINCT p FROM Pedido p LEFT JOIN FETCH p.volumes WHERE 1=1");
            if (status != null) {
                jpql.append(" AND p.status = :status");
            }
            if (canal != null) {
                jpql.append(" AND p.canal = :canal");
            }
            if (destinatario != null && !destinatario.isBlank()) {
                jpql.append(" AND LOWER(p.destinatarioNome) LIKE :destinatario");
            }
            if (dataInicio != null) {
                jpql.append(" AND p.createdAt >= :dataInicio");
            }
            if (dataFim != null) {
                jpql.append(" AND p.createdAt < :dataFim");
            }
            jpql.append(" ORDER BY p.createdAt DESC");
            TypedQuery<Pedido> query = em.createQuery(jpql.toString(), Pedido.class);
            if (status != null) {
                query.setParameter("status", status);
            }
            if (canal != null) {
                query.setParameter("canal", canal);
            }
            if (destinatario != null && !destinatario.isBlank()) {
                query.setParameter("destinatario", "%" + destinatario.toLowerCase() + "%");
            }
            if (dataInicio != null) {
                query.setParameter("dataInicio", dataInicio.atStartOfDay());
            }
            if (dataFim != null) {
                query.setParameter("dataFim", dataFim.plusDays(1).atStartOfDay());
            }
            return query.getResultList();
        } finally {
            em.close();
        }
    }

    public List<Pedido> buscarPorTelefone(String telefone) {
        EntityManager em = JPAUtil.getEntityManager();
        try {
            TypedQuery<Pedido> query = em.createQuery(
                "SELECT DISTINCT p FROM Pedido p " +
                    "LEFT JOIN FETCH p.volumes " +
                    "LEFT JOIN FETCH p.eventos " +
                    "WHERE p.destinatarioTelefone LIKE :telefone",
                Pedido.class);
            query.setParameter("telefone", "%" + telefone + "%");
            return query.getResultList();
        } finally {
            em.close();
        }
    }

    public long contarEventosDoDia(PedidoStatus status, LocalDate dia) {
        EntityManager em = JPAUtil.getEntityManager();
        try {
            LocalDateTime inicio = dia.atStartOfDay();
            LocalDateTime fim = dia.plusDays(1).atStartOfDay();
            String campo;
            switch (status) {
                case PRONTO:
                    campo = "readyAt";
                    break;
                case RETIRADO:
                    campo = "pickedUpAt";
                    break;
                default:
                    campo = "createdAt";
            }
            String jpql = "SELECT COUNT(p) FROM Pedido p WHERE p." + campo + " IS NOT NULL " +
                "AND p." + campo + " >= :inicio AND p." + campo + " < :fim";
            TypedQuery<Long> query = em.createQuery(jpql, Long.class);
            query.setParameter("inicio", inicio);
            query.setParameter("fim", fim);
            return query.getSingleResult();
        } finally {
            em.close();
        }
    }

    public long contarPorStatus(PedidoStatus status, CanalPedido canal) {
        EntityManager em = JPAUtil.getEntityManager();
        try {
            StringBuilder jpql = new StringBuilder("SELECT COUNT(p) FROM Pedido p WHERE p.status = :status");
            if (canal != null) {
                jpql.append(" AND p.canal = :canal");
            }
            TypedQuery<Long> query = em.createQuery(jpql.toString(), Long.class);
            query.setParameter("status", status);
            if (canal != null) {
                query.setParameter("canal", canal);
            }
            return query.getSingleResult();
        } finally {
            em.close();
        }
    }

    public void atualizarStatus(Long pedidoId, PedidoStatus status) {
        JPAUtil.executeInTransaction(em -> {
            Pedido pedido = em.find(Pedido.class, pedidoId);
            if (pedido != null) {
                pedido.setStatus(status);
                if (status == PedidoStatus.PRONTO && pedido.getReadyAt() == null) {
                    pedido.setReadyAt(LocalDateTime.now());
                }
                if (status == PedidoStatus.RETIRADO) {
                    pedido.setPickedUpAt(LocalDateTime.now());
                }
                em.merge(pedido);
            }
        });
    }
}