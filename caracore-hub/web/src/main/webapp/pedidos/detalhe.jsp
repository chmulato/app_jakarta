<%@ page contentType="text/html; charset=UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<!DOCTYPE html>
<html lang="pt-BR">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>Detalhe do Pedido</title>
  <link href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.2/dist/css/bootstrap.min.css" rel="stylesheet">
</head>
<body class="bg-light">
  <div class="container py-4">
    <div class="d-flex justify-content-between align-items-center mb-4">
      <div>
        <h1 class="h4">Detalhe do pedido</h1>
        <p class="text-muted mb-0">Visualize dados, volumes e histórico.</p>
      </div>
      <a class="btn btn-outline-secondary" href="${pageContext.request.contextPath}/pedidos/lista">Voltar</a>
    </div>

    <c:if test="${not empty erro}">
      <div class="alert alert-danger">${erro}</div>
    </c:if>
    <c:if test="${param.sucesso eq '1'}">
      <div class="alert alert-success">Pedido atualizado com sucesso.</div>
    </c:if>

    <c:if test="${empty pedido}">
      <div class="alert alert-warning">Pedido não encontrado.</div>
    </c:if>

    <c:if test="${not empty pedido}">
      <div class="card shadow-sm border-0 mb-4">
        <div class="card-body">
          <div class="row">
            <div class="col-md-3"><strong>Código:</strong> ${pedido.codigo}</div>
            <div class="col-md-3"><strong>Status:</strong> ${pedido.status}</div>
            <div class="col-md-3"><strong>Criado em:</strong> ${pedido.createdAt}</div>
            <div class="col-md-3"><strong>Canal:</strong> ${pedido.canal}</div>
          </div>
          <div class="row mt-3">
            <div class="col-md-4"><strong>Destinatário:</strong> ${pedido.destinatarioNome}</div>
            <div class="col-md-4"><strong>Documento:</strong> ${pedido.destinatarioDocumento}</div>
            <div class="col-md-4"><strong>Telefone:</strong> ${pedido.destinatarioTelefone}</div>
          </div>
        </div>
      </div>

      <div class="d-flex gap-2 mb-4">
        <form method="post">
          <input type="hidden" name="id" value="${pedido.id}">
          <input type="hidden" name="acao" value="pronto">
          <button type="submit" class="btn btn-success" ${pedido.status ne 'RECEBIDO' ? 'disabled' : ''}>Marcar como pronto</button>
        </form>
        <form method="post">
          <input type="hidden" name="id" value="${pedido.id}">
          <input type="hidden" name="acao" value="retirar">
          <button type="submit" class="btn btn-primary" ${pedido.status ne 'PRONTO' ? 'disabled' : ''}>Confirmar retirada</button>
        </form>
      </div>

      <div class="row g-3">
        <div class="col-lg-6">
          <div class="card shadow-sm border-0 h-100">
            <div class="card-header bg-white py-3"><strong>Volumes</strong></div>
            <div class="card-body">
              <c:choose>
                <c:when test="${empty pedido.volumes}">
                  <p class="text-muted mb-0">Nenhum volume cadastrado.</p>
                </c:when>
                <c:otherwise>
                  <ul class="list-group list-group-flush">
                    <c:forEach var="volume" items="${pedido.volumes}">
                      <li class="list-group-item d-flex justify-content-between align-items-center">
                        <div>
                          <strong>${volume.etiqueta}</strong>
                          <div class="small text-muted">${volume.dimensoes} · ${volume.peso} kg</div>
                          <div class="small">Posição: ${empty volume.posicao ? 'Não alocado' : volume.posicao.codigo}</div>
                        </div>
                        <span class="badge bg-light text-dark">${volume.status}</span>
                      </li>
                    </c:forEach>
                  </ul>
                </c:otherwise>
              </c:choose>
            </div>
          </div>
        </div>

        <div class="col-lg-6">
          <div class="card shadow-sm border-0 h-100">
            <div class="card-header bg-white py-3"><strong>Eventos</strong></div>
            <div class="card-body">
              <c:choose>
                <c:when test="${empty pedido.eventos}">
                  <p class="text-muted mb-0">Nenhum evento registrado.</p>
                </c:when>
                <c:otherwise>
                  <ul class="timeline list-unstyled">
                    <c:forEach var="evento" items="${pedido.eventos}">
                      <li class="mb-3">
                        <strong>${evento.tipo}</strong>
                        <div class="small text-muted">${evento.createdAt} · ${evento.actor}</div>
                        <div class="small">${evento.payload}</div>
                      </li>
                    </c:forEach>
                  </ul>
                </c:otherwise>
              </c:choose>
            </div>
          </div>
        </div>
      </div>
    </c:if>
  </div>
  <script src="https://cdn.jsdelivr.net/npm/bootstrap@5.3.2/dist/js/bootstrap.bundle.min.js"></script>
</body>
</html>
