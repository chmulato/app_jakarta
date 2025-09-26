<%@ page contentType="text/html; charset=UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<!DOCTYPE html>
<html lang="pt-BR">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>Recebimento de Pedido</title>
  <link href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.2/dist/css/bootstrap.min.css" rel="stylesheet">
</head>
<body class="bg-light">
  <div class="container py-4">
    <div class="d-flex justify-content-between align-items-center mb-4">
      <div>
        <h1 class="h4">Receber pedido</h1>
        <p class="text-muted mb-0">Cadastre um pedido manualmente e seus volumes.</p>
      </div>
      <a class="btn btn-outline-secondary" href="${pageContext.request.contextPath}/dashboard">Voltar</a>
    </div>

    <c:if test="${not empty erro}">
      <div class="alert alert-danger">${erro}</div>
    </c:if>

    <form method="post" class="card shadow-sm border-0">
      <div class="card-body">
        <h6 class="text-uppercase text-muted">Dados do destinatário</h6>
        <div class="row g-3 mb-4">
          <div class="col-md-4">
            <label class="form-label">Código do pedido</label>
            <input type="text" class="form-control" name="codigoPedido" placeholder="Opcional">
          </div>
          <div class="col-md-4">
            <label class="form-label">Nome completo *</label>
            <input type="text" class="form-control" name="destinatarioNome" required>
          </div>
          <div class="col-md-2">
            <label class="form-label">Documento *</label>
            <input type="text" class="form-control" name="destinatarioDocumento" required>
          </div>
          <div class="col-md-2">
            <label class="form-label">Telefone *</label>
            <input type="text" class="form-control" name="destinatarioTelefone" required>
          </div>
        </div>

        <div class="mb-3">
          <span class="badge bg-light text-dark">
            Posição sugerida: <strong>${empty posicaoSugerida ? "consultar triagem" : posicaoSugerida}</strong>
          </span>
        </div>

        <h6 class="text-uppercase text-muted">Volumes</h6>
        <div id="volumes-container" class="row g-3 mb-3">
          <div class="col-12">
            <div class="row g-3 align-items-end volume-item">
              <div class="col-md-4">
                <label class="form-label">Etiqueta</label>
                <input type="text" name="volumeEtiqueta" class="form-control" placeholder="Gerado automaticamente se vazio">
              </div>
              <div class="col-md-3">
                <label class="form-label">Peso (kg)</label>
                <input type="text" name="volumePeso" class="form-control">
              </div>
              <div class="col-md-4">
                <label class="form-label">Dimensões</label>
                <input type="text" name="volumeDimensao" class="form-control" placeholder="L x A x P">
              </div>
              <div class="col-md-1 d-grid">
                <button type="button" class="btn btn-outline-danger" onclick="removerVolume(this)">
                  &times;
                </button>
              </div>
            </div>
          </div>
        </div>
        <div class="mb-4">
          <button type="button" class="btn btn-outline-primary" onclick="adicionarVolume()">Adicionar volume</button>
        </div>

        <button type="submit" class="btn btn-primary">Registrar pedido</button>
      </div>
    </form>
  </div>

  <script>
    function adicionarVolume() {
      const container = document.getElementById('volumes-container');
      const template = container.querySelector('.volume-item');
      const clone = template.cloneNode(true);
      clone.querySelectorAll('input').forEach(input => input.value = '');
      container.appendChild(clone);
    }

    function removerVolume(button) {
      const container = document.getElementById('volumes-container');
      if (container.querySelectorAll('.volume-item').length === 1) {
        button.closest('.volume-item').querySelectorAll('input').forEach(input => input.value = '');
        return;
      }
      button.closest('.volume-item').remove();
    }
  </script>
  <script src="https://cdn.jsdelivr.net/npm/bootstrap@5.3.2/dist/js/bootstrap.bundle.min.js"></script>
</body>
</html>
