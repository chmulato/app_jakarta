<%@ page contentType="text/html; charset=UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<!DOCTYPE html>
<html lang="pt-BR">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>Dashboard - Hub Operacional</title>
  <link href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.2/dist/css/bootstrap.min.css" rel="stylesheet">
  <link href="https://cdn.jsdelivr.net/npm/bootstrap-icons@1.11.0/font/bootstrap-icons.css" rel="stylesheet">
</head>
<body class="bg-light">
  <nav class="navbar navbar-expand-lg navbar-dark bg-primary">
    <div class="container-fluid">
      <a class="navbar-brand" href="${pageContext.request.contextPath}/dashboard">Hub Operacional</a>
      <div class="d-flex align-items-center text-white">
        <i class="bi bi-person-circle me-2"></i>
        <span>${usuarioLogado.nome}</span>
        <a class="btn btn-sm btn-outline-light ms-3" href="${pageContext.request.contextPath}/logout">Sair</a>
      </div>
    </div>
  </nav>

  <main class="container my-4">
    <c:if test="${not empty erro}">
      <div class="alert alert-danger">${erro}</div>
    </c:if>

    <section class="mb-4">
      <div class="row g-3">
        <div class="col-md-4">
          <div class="card shadow-sm border-0">
            <div class="card-body">
              <h6 class="text-muted">Pedidos recebidos</h6>
              <h2 class="fw-bold text-primary">${recebidosHoje}</h2>
            </div>
          </div>
        </div>
        <div class="col-md-4">
          <div class="card shadow-sm border-0">
            <div class="card-body">
              <h6 class="text-muted">Prontos para retirada</h6>
              <h2 class="fw-bold text-success">${prontos}</h2>
            </div>
          </div>
        </div>
        <div class="col-md-4">
          <div class="card shadow-sm border-0">
            <div class="card-body">
              <h6 class="text-muted">Pedidos retirados</h6>
              <h2 class="fw-bold text-secondary">${retirados}</h2>
            </div>
          </div>
        </div>
      </div>
    </section>

    <section class="row g-3">
      <div class="col-lg-8">
        <div class="card shadow-sm border-0 h-100">
          <div class="card-header bg-white py-3">
            <strong>Pedidos recentes</strong>
          </div>
          <div class="card-body">
            <c:choose>
              <c:when test="${not empty pedidosRecentes}">
                <div class="table-responsive">
                  <table class="table table-sm align-middle">
                    <thead>
                      <tr>
                        <th>Código</th>
                        <th>Destinatário</th>
                        <th>Status</th>
                        <th>Criado em</th>
                        <th></th>
                      </tr>
                    </thead>
                    <tbody>
                      <c:forEach var="pedido" items="${pedidosRecentes}">
                        <tr>
                          <td>${pedido.codigo}</td>
                          <td>${pedido.destinatarioNome}</td>
                          <td>
                            <span class="badge bg-light text-dark">${pedido.status}</span>
                          </td>
                          <td>${pedido.createdAt}</td>
                          <td>
                            <a class="btn btn-sm btn-outline-primary" href="${pageContext.request.contextPath}/pedidos/detalhe?id=${pedido.id}">
                              Detalhes
                            </a>
                          </td>
                        </tr>
                      </c:forEach>
                    </tbody>
                  </table>
                </div>
              </c:when>
              <c:otherwise>
                <p class="text-muted mb-0">Nenhum pedido recente.</p>
              </c:otherwise>
            </c:choose>
          </div>
        </div>
      </div>

      <div class="col-lg-4">
        <div class="card shadow-sm border-0 mb-3">
          <div class="card-header bg-white py-3">
            <strong>Ações rápidas</strong>
          </div>
          <div class="list-group list-group-flush">
            <a class="list-group-item list-group-item-action" href="${pageContext.request.contextPath}/recebimento/entrada">
              <i class="bi bi-plus-circle me-2"></i>Receber pedido
            </a>
            <a class="list-group-item list-group-item-action" href="${pageContext.request.contextPath}/pedidos/lista">
              <i class="bi bi-list-ul me-2"></i>Consultar pedidos
            </a>
            <a class="list-group-item list-group-item-action" href="${pageContext.request.contextPath}/retirada/retirar">
              <i class="bi bi-box-arrow-up-right me-2"></i>Retirada no balcão
            </a>
          </div>
        </div>

        <div class="card shadow-sm border-0">
          <div class="card-header bg-white py-3">
            <strong>Operadores recentes</strong>
          </div>
          <div class="card-body">
            <c:choose>
              <c:when test="${not empty usuariosRecentes}">
                <ul class="list-unstyled mb-0">
                  <c:forEach var="usuario" items="${usuariosRecentes}">
                    <li class="mb-2">
                      <strong>${usuario.nome}</strong>
                      <div class="text-muted small">${usuario.email}</div>
                    </li>
                  </c:forEach>
                </ul>
              </c:when>
              <c:otherwise>
                <p class="text-muted mb-0">Nenhum operador ativo.</p>
              </c:otherwise>
            </c:choose>
          </div>
        </div>
      </div>
    </section>
  </main>

  <script src="https://cdn.jsdelivr.net/npm/bootstrap@5.3.2/dist/js/bootstrap.bundle.min.js"></script>
</body>
</html>
