<%@ page contentType="text/html; charset=UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<!DOCTYPE html>
<html lang="pt-BR">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>Consulta de Pedidos</title>
  <link href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.2/dist/css/bootstrap.min.css" rel="stylesheet">
</head>
<body class="bg-light">
  <div class="container py-4">
    <div class="d-flex justify-content-between align-items-center mb-4">
      <div>
        <h1 class="h4">Pedidos</h1>
        <p class="text-muted mb-0">Filtre pedidos por status, período, destinatário ou canal.</p>
      </div>
      <a class="btn btn-outline-secondary" href="${pageContext.request.contextPath}/dashboard">Voltar</a>
    </div>

    <form method="get" class="card shadow-sm border-0 mb-4">
      <div class="card-body">
        <div class="row g-3 align-items-end">
          <div class="col-md-2">
            <label class="form-label">Status</label>
            <select class="form-select" name="status">
              <option value="">Todos</option>
              <c:forEach var="status" items="${statusList}">
                <option value="${status}" ${status == statusSelecionado ? 'selected' : ''}>${status}</option>
              </c:forEach>
            </select>
          </div>
          <div class="col-md-2">
            <label class="form-label">Canal</label>
            <select class="form-select" name="canal">
              <option value="">Todos</option>
              <c:forEach var="canal" items="${canais}">
                <option value="${canal}" ${canal == canalSelecionado ? 'selected' : ''}>${canal}</option>
              </c:forEach>
            </select>
          </div>
          <div class="col-md-2">
            <label class="form-label">Data início</label>
            <input type="date" class="form-control" name="dataInicio" value="${dataInicio}">
          </div>
          <div class="col-md-2">
            <label class="form-label">Data fim</label>
            <input type="date" class="form-control" name="dataFim" value="${dataFim}">
          </div>
          <div class="col-md-3">
            <label class="form-label">Destinatário</label>
            <input type="text" class="form-control" name="destinatario" value="${destinatario}">
          </div>
          <div class="col-md-1 d-grid">
            <button type="submit" class="btn btn-primary">Filtrar</button>
          </div>
        </div>
      </div>
    </form>

    <div class="card shadow-sm border-0">
      <div class="card-header bg-white py-3">
        <strong>Resultados</strong>
      </div>
      <div class="card-body">
        <c:choose>
          <c:when test="${empty pedidos}">
            <p class="text-muted mb-0">Nenhum pedido encontrado com os filtros informados.</p>
          </c:when>
          <c:otherwise>
            <div class="table-responsive">
              <table class="table table-sm align-middle">
                <thead>
                  <tr>
                    <th>Código</th>
                    <th>Destinatário</th>
                    <th>Status</th>
                    <th>Criado em</th>
                    <th>Volumes</th>
                    <th>Ações</th>
                  </tr>
                </thead>
                <tbody>
                  <c:forEach var="pedido" items="${pedidos}">
                    <tr>
                      <td>${pedido.codigo}</td>
                      <td>${pedido.destinatarioNome}</td>
                      <td><span class="badge bg-light text-dark">${pedido.status}</span></td>
                      <td>${pedido.createdAt}</td>
                      <td>${pedido.volumes.size()}</td>
                      <td class="d-flex gap-2">
                        <a class="btn btn-sm btn-outline-primary" href="${pageContext.request.contextPath}/pedidos/detalhe?id=${pedido.id}">Detalhar</a>
                        <a class="btn btn-sm btn-outline-success" href="${pageContext.request.contextPath}/triagem/alocar?pedidoId=${pedido.id}">Alocar</a>
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
  </div>
  <script src="https://cdn.jsdelivr.net/npm/bootstrap@5.3.2/dist/js/bootstrap.bundle.min.js"></script>
</body>
</html>
