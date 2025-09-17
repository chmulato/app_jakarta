<%@ page contentType="text/html; charset=UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<!DOCTYPE html>
<html lang="pt-BR">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>Dashboard - Meu App</title>
  
  <!-- Bootstrap 5 CSS -->
  <link href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.2/dist/css/bootstrap.min.css" rel="stylesheet">
  <link href="https://cdn.jsdelivr.net/npm/bootstrap-icons@1.11.0/font/bootstrap-icons.css" rel="stylesheet">
  
  <style>
    .sidebar {
      background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
      color: white;
      min-height: 100vh;
    }
    .sidebar .nav-link {
      color: rgba(255,255,255,0.8);
      border-radius: 8px;
      margin: 2px 0;
    }
    .sidebar .nav-link:hover,
    .sidebar .nav-link.active {
      background: rgba(255,255,255,0.1);
      color: white;
    }
    .main-content {
      background: #f8f9fa;
      min-height: 100vh;
    }
    .card {
      border: none;
      border-radius: 15px;
      box-shadow: 0 5px 15px rgba(0,0,0,0.08);
    }
    .card-header {
      border-radius: 15px 15px 0 0 !important;
      background: white;
      border-bottom: 1px solid #eee;
    }
    .stat-card {
      transition: transform 0.2s;
    }
    .stat-card:hover {
      transform: translateY(-5px);
    }
    .user-avatar {
      width: 40px;
      height: 40px;
      background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
      border-radius: 50%;
      display: flex;
      align-items: center;
      justify-content: center;
      color: white;
      font-weight: bold;
    }
  </style>
