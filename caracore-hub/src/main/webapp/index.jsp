<%@ page contentType="text/html; charset=UTF-8" %>
<!DOCTYPE html>
<html lang="pt-BR">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>Meu App - Sistema de Gestão</title>
  
  <!-- Bootstrap 5 CSS -->
  <link href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.2/dist/css/bootstrap.min.css" rel="stylesheet">
  <link href="https://cdn.jsdelivr.net/npm/bootstrap-icons@1.11.0/font/bootstrap-icons.css" rel="stylesheet">
  
  <style>
    .hero-section {
      background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
      color: white;
      padding: 4rem 0;
    }
    .feature-card {
      transition: transform 0.2s;
    }
    .feature-card:hover {
      transform: translateY(-5px);
    }
    .status-badge {
      font-size: 0.85rem;
    }
  </style>
</head>
<body>
  <!-- Navbar -->
  <nav class="navbar navbar-expand-lg navbar-dark bg-dark">
    <div class="container">
      <a class="navbar-brand" href="#"><i class="bi bi-gear-fill me-2"></i>Meu App</a>
      <button class="navbar-toggler" type="button" data-bs-toggle="collapse" data-bs-target="#navbarNav">
        <span class="navbar-toggler-icon"></span>
      </button>
      <div class="collapse navbar-collapse" id="navbarNav">
        <ul class="navbar-nav ms-auto">
          <li class="nav-item">
            <a class="nav-link" href="#recursos"><i class="bi bi-grid me-1"></i>Recursos</a>
          </li>
          <li class="nav-item">
            <a class="nav-link" href="#sobre"><i class="bi bi-info-circle me-1"></i>Sobre</a>
          </li>
          <li class="nav-item">
            <a class="nav-link btn btn-outline-light ms-2 px-3" href="${pageContext.request.contextPath}/login">
              <i class="bi bi-box-arrow-in-right me-1"></i>Entrar
            </a>
          </li>
        </ul>
      </div>
    </div>
  </nav>

  <!-- Hero Section -->
  <section class="hero-section">
    <div class="container">
      <div class="row align-items-center">
        <div class="col-lg-8">
          <h1 class="display-4 fw-bold mb-3">Sistema de Gestão Empresarial</h1>
          <p class="lead mb-4">
            Plataforma completa para gerenciar usuários, produtos e relatórios.
            Tecnologia Java Enterprise com design moderno e responsivo.
          </p>
          <div class="d-flex flex-wrap gap-3 mb-4">
            <a href="${pageContext.request.contextPath}/login" class="btn btn-light btn-lg">
              <i class="bi bi-box-arrow-in-right me-2"></i>Acessar Sistema
            </a>
            <a href="#recursos" class="btn btn-outline-light btn-lg">
              <i class="bi bi-play-circle me-2"></i>Ver Demonstração
            </a>
          </div>
          <div class="d-flex flex-wrap gap-2">
            <span class="badge bg-success status-badge">
              <i class="bi bi-check-circle me-1"></i>Sistema Online
            </span>
            <span class="badge bg-info status-badge">
              <i class="bi bi-clock me-1"></i><%= new java.text.SimpleDateFormat("dd/MM/yyyy HH:mm:ss").format(new java.util.Date()) %>
            </span>
            <span class="badge bg-warning text-dark status-badge">
              <i class="bi bi-cpu me-1"></i>Java <%= System.getProperty("java.version") %>
            </span>
          </div>
        </div>
        <div class="col-lg-4 text-center">
          <div class="d-flex flex-column align-items-center">
            <i class="bi bi-shield-check display-1 opacity-75 mb-3"></i>
            <div class="text-center">
              <h5>Acesso Seguro</h5>
              <p class="opacity-75 mb-0">Autenticação com BCrypt</p>
            </div>
          </div>
        </div>
      </div>
    </div>
  </section>

  <!-- Features Section -->
  <section class="py-5" id="recursos">
    <div class="container">
      <div class="row text-center mb-5">
        <div class="col-lg-8 mx-auto">
          <h2 class="fw-bold">Recursos do Sistema</h2>
          <p class="text-muted">Tecnologias enterprise em uma plataforma moderna e intuitiva</p>
        </div>
      </div>
      
      <div class="row g-4">
        <div class="col-md-6 col-lg-4">
          <div class="card h-100 feature-card border-0 shadow-sm">
            <div class="card-body text-center p-4">
              <div class="bg-primary bg-opacity-10 rounded-circle d-inline-flex align-items-center justify-content-center mb-3" style="width: 60px; height: 60px;">
                <i class="bi bi-people text-primary fs-4"></i>
              </div>
              <h5 class="card-title">Gestão de Usuários</h5>
              <p class="card-text text-muted">Sistema completo de autenticação com perfis e permissões.</p>
            </div>
          </div>
        </div>
        
        <div class="col-md-6 col-lg-4">
          <div class="card h-100 feature-card border-0 shadow-sm">
            <div class="card-body text-center p-4">
              <div class="bg-success bg-opacity-10 rounded-circle d-inline-flex align-items-center justify-content-center mb-3" style="width: 60px; height: 60px;">
                <i class="bi bi-box text-success fs-4"></i>
              </div>
              <h5 class="card-title">Catálogo de Produtos</h5>
              <p class="card-text text-muted">Gerenciamento completo de produtos com validações e relatórios.</p>
            </div>
          </div>
        </div>
        
        <div class="col-md-6 col-lg-4">
          <div class="card h-100 feature-card border-0 shadow-sm">
            <div class="card-body text-center p-4">
              <div class="bg-info bg-opacity-10 rounded-circle d-inline-flex align-items-center justify-content-center mb-3" style="width: 60px; height: 60px;">
                <i class="bi bi-graph-up text-info fs-4"></i>
              </div>
              <h5 class="card-title">Dashboard Analítico</h5>
              <p class="card-text text-muted">Painel administrativo com métricas e estatísticas em tempo real.</p>
            </div>
          </div>
        </div>
        
        <div class="col-md-6 col-lg-4">
          <div class="card h-100 feature-card border-0 shadow-sm">
            <div class="card-body text-center p-4">
              <div class="bg-warning bg-opacity-10 rounded-circle d-inline-flex align-items-center justify-content-center mb-3" style="width: 60px; height: 60px;">
                <i class="bi bi-shield-check text-warning fs-4"></i>
              </div>
              <h5 class="card-title">Segurança Avançada</h5>
              <p class="card-text text-muted">Criptografia BCrypt, controle de sessão e auditoria completa.</p>
            </div>
          </div>
        </div>
        
        <div class="col-md-6 col-lg-4">
          <div class="card h-100 feature-card border-0 shadow-sm">
            <div class="card-body text-center p-4">
              <div class="bg-danger bg-opacity-10 rounded-circle d-inline-flex align-items-center justify-content-center mb-3" style="width: 60px; height: 60px;">
                <i class="bi bi-database text-danger fs-4"></i>
              </div>
              <h5 class="card-title">PostgreSQL Enterprise</h5>
              <p class="card-text text-muted">Banco robusto com pool de conexões e alta performance.</p>
            </div>
          </div>
        </div>
        
        <div class="col-md-6 col-lg-4">
          <div class="card h-100 feature-card border-0 shadow-sm">
            <div class="card-body text-center p-4">
              <div class="bg-secondary bg-opacity-10 rounded-circle d-inline-flex align-items-center justify-content-center mb-3" style="width: 60px; height: 60px;">
                <i class="bi bi-phone text-secondary fs-4"></i>
              </div>
              <h5 class="card-title">Design Responsivo</h5>
              <p class="card-text text-muted">Interface Bootstrap 5 otimizada para todos os dispositivos.</p>
            </div>
          </div>
        </div>
      </div>
    </div>
  </section>

  <!-- Sobre Section -->
  <section class="py-5 bg-light" id="sobre">
    <div class="container">
      <div class="row align-items-center">
        <div class="col-lg-6">
          <h2 class="fw-bold mb-4">Sobre o Sistema</h2>
          <p class="text-muted mb-4">
            Sistema de gestão empresarial desenvolvido com <strong>Java Enterprise</strong>, 
            utilizando as melhores práticas de desenvolvimento e arquitetura moderna.
          </p>
          <div class="row g-3">
            <div class="col-sm-6">
              <div class="d-flex align-items-center">
                <div class="bg-primary bg-opacity-10 rounded p-2 me-3">
                  <i class="bi bi-code text-primary"></i>
                </div>
                <div>
                  <h6 class="mb-0">Jakarta EE 9+</h6>
                  <small class="text-muted">Especificação enterprise</small>
                </div>
              </div>
            </div>
            <div class="col-sm-6">
              <div class="d-flex align-items-center">
                <div class="bg-success bg-opacity-10 rounded p-2 me-3">
                  <i class="bi bi-database text-success"></i>
                </div>
                <div>
                  <h6 class="mb-0">JPA + Hibernate</h6>
                  <small class="text-muted">ORM moderno</small>
                </div>
              </div>
            </div>
            <div class="col-sm-6">
              <div class="d-flex align-items-center">
                <div class="bg-info bg-opacity-10 rounded p-2 me-3">
                  <i class="bi bi-check-circle text-info"></i>
                </div>
                <div>
                  <h6 class="mb-0">85% Coverage</h6>
                  <small class="text-muted">Testes automatizados</small>
                </div>
              </div>
            </div>
            <div class="col-sm-6">
              <div class="d-flex align-items-center">
                <div class="bg-warning bg-opacity-10 rounded p-2 me-3">
                  <i class="bi bi-lightning text-warning"></i>
                </div>
                <div>
                  <h6 class="mb-0">Docker Ready</h6>
                  <small class="text-muted">Containerização</small>
                </div>
              </div>
            </div>
          </div>
          <div class="mt-4">
            <a href="${pageContext.request.contextPath}/login" class="btn btn-primary btn-lg">
              <i class="bi bi-arrow-right me-2"></i>Começar Agora
            </a>
          </div>
        </div>
        <div class="col-lg-6 text-center">
          <div class="row g-3">
            <div class="col-6">
              <div class="card border-0 shadow-sm">
                <div class="card-body text-center py-4">
                  <i class="bi bi-people display-6 text-primary mb-2"></i>
                  <h3 class="mb-1">31</h3>
                  <small class="text-muted">Testes Automatizados</small>
                </div>
              </div>
            </div>
            <div class="col-6">
              <div class="card border-0 shadow-sm">
                <div class="card-body text-center py-4">
                  <i class="bi bi-shield-check display-6 text-success mb-2"></i>
                  <h3 class="mb-1">100%</h3>
                  <small class="text-muted">Seguro</small>
                </div>
              </div>
            </div>
            <div class="col-6">
              <div class="card border-0 shadow-sm">
                <div class="card-body text-center py-4">
                  <i class="bi bi-graph-up display-6 text-info mb-2"></i>
                  <h3 class="mb-1">24/7</h3>
                  <small class="text-muted">Disponibilidade</small>
                </div>
              </div>
            </div>
            <div class="col-6">
              <div class="card border-0 shadow-sm">
                <div class="card-body text-center py-4">
                  <i class="bi bi-rocket display-6 text-warning mb-2"></i>
                  <h3 class="mb-1">Fast</h3>
                  <small class="text-muted">Performance</small>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </section>

  <!-- CTA Section -->
  <section class="py-5 bg-primary text-white">
    <div class="container text-center">
      <div class="row justify-content-center">
        <div class="col-lg-8">
          <h2 class="fw-bold mb-3">Pronto para começar?</h2>
          <p class="lead mb-4">
            Acesse o sistema com suas credenciais ou utilize as credenciais de demonstração.
          </p>
          <div class="d-flex flex-wrap gap-3 justify-content-center">
            <a href="${pageContext.request.contextPath}/login" class="btn btn-light btn-lg">
              <i class="bi bi-box-arrow-in-right me-2"></i>Fazer Login
            </a>
            <div class="text-start">
              <small class="opacity-75">
                <strong>Demo:</strong> admin@meuapp.com<br>
                <strong>Senha:</strong> Admin@123
              </small>
            </div>
          </div>
        </div>
      </div>
    </div>
  </section>

  <!-- Footer -->
  <footer class="bg-dark text-light py-4">
    <div class="container">
      <div class="row">
        <div class="col-md-6">
          <h6 class="fw-bold mb-3">
            <i class="bi bi-gear-fill me-2"></i>Meu App
          </h6>
          <p class="text-muted mb-3">Sistema de gestão empresarial moderno e seguro.</p>
          <div class="d-flex gap-2">
            <small class="badge bg-secondary">Java 11</small>
            <small class="badge bg-secondary">Jakarta EE</small>
            <small class="badge bg-secondary">PostgreSQL</small>
            <small class="badge bg-secondary">Bootstrap 5</small>
          </div>
        </div>
        <div class="col-md-6 text-md-end">
          <h6 class="fw-bold mb-3">Links Rápidos</h6>
          <div class="d-flex flex-column align-items-md-end gap-2">
            <a href="${pageContext.request.contextPath}/login" class="text-decoration-none text-light">
              <i class="bi bi-box-arrow-in-right me-1"></i>Acessar Sistema
            </a>
            <a href="#recursos" class="text-decoration-none text-muted">
              <i class="bi bi-grid me-1"></i>Recursos
            </a>
            <a href="#sobre" class="text-decoration-none text-muted">
              <i class="bi bi-info-circle me-1"></i>Sobre
            </a>
          </div>
          <hr class="my-3 opacity-25">
          <small class="text-muted">
            <i class="bi bi-code-slash me-1"></i>
            Desenvolvido com Java Enterprise • Maven • Docker
          </small>
        </div>
      </div>
    </div>
  </footer>

  <!-- Bootstrap 5 JS -->
  <script src="https://cdn.jsdelivr.net/npm/bootstrap@5.3.2/dist/js/bootstrap.bundle.min.js"></script>
</body>
</html>
