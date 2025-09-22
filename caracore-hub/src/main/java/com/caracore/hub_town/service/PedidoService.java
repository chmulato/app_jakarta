package com.caracore.hub_town.service;

import com.caracore.hub_town.dao.JPAUtil;
import com.caracore.hub_town.dao.PedidoDAO;
import com.caracore.hub_town.dao.PosicaoDAO;
import com.caracore.hub_town.model.CanalPedido;
import com.caracore.hub_town.model.EventoPedido;
import com.caracore.hub_town.model.Pedido;
import com.caracore.hub_town.model.PedidoStatus;
import com.caracore.hub_town.model.Posicao;
import com.caracore.hub_town.model.TipoEvento;
import com.caracore.hub_town.model.Volume;
import com.caracore.hub_town.model.VolumeStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityNotFoundException;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

public class PedidoService {
    private final PedidoDAO pedidoDAO = new PedidoDAO();
    private final PosicaoDAO posicaoDAO = new PosicaoDAO();

    public Pedido registrarPedidoManual(Pedido pedido, List<Volume> volumes, String actor) {
        if (volumes == null || volumes.isEmpty()) {
            throw new IllegalArgumentException("Informe ao menos um volume");
        }
        if (pedido.getCodigo() == null || pedido.getCodigo().isBlank()) {
            pedido.setCodigo(gerarCodigo());
        }
        pedido.setCanal(pedido.getCanal() == null ? CanalPedido.MANUAL : pedido.getCanal());
        pedido.setStatus(PedidoStatus.RECEBIDO);
        pedidoDAO.buscarPorCodigo(pedido.getCodigo()).ifPresent(existing -> {
            throw new IllegalArgumentException("Código de pedido já cadastrado");
        });
        pedido.getVolumes().clear();
        for (Volume volume : volumes) {
            if (volume.getEtiqueta() == null || volume.getEtiqueta().isBlank()) {
                volume.setEtiqueta(gerarEtiqueta(pedido.getCodigo()));
            }
            volume.setStatus(VolumeStatus.RECEBIDO);
            pedido.adicionarVolume(volume);
        }
        EventoPedido evento = new EventoPedido();
        evento.setTipo(TipoEvento.CRIACAO);
        evento.setActor(actor);
        evento.setPayload("Pedido criado manualmente");
        pedido.adicionarEvento(evento);
        return pedidoDAO.salvar(pedido);
    }

    public Pedido marcarComoPronto(Long pedidoId, String actor) {
        return JPAUtil.executeInTransaction((EntityManager em) -> {
            Pedido pedido = em.find(Pedido.class, pedidoId);
            if (pedido == null) {
                throw new EntityNotFoundException("Pedido não encontrado");
            }
            pedido.marcarPronto();
            EventoPedido evento = new EventoPedido();
            evento.setTipo(TipoEvento.PRONTO);
            evento.setActor(actor);
            evento.setPayload("Pedido marcado como PRONTO");
            pedido.adicionarEvento(evento);
            return em.merge(pedido);
        });
    }

    public Pedido registrarRetirada(Long pedidoId, String actor) {
        return JPAUtil.executeInTransaction((EntityManager em) -> {
            Pedido pedido = em.find(Pedido.class, pedidoId);
            if (pedido == null) {
                throw new EntityNotFoundException("Pedido não encontrado");
            }
            pedido.marcarRetirado();
            EventoPedido evento = new EventoPedido();
            evento.setTipo(TipoEvento.RETIRADA);
            evento.setActor(actor);
            evento.setPayload("Retirada confirmada no balcão");
            pedido.adicionarEvento(evento);
            return em.merge(pedido);
        });
    }

    public List<Pedido> consultar(PedidoStatus status, LocalDate dataInicio, LocalDate dataFim,
                                   String destinatario, CanalPedido canal) {
        return pedidoDAO.buscarComFiltros(status, dataInicio, dataFim, destinatario, canal);
    }

    public Optional<Pedido> buscarPorCodigo(String codigo) {
        return pedidoDAO.buscarPorCodigo(codigo);
    }

    public Optional<Pedido> buscarPorId(Long id) {
        return pedidoDAO.buscarPorId(id);
    }

    public List<Pedido> buscarPorTelefone(String telefone) {
        return pedidoDAO.buscarPorTelefone(telefone);
    }

    public Volume atualizarPosicao(Long volumeId, Long posicaoId, String actor) {
        return JPAUtil.executeInTransaction((EntityManager em) -> {
            Volume volume = em.find(Volume.class, volumeId);
            if (volume == null) {
                throw new EntityNotFoundException("Volume não encontrado");
            }
            Posicao posicao = posicaoId != null ? em.find(Posicao.class, posicaoId) : null;
            if (posicao != null) {
                posicao.marcarOcupada();
            } else if (volume.getPosicao() != null) {
                volume.getPosicao().liberar();
            }
            volume.setPosicao(posicao);
            if (posicao != null) {
                volume.setStatus(VolumeStatus.ALOCADO);
            }
            EventoPedido evento = new EventoPedido();
            evento.setTipo(TipoEvento.ALOCACAO);
            evento.setActor(actor);
            evento.setPayload("Volume " + volume.getEtiqueta() + " alocado na posição " +
                (posicao != null ? posicao.getCodigo() : "sem posição"));
            volume.getPedido().adicionarEvento(evento);
            return em.merge(volume);
        });
    }

    public List<Posicao> listarPosicoesLivres() {
        return posicaoDAO.listarTodas().stream()
            .filter(posicao -> !posicao.isOcupada())
            .collect(Collectors.toList());
    }

    public Optional<Posicao> sugerirPosicao() {
        return posicaoDAO.sugerirDisponivel();
    }

    public long contarEventosDoDia(PedidoStatus status, LocalDate dia) {
        return pedidoDAO.contarEventosDoDia(status, dia);
    }

    private String gerarCodigo() {
        return "PED-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private String gerarEtiqueta(String codigoPedido) {
        return codigoPedido + "-VOL-" + UUID.randomUUID().toString().substring(0, 4).toUpperCase();
    }
}