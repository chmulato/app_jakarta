package com.caracore.hub_town.dao;

import com.caracore.hub_town.model.Posicao;
import com.caracore.hub_town.model.Volume;
import com.caracore.hub_town.model.VolumeStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;

import java.util.List;
import java.util.Optional;

public class VolumeDAO {
    public Volume salvar(Volume volume) {
        return JPAUtil.executeInTransaction(em -> {
            if (volume.getId() == null) {
                em.persist(volume);
                return volume;
            }
            return em.merge(volume);
        });
    }

    public Optional<Volume> buscarPorId(Long id) {
        EntityManager em = JPAUtil.getEntityManager();
        try {
            return Optional.ofNullable(em.find(Volume.class, id));
        } finally {
            em.close();
        }
    }

    public Optional<Volume> buscarPorEtiqueta(String etiqueta) {
        EntityManager em = JPAUtil.getEntityManager();
        try {
            TypedQuery<Volume> query = em.createQuery(
                "SELECT v FROM Volume v JOIN FETCH v.pedido WHERE v.etiqueta = :etiqueta", Volume.class);
            query.setParameter("etiqueta", etiqueta);
            List<Volume> result = query.getResultList();
            return result.isEmpty() ? Optional.empty() : Optional.of(result.get(0));
        } finally {
            em.close();
        }
    }

    public List<Volume> listarPorPedido(Long pedidoId) {
        EntityManager em = JPAUtil.getEntityManager();
        try {
            TypedQuery<Volume> query = em.createQuery(
                "SELECT v FROM Volume v WHERE v.pedido.id = :pedidoId ORDER BY v.id", Volume.class);
            query.setParameter("pedidoId", pedidoId);
            return query.getResultList();
        } finally {
            em.close();
        }
    }

    public void atualizarPosicao(Long volumeId, Posicao posicao) {
        JPAUtil.executeInTransaction(em -> {
            Volume volume = em.find(Volume.class, volumeId);
            if (volume != null) {
                Posicao posicaoGerenciada = posicao != null ? em.find(Posicao.class, posicao.getId()) : null;
                volume.setPosicao(posicaoGerenciada);
                if (posicaoGerenciada != null) {
                    posicaoGerenciada.marcarOcupada();
                    volume.setStatus(VolumeStatus.ALOCADO);
                }
                em.merge(volume);
            }
        });
    }
}
