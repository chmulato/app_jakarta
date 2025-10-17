<%@ page contentType="text/html; charset=UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<!DOCTYPE html>
<html lang="pt-BR">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Retirada de Pedidos</title>
    <link href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.2/dist/css/bootstrap.min.css" rel="stylesheet">
</head>
<body class="bg-light">
    <div class="container py-4">
        <div class="d-flex justify-content-between align-items-center mb-4">
            <div>
                <h1 class="h4">Retirada de pedidos</h1>
                <p class="text-muted mb-0">Localize pedidos prontos e confirme a retirada no balcão.</p>
            </div>
            <a class="btn btn-outline-secondary" href="${pageContext.request.contextPath}/dashboard">Dashboard</a>
        </div>

        <c:if test="${param.sucesso eq '1'}">
            <div class="alert alert-success alert-dismissible fade show" role="alert">
                Retirada confirmada com sucesso.
                <button type="button" class="btn-close" data-bs-dismiss="alert" aria-label="Fechar"></button>
            </div>
        </c:if>

        <c:if test="${not empty erro}">
            <div class="alert alert-danger alert-dismissible fade show" role="alert">
                ${erro}
                <button type="button" class="btn-close" data-bs-dismiss="alert" aria-label="Fechar"></button>
            </div>
        </c:if>

        <div class="card shadow-sm border-0 mb-4">
            <div class="card-header bg-white">
                <strong>Buscar pedido</strong>
            </div>
            <div class="card-body">
                <form method="post" class="row g-3 align-items-end">
                    <input type="hidden" name="acao" value="buscar">
                    <div class="col-md-4">
                        <label for="codigo" class="form-label">Codigo do pedido</label>
                        <input type="text" class="form-control" id="codigo" name="codigo" placeholder="Ex: PED-123" value="${param.codigo}">
                    </div>
                    <div class="col-md-4">
                        <label for="telefone" class="form-label">telefone do Destinatario</label>
                        <input type="text" class="form-control" id="telefone" name="telefone" placeholder="Somente números" value="${param.telefone}">
                        <div class="form-text">Informe Codigo <strong>ou</strong> telefone.</div>
                    </div>
                    <div class="col-md-4">
                        <button type="submit" class="btn btn-primary w-100">Buscar</button>
                    </div>
                </form>
            </div>
        </div>

        <c:if test="${not empty pedidosEncontrados}">
            <div class="card shadow-sm border-0 mb-4">
                <div class="card-header bg-white d-flex justify-content-between align-items-center">
                    <strong>Pedidos encontrados</strong>
                    <span class="badge bg-primary">${fn:length(pedidosEncontrados)}</span>
                </div>
                <div class="card-body p-0">
                    <div class="table-responsive">
                        <table class="table table-hover mb-0 align-middle">
                            <thead class="table-light">
                                <tr>
                                    <th>Codigo</th>
                                    <th>Destinatario</th>
                                    <th>Telefone</th>
                                    <th>Status</th>
                                    <th></th>
                                </tr>
                            </thead>
                            <tbody>
                                <c:forEach var="pedido" items="${pedidosEncontrados}">
                                    <tr>
                                        <td>${pedido.codigo}</td>
                                        <td>${pedido.destinatarioNome}</td>
                                        <td>${pedido.destinatarioTelefone}</td>
                                        <td>
                                            <span class="badge ${pedido.status eq 'PRONTO' ? 'bg-success' : 'bg-secondary'}">${pedido.status}</span>
                                        </td>
                                        <td class="text-end">
                                            <form method="post" class="d-inline">
                                                <input type="hidden" name="acao" value="buscar">
                                                <input type="hidden" name="codigo" value="${pedido.codigo}">
                                                <button type="submit" class="btn btn-sm btn-outline-primary">Ver detalhes</button>
                                            </form>
                                        </td>
                                    </tr>
                                </c:forEach>
                            </tbody>
                        </table>
                    </div>
                </div>
            </div>
        </c:if>

        <c:if test="${not empty pedidoSelecionado}">
            <div class="card shadow-sm border-0">
                <div class="card-header bg-white d-flex justify-content-between align-items-center">
                    <div>
                        <strong>Pedido selecionado</strong>
                        <span class="badge ${pedidoSelecionado.status eq 'PRONTO' ? 'bg-success' : 'bg-secondary'} ms-2">${pedidoSelecionado.status}</span>
                    </div>
                    <span class="text-muted small">Codigo: ${pedidoSelecionado.codigo}</span>
                </div>
                <div class="card-body">
                    <div class="row g-3">
                        <div class="col-md-4">
                            <div class="text-muted small">Destinatario</div>
                            <div class="fw-semibold">${pedidoSelecionado.destinatarioNome}</div>
                        </div>
                        <div class="col-md-4">
                            <div class="text-muted small">Telefone</div>
                            <div class="fw-semibold">${pedidoSelecionado.destinatarioTelefone}</div>
                        </div>
                        <div class="col-md-4">
                            <div class="text-muted small">Documento</div>
                            <div class="fw-semibold">${pedidoSelecionado.destinatarioDocumento}</div>
                        </div>
                    </div>
                    <hr>
                    <div class="row g-3">
                        <div class="col-lg-6">
                            <h2 class="h6">Volumes</h2>
                            <c:choose>
                                <c:when test="${empty pedidoSelecionado.volumes}">
                                    <p class="text-muted mb-0">Nenhum volume cadastrado.</p>
                                </c:when>
                                <c:otherwise>
                                    <ul class="list-group list-group-flush">
                                        <c:forEach var="volume" items="${pedidoSelecionado.volumes}">
                                            <li class="list-group-item">
                                                <div class="d-flex justify-content-between align-items-center">
                                                    <div>
                                                        <strong>${volume.etiqueta}</strong>
                                                        <div class="small text-muted">${volume.dimensoes} · ${volume.peso} kg</div>
                                                        <div class="small">Posicao: ${empty volume.posicao ? 'Nao alocado' : volume.posicao.codigo}</div>
                                                    </div>
                                                    <span class="badge bg-light text-dark">${volume.status}</span>
                                                </div>
                                            </li>
                                        </c:forEach>
                                    </ul>
                                </c:otherwise>
                            </c:choose>
                        </div>
                        <div class="col-lg-6">
                            <h2 class="h6">Historico de eventos</h2>
                            <c:choose>
                                <c:when test="${empty pedidoSelecionado.eventos}">
                                    <p class="text-muted mb-0">Nenhum evento registrado.</p>
                                </c:when>
                                <c:otherwise>
                                    <ul class="list-group list-group-flush">
                                        <c:forEach var="evento" items="${pedidoSelecionado.eventos}">
                                            <li class="list-group-item">
                                                <div class="fw-semibold">${evento.tipo}</div>
                                                <div class="small text-muted">${evento.createdAt} · ${evento.actor}</div>
                                                <c:if test="${not empty evento.payload}">
                                                    <div class="small">${evento.payload}</div>
                                                </c:if>
                                            </li>
                                        </c:forEach>
                                    </ul>
                                </c:otherwise>
                            </c:choose>
                        </div>
                    </div>
                    <hr>
                    <form method="post" class="d-flex justify-content-end">
                        <input type="hidden" name="acao" value="confirmar">
                        <input type="hidden" name="pedidoId" value="${pedidoSelecionado.id}">
                        <button type="submit" class="btn btn-success px-4" ${pedidoSelecionado.status ne 'PRONTO' ? 'disabled' : ''}>
                            Confirmar retirada
                        </button>
                    </form>
                    <c:if test="${pedidoSelecionado.status ne 'PRONTO'}">
                        <p class="text-warning small mt-2 mb-0">Somente pedidos no status PRONTO podem ser retirados.</p>
                    </c:if>
                </div>
            </div>
        </c:if>

        <c:if test="${empty pedidoSelecionado and empty pedidosEncontrados}">
            <div class="alert alert-info">Digite um Codigo ou telefone para localizar o pedido.</div>
        </c:if>
    </div>
    <script src="https://cdn.jsdelivr.net/npm/bootstrap@5.3.2/dist/js/bootstrap.bundle.min.js"></script>
</body>
</html>




