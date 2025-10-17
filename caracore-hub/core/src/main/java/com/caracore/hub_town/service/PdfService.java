package com.caracore.hub_town.service.pdf;

import com.caracore.hub_town.model.Pedido;
import com.caracore.hub_town.model.Volume;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class PdfService {
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    public byte[] gerarEtiquetaPedido(Pedido pedido) {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage(PDRectangle.A6);
            document.addPage(page);
            try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                float cursorY = page.getMediaBox().getHeight() - 36;
                cursorY = writeHeading(stream, page, cursorY, "Etiqueta de Pedido");
                cursorY = writeLine(stream, 36, cursorY, "Pedido: " + safe(pedido.getCodigo()), true);
                cursorY = writeLine(stream, 36, cursorY, "Destinatario: " + safe(pedido.getDestinatarioNome()), false);
                cursorY = writeLine(stream, 36, cursorY, "Telefone: " + safe(pedido.getDestinatarioTelefone()), false);
                cursorY = writeLine(stream, 36, cursorY, "Documento: " + safe(pedido.getDestinatarioDocumento()), false);
                cursorY -= 6;

                List<Volume> volumes = pedido.getVolumes();
                if (volumes.isEmpty()) {
                    cursorY = writeLine(stream, 36, cursorY, "Volumes: nenhum cadastrado", false);
                } else {
                    int limit = Math.min(volumes.size(), 3);
                    for (int i = 0; i < limit; i++) {
                        Volume volume = volumes.get(i);
                        String peso = volume.getPeso() != null ? volume.getPeso().stripTrailingZeros().toPlainString() + " kg" : "-";
                        String posicao = volume.getPosicao() != null ? safe(volume.getPosicao().getCodigo()) : "Nao alocado";
                        String resumoVol = String.format("Vol %d | %s | %s | %s", i + 1, safe(volume.getEtiqueta()), safe(volume.getDimensoes()), peso);
                        cursorY = writeLine(stream, 36, cursorY, resumoVol, false);
                        cursorY = writeLine(stream, 48, cursorY, "Posicao: " + posicao, false);
                    }
                    if (volumes.size() > limit) {
                        cursorY -= 6;
                        cursorY = writeLine(stream, 36, cursorY, String.format("+%d volumes adicionais", volumes.size() - limit), false);
                    }
                }
            }
            document.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new PdfGenerationException("Erro ao gerar etiqueta do pedido", e);
        }
    }

    public byte[] gerarComprovanteRetirada(Pedido pedido, String atendente) {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage(PDRectangle.A5);
            document.addPage(page);
            try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                float cursorY = page.getMediaBox().getHeight() - 48;
                cursorY = writeHeading(stream, page, cursorY, "Comprovante de Retirada");
                cursorY = writeLine(stream, 40, cursorY, "Pedido: " + safe(pedido.getCodigo()) + " (" + pedido.getStatus() + ")", true);
                cursorY = writeLine(stream, 40, cursorY, "Destinatario: " + safe(pedido.getDestinatarioNome()), false);
                cursorY = writeLine(stream, 40, cursorY, "Telefone: " + safe(pedido.getDestinatarioTelefone()), false);
                cursorY = writeLine(stream, 40, cursorY, "Documento: " + safe(pedido.getDestinatarioDocumento()), false);
                LocalDateTime retirada = pedido.getPickedUpAt() != null ? pedido.getPickedUpAt() : LocalDateTime.now();
                cursorY = writeLine(stream, 40, cursorY, "Retirado em: " + DATE_TIME_FORMATTER.format(retirada), false);
                cursorY = writeLine(stream, 40, cursorY, "Atendente: " + safe(atendente), false);
                cursorY -= 4;
                cursorY = writeLine(stream, 40, cursorY, "Volumes:", true);
                if (pedido.getVolumes().isEmpty()) {
                    cursorY = writeLine(stream, 52, cursorY, "Nenhum volume vinculado ao pedido.", false);
                } else {
                    for (Volume volume : pedido.getVolumes()) {
                        cursorY = writeLine(stream, 52, cursorY, "- " + safe(volume.getEtiqueta()) + " (" + volume.getStatus() + ")", false);
                    }
                }
                cursorY -= 16;
                writeLine(stream, 40, cursorY, "Assinatura do destinatario: ________________________________", false);
            }
            document.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new PdfGenerationException("Erro ao gerar comprovante de retirada", e);
        }
    }

    private float writeHeading(PDPageContentStream stream, PDPage page, float cursorY, String title) throws IOException {
        stream.beginText();
        stream.setFont(PDType1Font.HELVETICA_BOLD, 16);
        float textWidth = PDType1Font.HELVETICA_BOLD.getStringWidth(title) / 1000 * 16;
        float pageWidth = page.getMediaBox().getWidth();
        float startX = Math.max((pageWidth - textWidth) / 2, 36);
        stream.newLineAtOffset(startX, cursorY);
        stream.showText(title);
        stream.endText();
        return cursorY - 22;
    }

    private float writeLine(PDPageContentStream stream, float x, float cursorY, String text, boolean bold) throws IOException {
        stream.beginText();
        stream.setFont(bold ? PDType1Font.HELVETICA_BOLD : PDType1Font.HELVETICA, bold ? 12 : 11);
        stream.newLineAtOffset(x, cursorY);
        stream.showText(text);
        stream.endText();
        return cursorY - (bold ? 16 : 14);
    }

    private String safe(String value) {
        return value == null ? "-" : value;
    }
}

