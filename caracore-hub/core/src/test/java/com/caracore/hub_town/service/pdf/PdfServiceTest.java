package com.caracore.hub_town.service.pdf;

import com.caracore.hub_town.model.Pedido;
import com.caracore.hub_town.model.PedidoStatus;
import com.caracore.hub_town.model.Volume;
import com.caracore.hub_town.model.VolumeStatus;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class PdfServiceTest {
    private final PdfService pdfService = new PdfService();

    @Test
    @DisplayName("gerarEtiquetaPedido deve retornar bytes de um PDF valido")
    void gerarEtiquetaPedido() throws IOException {
        Pedido pedido = buildPedido();

        byte[] pdf = pdfService.gerarEtiquetaPedido(pedido);

        assertThat(pdf).isNotEmpty();
        try (PDDocument document = PDDocument.load(pdf)) {
            assertThat(document.getNumberOfPages()).isGreaterThan(0);
        }
    }

    @Test
    @DisplayName("gerarComprovanteRetirada deve retornar bytes de um PDF valido")
    void gerarComprovanteRetirada() throws IOException {
        Pedido pedido = buildPedido();
        pedido.setStatus(PedidoStatus.PRONTO);
        pedido.setPickedUpAt(LocalDateTime.of(2024, 1, 10, 14, 30));

        byte[] pdf = pdfService.gerarComprovanteRetirada(pedido, "Operador Teste");

        assertThat(pdf).isNotEmpty();
        try (PDDocument document = PDDocument.load(pdf)) {
            assertThat(document.getNumberOfPages()).isGreaterThan(0);
        }
    }

    private Pedido buildPedido() {
        Pedido pedido = new Pedido();
        pedido.setCodigo("PED-TESTE-001");
        pedido.setDestinatarioNome("Cliente Teste");
        pedido.setDestinatarioTelefone("11999990000");
        pedido.setDestinatarioDocumento("12345678900");
        pedido.setStatus(PedidoStatus.PRONTO);

        Volume volume1 = new Volume();
        volume1.setEtiqueta("VOL-001");
        volume1.setDimensoes("20x20x20");
        volume1.setPeso(new BigDecimal("1.50"));
        volume1.setStatus(VolumeStatus.PRONTO);
        pedido.adicionarVolume(volume1);

        Volume volume2 = new Volume();
        volume2.setEtiqueta("VOL-002");
        volume2.setDimensoes("25x25x15");
        volume2.setPeso(new BigDecimal("2.00"));
        volume2.setStatus(VolumeStatus.PRONTO);
        pedido.adicionarVolume(volume2);

        return pedido;
    }
}
