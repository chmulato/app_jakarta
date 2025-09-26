package com.caracore.hub_town.service;

import com.caracore.hub_town.dao.PedidoDAO;
import com.caracore.hub_town.model.CanalPedido;
import com.caracore.hub_town.model.Pedido;
import com.caracore.hub_town.model.PedidoStatus;
import com.caracore.hub_town.model.Volume;
import com.caracore.hub_town.model.VolumeStatus;
import com.caracore.hub_town.dao.JPAUtil;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class PedidoServiceIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
        .withDatabaseName("caracore_test")
        .withUsername("test_user")
        .withPassword("test_pass");

    private static PedidoService pedidoService;
    private static PedidoDAO pedidoDAO;

    @BeforeAll
    static void beforeAll() {
        postgres.start();
        System.setProperty("DB_HOST", postgres.getHost());
        System.setProperty("DB_PORT", String.valueOf(postgres.getMappedPort(5432)));
        System.setProperty("DB_NAME", postgres.getDatabaseName());
        System.setProperty("DB_USER", postgres.getUsername());
        System.setProperty("DB_PASS", postgres.getPassword());

        Flyway.configure()
            .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
            .locations("filesystem:" + Path.of("src/main/resources/db/migration").toAbsolutePath())
            .load()
            .migrate();

        pedidoService = new PedidoService();
        pedidoDAO = new PedidoDAO();
    }

    @AfterEach
    void clean() {
        JPAUtil.executeInTransaction(em -> {
            em.createQuery("DELETE FROM EventoPedido").executeUpdate();
            em.createQuery("DELETE FROM Volume").executeUpdate();
            em.createQuery("DELETE FROM Pedido").executeUpdate();
        });
    }

    @Test
    void deveRegistrarPedidoManualComVolumeEEvento() {
        Pedido pedido = novoPedido("PED-IT-001");
        Volume volume = novoVolume(null);

        Pedido salvo = pedidoService.registrarPedidoManual(pedido, List.of(volume), "teste");

        assertThat(salvo.getId()).isNotNull();
        assertThat(salvo.getStatus()).isEqualTo(PedidoStatus.RECEBIDO);
        assertThat(salvo.getVolumes()).hasSize(1);
        assertThat(salvo.getEventos()).hasSize(1);

        Pedido buscado = pedidoDAO.buscarPorCodigo("PED-IT-001").orElseThrow();
        assertThat(buscado.getEventos()).extracting("tipo")
            .containsExactly(com.caracore.hub_town.model.TipoEvento.CRIACAO);
    }

    @Test
    void deveMarcarPedidoComoProntoEAtualizarVolumes() {
        Pedido pedido = pedidoService.registrarPedidoManual(novoPedido("PED-IT-002"), List.of(novoVolume(VolumeStatus.ALOCADO)), "teste");

        Pedido pronto = pedidoService.marcarComoPronto(pedido.getId(), "teste");

        assertThat(pronto.getStatus()).isEqualTo(PedidoStatus.PRONTO);
        assertThat(pronto.getReadyAt()).isNotNull();
        assertThat(pronto.getVolumes()).allMatch(v -> v.getStatus() == VolumeStatus.PRONTO);
        assertThat(pronto.getEventos()).extracting("tipo")
            .contains(com.caracore.hub_town.model.TipoEvento.CRIACAO, com.caracore.hub_town.model.TipoEvento.PRONTO);
    }

    @Test
    void deveRegistrarRetiradaERegistrarEvento() {
        Pedido pedido = pedidoService.registrarPedidoManual(novoPedido("PED-IT-003"), List.of(novoVolume(VolumeStatus.ALOCADO)), "teste");
        pedidoService.marcarComoPronto(pedido.getId(), "teste");

        Pedido retirado = pedidoService.registrarRetirada(pedido.getId(), "teste");

        assertThat(retirado.getStatus()).isEqualTo(PedidoStatus.RETIRADO);
        assertThat(retirado.getPickedUpAt()).isNotNull();
        assertThat(retirado.getVolumes()).allMatch(v -> v.getStatus() == VolumeStatus.RETIRADO);
        assertThat(retirado.getEventos()).extracting("tipo")
            .contains(com.caracore.hub_town.model.TipoEvento.RETIRADA);
    }

    @Test
    void deveBuscarPedidoPorTelefone() {
        pedidoService.registrarPedidoManual(novoPedido("PED-IT-004"), List.of(novoVolume(null)), "teste");

        List<Pedido> pedidos = pedidoService.buscarPorTelefone("11988887777");

        assertThat(pedidos).hasSize(1);
        assertThat(pedidos.get(0).getCodigo()).isEqualTo("PED-IT-004");
    }

    private Pedido novoPedido(String codigo) {
        Pedido pedido = new Pedido();
        pedido.setCodigo(codigo);
        pedido.setCanal(CanalPedido.MANUAL);
        pedido.setDestinatarioNome("Cliente Exemplo");
        pedido.setDestinatarioDocumento("12345678901");
        pedido.setDestinatarioTelefone("11988887777");
        return pedido;
    }

    private Volume novoVolume(VolumeStatus status) {
        Volume volume = new Volume();
        volume.setEtiqueta(null);
        volume.setDimensoes("30x20x15");
        volume.setPeso(BigDecimal.valueOf(2.5));
        if (status != null) {
            volume.setStatus(status);
        }
        return volume;
    }
}