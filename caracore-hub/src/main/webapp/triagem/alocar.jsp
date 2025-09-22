<%@ page contentType="text/html; charset=UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<!DOCTYPE html>
<html lang="pt-BR">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>Triagem e Alocação</title>
  <link href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.2/dist/css/bootstrap.min.css" rel="stylesheet">
</head>
<body class="bg-light">
  <div class="container py-4">
    <div class="d-flex justify-content-between align-items-center mb-4">
      <div>
        <h1 class="h4">Triagem e alocação</h1>
        <p class="text-muted mb-0">Confirme ou ajuste a posição dos volumes em estoque.</p>
      </div>
      <a class="btn btn-outline-secondary" href="${pageContext.request.contextPath}/pedidos/lista">Voltar</a>
    </div>

    <c:if test="${not empty erro}">
      <div class="alert alert-danger">${erro}</div>
    </c:if>
    <c:if test="${param.sucesso eq '1'}">
      <div class="alert alert-success">Alocação atualizada com sucesso.</div>
    </c:if>

    <c:if test="${not empty pedido}">
      <div class="card shadow-sm border-0 mb-4">
        <div class="card-body">
          <h5 class="card-title mb-3">Pedido ${pedido.codigo}</h5>
          <div class="row">
            <div class="col-md-4"><strong>Destinatário:</strong> ${pedido.destinatarioNome}</div>
            <div class="col-md-4"><strong>Documento:</strong> ${pedido.destinatarioDocumento}</div>
            <div class="col-md-4"><strong>Telefone:</strong> ${pedido.destinatarioTelefone}</div>
          </div>
        </div>
      </div>

      <div class="card shadow-sm border-0">
        <div class="card-header bg-white py-3">
          <div class="d-flex justify-content-between align-items-center">
            <strong>Volumes cadastrados</strong>
            <span class="badge bg-light text-dark">Sugestão: ${empty posicaoSugerida ? "consultar mapa" : posicaoSugerida}</span>
          </div>
        </div>
        <div class="card-body">
          <c:choose>
            <c:when test="${empty volumes}">
              <p class="text-muted mb-0">Nenhum volume vinculado ao pedido.</p>
            </c:when>
            <c:otherwise>
              <div class="table-responsive">
                <table class="table table-sm align-middle">
                  <thead>
                    <tr>
                      <th>Etiqueta</th>
                      <th>Peso</th>
                      <th>Dimensões</th>
                      <th>Posição atual</th>
                      <th>Alterar posição</th>
                    </tr>
                  </thead>
                  <tbody>
                    <c:forEach var="volume" items="${volumes}">
                      <tr>
                        <td>${volume.etiqueta}</td>
                        <td>${volume.peso}</td>
                        <td>${volume.dimensoes}</td>
                        <td>${empty volume.posicao ? "Não alocado" : volume.posicao.codigo}</td>
                        <td>
                          <form method="post" class="d-flex gap-2">
                            <input type="hidden" name="pedidoId" value="${pedido.id}">
                            <input type="hidden" name="volumeId" value="${volume.id}">
                            <select class="form-select form-select-sm" name="posicaoId">
                              <option value="">-- Selecionar --</option>
                              <c:forEach var="posicao" items="${posicoes}">
                                <c:set var="selected" value="${!empty volume.posicao and volume.posicao.id == posicao.id ? 'selected' : ''}" />
                                <option value="${posicao.id}" ${selected}>${posicao.codigo}</option>
                              </c:forEach>
                            </select>
                            <button type="submit" class="btn btn-sm btn-primary">Salvar</button>
                          </form>
                        </td>
                      </tr>
                    </c:forEach>
                  </tbody>
                </table>
              </div>
            </c:otherwise>
          </c:choose>
        </div>
      </div>
    </c:if>
  </div>
  <script src="https://cdn.jsdelivr.net/npm/bootstrap@5.3.2/dist/js/bootstrap.bundle.min.js"></script>
</body>
</html>
