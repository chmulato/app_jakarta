package com.caracore.hub_town.api;

import com.caracore.hub_town.model.CanalPedido;
import com.caracore.hub_town.model.Pedido;
import com.caracore.hub_town.model.PedidoStatus;
import com.caracore.hub_town.model.Volume;
import com.caracore.hub_town.service.PedidoService;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Path("/pedidos")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class PedidoResource {
    private final PedidoService pedidoService = new PedidoService();

    @POST
    public Response criarPedido(PedidoCreateRequest request) {
        try {
            validarRequest(request);
            Pedido pedido = new Pedido();
            pedido.setCodigo(request.codigo);
            pedido.setDestinatarioNome(request.destinatarioNome);
            pedido.setDestinatarioDocumento(request.destinatarioDocumento);
            pedido.setDestinatarioTelefone(request.destinatarioTelefone);
            pedido.setCanal(Optional.ofNullable(request.canal).map(String::toUpperCase).map(CanalPedido::valueOf).orElse(CanalPedido.MANUAL));
            List<Volume> volumes = request.volumes.stream().map(dto -> {
                Volume volume = new Volume();
                volume.setEtiqueta(dto.etiqueta);
                if (dto.peso != null) {
                    volume.setPeso(dto.peso);
                }
                volume.setDimensoes(dto.dimensoes);
                return volume;
            }).collect(Collectors.toList());
            Pedido salvo = pedidoService.registrarPedidoManual(pedido, volumes, request.actor != null ? request.actor : "api");
            return Response.status(Response.Status.CREATED).entity(PedidoResponse.fromEntity(salvo)).build();
        } catch (IllegalArgumentException ex) {
            return Response.status(Response.Status.BAD_REQUEST).entity(new ErrorResponse(ex.getMessage())).build();
        } catch (Exception ex) {
            return Response.serverError().entity(new ErrorResponse("Erro ao criar pedido: " + ex.getMessage())).build();
        }
    }

    @GET
    public Response listarPedidos(@QueryParam("status") String status,
                                  @QueryParam("dataInicio") String dataInicio,
                                  @QueryParam("dataFim") String dataFim,
                                  @QueryParam("destinatario") String destinatario,
                                  @QueryParam("canal") String canal) {
        PedidoStatus statusFiltro = parseStatus(status);
        CanalPedido canalFiltro = parseCanal(canal);
        LocalDate inicio = parseData(dataInicio);
        LocalDate fim = parseData(dataFim);
        List<Pedido> pedidos = pedidoService.consultar(statusFiltro, inicio, fim, destinatario, canalFiltro);
        List<PedidoResponse> resposta = pedidos.stream().map(PedidoResponse::fromEntity).collect(Collectors.toList());
        return Response.ok(resposta).build();
    }

    @POST
    @Path("/{id}/ready")
    public Response marcarComoPronto(@PathParam("id") Long id) {
        try {
            Pedido pedido = pedidoService.marcarComoPronto(id, "api");
            return Response.ok(PedidoResponse.fromEntity(pedido)).build();
        } catch (Exception ex) {
            return Response.status(Response.Status.BAD_REQUEST).entity(new ErrorResponse(ex.getMessage())).build();
        }
    }

    @POST
    @Path("/{id}/pickup")
    public Response confirmarRetirada(@PathParam("id") Long id) {
        try {
            Pedido pedido = pedidoService.registrarRetirada(id, "api");
            return Response.ok(PedidoResponse.fromEntity(pedido)).build();
        } catch (Exception ex) {
            return Response.status(Response.Status.BAD_REQUEST).entity(new ErrorResponse(ex.getMessage())).build();
        }
    }

    private void validarRequest(PedidoCreateRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Payload obrigatório");
        }
        if (isBlank(request.destinatarioNome) || isBlank(request.destinatarioDocumento) || isBlank(request.destinatarioTelefone)) {
            throw new IllegalArgumentException("Dados do destinatário são obrigatórios");
        }
        if (request.volumes == null || request.volumes.isEmpty()) {
            throw new IllegalArgumentException("Informe ao menos um volume");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private PedidoStatus parseStatus(String value) {
        if (isBlank(value)) return null;
        try {
            return PedidoStatus.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private CanalPedido parseCanal(String value) {
        if (isBlank(value)) return null;
        try {
            return CanalPedido.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private LocalDate parseData(String value) {
        if (isBlank(value)) return null;
        try {
            return LocalDate.parse(value);
        } catch (Exception ex) {
            return null;
        }
    }

    public static class PedidoCreateRequest {
        public String codigo;
        public String destinatarioNome;
        public String destinatarioDocumento;
        public String destinatarioTelefone;
        public String canal;
        public String actor;
        public List<VolumeRequest> volumes = new ArrayList<>();
    }

    public static class VolumeRequest {
        public String etiqueta;
        public BigDecimal peso;
        public String dimensoes;
    }

    public static class PedidoResponse {
        public Long id;
        public String codigo;
        public String destinatarioNome;
        public String destinatarioDocumento;
        public String destinatarioTelefone;
        public String status;
        public String canal;
        public String createdAt;
        public String readyAt;
        public String pickedUpAt;
        public List<VolumeResponse> volumes;

        public static PedidoResponse fromEntity(Pedido pedido) {
            PedidoResponse response = new PedidoResponse();
            response.id = pedido.getId();
            response.codigo = pedido.getCodigo();
            response.destinatarioNome = pedido.getDestinatarioNome();
            response.destinatarioDocumento = pedido.getDestinatarioDocumento();
            response.destinatarioTelefone = pedido.getDestinatarioTelefone();
            response.status = pedido.getStatus().name();
            response.canal = pedido.getCanal() != null ? pedido.getCanal().name() : null;
            response.createdAt = pedido.getCreatedAt() != null ? pedido.getCreatedAt().toString() : null;
            response.readyAt = pedido.getReadyAt() != null ? pedido.getReadyAt().toString() : null;
            response.pickedUpAt = pedido.getPickedUpAt() != null ? pedido.getPickedUpAt().toString() : null;
            response.volumes = pedido.getVolumes().stream().map(VolumeResponse::fromEntity).collect(Collectors.toList());
            return response;
        }
    }

    public static class VolumeResponse {
        public Long id;
        public String etiqueta;
        public BigDecimal peso;
        public String dimensoes;
        public String status;
        public String posicao;

        public static VolumeResponse fromEntity(Volume volume) {
            VolumeResponse response = new VolumeResponse();
            response.id = volume.getId();
            response.etiqueta = volume.getEtiqueta();
            response.peso = volume.getPeso();
            response.dimensoes = volume.getDimensoes();
            response.status = volume.getStatus().name();
            response.posicao = volume.getPosicao() != null ? volume.getPosicao().getCodigo() : null;
            return response;
        }
    }

    public static class ErrorResponse {
        public final String mensagem;
        public ErrorResponse(String mensagem) { this.mensagem = mensagem; }
    }
}
