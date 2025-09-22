package com.caracore.hub_town.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class PedidoTest {

    @Test
    void deveAdicionarVolumeEAtribuirPedido() {
        Pedido pedido = novoPedidoBasico();
        Volume volume = novoVolume();
        pedido.adicionarVolume(volume);
        assertThat(pedido.getVolumes()).hasSize(1);
        assertThat(pedido.getVolumes().get(0).getPedido()).isEqualTo(pedido);
        assertThat(pedido.getVolumes().get(0).getStatus()).isEqualTo(VolumeStatus.RECEBIDO);
    }

    @Test
    void deveMarcarPedidoComoProntoAtualizandoVolumes() {
        Pedido pedido = novoPedidoComVolume();
        pedido.getVolumes().get(0).setStatus(VolumeStatus.ALOCADO);
        pedido.marcarPronto();
        assertThat(pedido.getStatus()).isEqualTo(PedidoStatus.PRONTO);
        assertThat(pedido.getReadyAt()).isNotNull();
        assertThat(pedido.getVolumes()).allMatch(v -> v.getStatus() == VolumeStatus.PRONTO);
    }

    @Test
    void deveMarcarPedidoComoRetiradoAtualizandoVolumes() {
        Pedido pedido = novoPedidoComVolume();
        pedido.getVolumes().get(0).setStatus(VolumeStatus.ALOCADO);
        pedido.marcarPronto();
        pedido.marcarRetirado();
        assertThat(pedido.getStatus()).isEqualTo(PedidoStatus.RETIRADO);
        assertThat(pedido.getPickedUpAt()).isNotNull();
        assertThat(pedido.getVolumes()).allMatch(v -> v.getStatus() == VolumeStatus.RETIRADO);
    }

    private Pedido novoPedidoBasico() {
        Pedido pedido = new Pedido();
        pedido.setCodigo("PED-TEST");
        pedido.setDestinatarioNome("Cliente Teste");
        pedido.setDestinatarioDocumento("12345678900");
        pedido.setDestinatarioTelefone("11999999999");
        pedido.setCanal(CanalPedido.MANUAL);
        return pedido;
    }

    private Pedido novoPedidoComVolume() {
        Pedido pedido = novoPedidoBasico();
        Volume volume = novoVolume();
        pedido.adicionarVolume(volume);
        return pedido;
    }

    private Volume novoVolume() {
        Volume volume = new Volume();
        volume.setEtiqueta("VOL-01");
        volume.setPeso(BigDecimal.valueOf(1.5));
        volume.setDimensoes("10x10x10");
        return volume;
    }
}