</head>
<body>
  <div class="container-fluid">
    <div class="row">
      
      <!-- Sidebar -->
      <div class="col-md-3 col-lg-2 px-0">
        <div class="sidebar p-3">
          
          <!-- Logo/Brand -->
          <div class="text-center mb-4">
            <i class="bi bi-gear-fill display-4"></i>
            <h5 class="mt-2">Meu App</h5>
            <small class="opacity-75">Dashboard</small>
          </div>
          
          <!-- User Info -->
          <div class="text-center mb-4 p-3 bg-white bg-opacity-10 rounded">
            <div class="user-avatar mx-auto mb-2">
              ${usuarioLogado.nome.substring(0,1).toUpperCase()}
            </div>
            <h6 class="mb-1">${usuarioLogado.nome}</h6>
            <small class="opacity-75">${usuarioLogado.perfil.descricao}</small>
          </div>
          
          <!-- Navigation -->
          <nav class="nav flex-column">
            <a class="nav-link active" href="${pageContext.request.contextPath}/dashboard">
              <i class="bi bi-speedometer2 me-2"></i>Dashboard
            </a>
            <a class="nav-link" href="${pageContext.request.contextPath}/usuarios">
              <i class="bi bi-people me-2"></i>Usuários
            </a>
            <a class="nav-link" href="${pageContext.request.contextPath}/produtos">
              <i class="bi bi-box me-2"></i>Produtos
            </a>
            <a class="nav-link" href="${pageContext.request.contextPath}/relatorios">
              <i class="bi bi-graph-up me-2"></i>Relatórios
            </a>
            <a class="nav-link" href="${pageContext.request.contextPath}/configuracoes">
              <i class="bi bi-gear me-2"></i>Configurações
            </a>
            
            <hr class="my-3 opacity-50">
            
            <a class="nav-link" href="${pageContext.request.contextPath}/">
              <i class="bi bi-house me-2"></i>Site Principal
            </a>
            <a class="nav-link" href="${pageContext.request.contextPath}/logout">
              <i class="bi bi-box-arrow-right me-2"></i>Sair
            </a>
          </nav>
        </div>
      </div>
      
      <!-- Main Content -->
      <div class="col-md-9 col-lg-10">
        <div class="main-content p-4">
          
          <!-- Header -->
          <div class="d-flex justify-content-between align-items-center mb-4">
            <div>
              <h2 class="mb-1">Dashboard</h2>
              <p class="text-muted mb-0">Bem-vindo de volta, ${usuarioLogado.nome}!</p>
            </div>
            <div class="text-muted">
              <i class="bi bi-clock me-1"></i>
              <script>
                document.write(new Date().toLocaleDateString('pt-BR', {
                  weekday: 'long',
                  year: 'numeric',
                  month: 'long',
                  day: 'numeric'
                }));
              </script>
            </div>
          </div>
          
          <!-- Stats Cards -->
          <div class="row mb-4">
            <div class="col-lg-3 col-md-6 mb-3">
              <div class="card stat-card">
                <div class="card-body">
                  <div class="d-flex align-items-center">
                    <div class="flex-grow-1">
                      <h3 class="mb-1">${totalUsuarios}</h3>
                      <p class="text-muted mb-0">Total de Usuários</p>
                    </div>
                    <div class="text-primary">
                      <i class="bi bi-people display-6"></i>
                    </div>
                  </div>
                </div>
              </div>
            </div>
            
            <div class="col-lg-3 col-md-6 mb-3">
              <div class="card stat-card">
                <div class="card-body">
                  <div class="d-flex align-items-center">
                    <div class="flex-grow-1">
                      <h3 class="mb-1">5</h3>
                      <p class="text-muted mb-0">Produtos Ativos</p>
                    </div>
                    <div class="text-success">
                      <i class="bi bi-box display-6"></i>
                    </div>
                  </div>
                </div>
              </div>
            </div>
            
            <div class="col-lg-3 col-md-6 mb-3">
              <div class="card stat-card">
                <div class="card-body">
                  <div class="d-flex align-items-center">
                    <div class="flex-grow-1">
                      <h3 class="mb-1">98%</h3>
                      <p class="text-muted mb-0">Uptime Sistema</p>
                    </div>
                    <div class="text-info">
                      <i class="bi bi-graph-up display-6"></i>
                    </div>
                  </div>
                </div>
              </div>
            </div>
            
            <div class="col-lg-3 col-md-6 mb-3">
              <div class="card stat-card">
                <div class="card-body">
                  <div class="d-flex align-items-center">
                    <div class="flex-grow-1">
                      <h3 class="mb-1">24/7</h3>
                      <p class="text-muted mb-0">Suporte Online</p>
                    </div>
                    <div class="text-warning">
                      <i class="bi bi-headset display-6"></i>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>
          
          <!-- Recent Users -->
          <div class="row">
            <div class="col-lg-8">
              <div class="card">
                <div class="card-header">
                  <h5 class="mb-0">
                    <i class="bi bi-people me-2"></i>Usuários Recentes
                  </h5>
                </div>
                <div class="card-body">
                  <c:choose>
                    <c:when test="${not empty usuariosRecentes}">
                      <div class="table-responsive">
                        <table class="table table-hover">
                          <thead>
                            <tr>
                              <th>Nome</th>
                              <th>Email</th>
                              <th>Perfil</th>
                              <th>Status</th>
                            </tr>
                          </thead>
                          <tbody>
                            <c:forEach var="usuario" items="${usuariosRecentes}">
                              <tr>
                                <td>
                                  <div class="d-flex align-items-center">
                                    <div class="user-avatar me-3" style="width: 30px; height: 30px; font-size: 12px;">
                                      ${usuario.nome.substring(0,1).toUpperCase()}
                                    </div>
                                    ${usuario.nome}
                                  </div>
                                </td>
                                <td>${usuario.email}</td>
                                <td>
                                  <span class="badge ${usuario.perfil == 'ADMIN' ? 'bg-danger' : 'bg-primary'}">
                                    ${usuario.perfil.descricao}
                                  </span>
                                </td>
                                <td>
                                  <c:choose>
                                    <c:when test="${usuario.ativo}">
                                      <span class="badge bg-success">Ativo</span>
                                    </c:when>
                                    <c:otherwise>
                                      <span class="badge bg-secondary">Inativo</span>
                                    </c:otherwise>
                                  </c:choose>
                                </td>
                              </tr>
                            </c:forEach>
                          </tbody>
                        </table>
                      </div>
                    </c:when>
                    <c:otherwise>
                      <div class="text-center py-4">
                        <i class="bi bi-people display-4 text-muted"></i>
                        <p class="text-muted mt-2">Nenhum usuário encontrado</p>
                      </div>
                    </c:otherwise>
                  </c:choose>
                </div>
              </div>
            </div>
            
            <!-- Quick Actions -->
            <div class="col-lg-4">
              <div class="card">
                <div class="card-header">
                  <h5 class="mb-0">
                    <i class="bi bi-lightning me-2"></i>Ações Rápidas
                  </h5>
                </div>
                <div class="card-body">
                  <div class="d-grid gap-2">
                    <a href="${pageContext.request.contextPath}/usuarios" class="btn btn-outline-primary">
                      <i class="bi bi-person-plus me-2"></i>Novo Usuário
                    </a>
                    <a href="${pageContext.request.contextPath}/produtos" class="btn btn-outline-success">
                      <i class="bi bi-plus-circle me-2"></i>Novo Produto
                    </a>
                    <a href="${pageContext.request.contextPath}/relatorios" class="btn btn-outline-info">
                      <i class="bi bi-file-earmark-text me-2"></i>Gerar Relatório
                    </a>
                    <a href="${pageContext.request.contextPath}/configuracoes" class="btn btn-outline-warning">
                      <i class="bi bi-gear me-2"></i>Configurações
                    </a>
                  </div>
                </div>
              </div>
              
              <!-- System Info -->
              <div class="card mt-3">
                <div class="card-header">
                  <h5 class="mb-0">
                    <i class="bi bi-info-circle me-2"></i>Sistema
                  </h5>
                </div>
                <div class="card-body">
                  <small class="text-muted">
                    <div class="mb-2">
                      <i class="bi bi-server me-1"></i>
                      <strong>JVM:</strong> <%= System.getProperty("java.version") %>
                    </div>
                    <div class="mb-2">
                      <i class="bi bi-database me-1"></i>
                      <strong>Banco:</strong> PostgreSQL
                    </div>
                    <div class="mb-2">
                      <i class="bi bi-clock me-1"></i>
                      <strong>Uptime:</strong> 
                      <script>
                        document.write(Math.floor(performance.now() / 1000) + 's');
                      </script>
                    </div>
                  </small>
                </div>
              </div>
            </div>
          </div>
          
        </div>
      </div>
    </div>
  </div>

  <!-- Bootstrap 5 JS -->
  <script src="https://cdn.jsdelivr.net/npm/bootstrap@5.3.2/dist/js/bootstrap.bundle.min.js"></script>
</body>
</html>