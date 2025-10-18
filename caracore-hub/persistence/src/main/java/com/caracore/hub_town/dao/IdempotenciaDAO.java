package com.caracore.hub_town.dao;

import com.caracore.hub_town.model.Idempotencia;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import org.hibernate.exception.ConstraintViolationException;

import java.util.Optional;

public class IdempotenciaDAO {

    public boolean registrar(String chave) {
        Idempotencia entidade = new Idempotencia(chave);
        try {
            JPAUtil.executeInTransaction(em -> {
                em.persist(entidade);
            });
            return true;
        } catch (RuntimeException ex) {
            if (isConstraintViolation(ex)) {
                return false;
            }
            throw ex;
        }
    }

    public Optional<Idempotencia> buscar(String chave) {
        EntityManager em = JPAUtil.getEntityManager();
        try {
            return Optional.ofNullable(em.find(Idempotencia.class, chave));
        } finally {
            em.close();
        }
    }

    private boolean isConstraintViolation(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof ConstraintViolationException) {
                return true;
            }
            if (current instanceof PersistenceException && current.getCause() != null) {
                current = current.getCause();
                continue;
            }
            current = current.getCause();
        }
        return false;
    }
}
