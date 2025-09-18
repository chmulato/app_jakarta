<%@ page contentType="text/html; charset=UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<!DOCTYPE html>
<html lang="pt-BR">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>Login - Meu App</title>
  
  <!-- Bootstrap 5 CSS -->
  <link href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.2/dist/css/bootstrap.min.css" rel="stylesheet">
  <link href="https://cdn.jsdelivr.net/npm/bootstrap-icons@1.11.0/font/bootstrap-icons.css" rel="stylesheet">
  
  <style>
    body {
      background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
      min-height: 100vh;
      display: flex;
      align-items: center;
      font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;
    }
    
    .login-container {
      background: white;
      border-radius: 20px;
      box-shadow: 0 20px 40px rgba(0,0,0,0.15);
      overflow: hidden;
      animation: slideUp 0.6s ease-out;
      max-width: 450px;
      width: 100%;
    }
    
    @keyframes slideUp {
      from {
        opacity: 0;
        transform: translateY(30px);
      }
      to {
        opacity: 1;
        transform: translateY(0);
      }
    }
    
    .login-header {
      background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
      color: white;
      padding: 2.5rem 2rem;
      text-align: center;
      position: relative;
      overflow: hidden;
    }
    
    .login-header::before {
      content: '';
      position: absolute;
      top: -50%;
      left: -50%;
      width: 200%;
      height: 200%;
      background: radial-gradient(circle, rgba(255,255,255,0.1) 0%, transparent 70%);
      animation: pulse 4s ease-in-out infinite;
    }
    
    @keyframes pulse {
      0%, 100% {
        transform: scale(1);
        opacity: 0.5;
      }
      50% {
        transform: scale(1.1);
        opacity: 0.8;
      }
    }
    
    .login-header > * {
      position: relative;
      z-index: 1;
    }
    
    .login-form {
      padding: 2.5rem;
    }
    
    .form-control {
      border: 2px solid #e9ecef;
      border-radius: 12px;
      padding: 0.75rem 1rem;
      font-size: 1rem;
      transition: all 0.3s ease;
    }
    
    .form-control:focus {
      border-color: #667eea;
      box-shadow: 0 0 0 0.25rem rgba(102, 126, 234, 0.15);
      transform: translateY(-2px);
    }
    
    .form-label {
      font-weight: 600;
      color: #495057;
      margin-bottom: 0.75rem;
    }
    
    .btn-login {
      background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
      border: none;
      color: white;
      font-weight: 600;
      padding: 1rem 2rem;
      border-radius: 15px;
      transition: all 0.3s ease;
      position: relative;
      overflow: hidden;
    }
    
    .btn-login::before {
      content: '';
      position: absolute;
      top: 0;
      left: -100%;
      width: 100%;
      height: 100%;
      background: linear-gradient(90deg, transparent, rgba(255,255,255,0.2), transparent);
      transition: left 0.5s;
    }
    
    .btn-login:hover {
      transform: translateY(-3px);
      box-shadow: 0 10px 25px rgba(102, 126, 234, 0.3);
      color: white;
    }
    
    .btn-login:hover::before {
      left: 100%;
    }
    
    .btn-login:active {
      transform: translateY(-1px);
    }
    
    .alert {
      border-radius: 12px;
      border: none;
      animation: shakeX 0.6s ease-in-out;
    }
    
    @keyframes shakeX {
      0%, 100% {
        transform: translateX(0);
      }
      10%, 30%, 50%, 70%, 90% {
        transform: translateX(-10px);
      }
      20%, 40%, 60%, 80% {
        transform: translateX(10px);
      }
    }
    
    .input-group .btn {
      border-radius: 0 12px 12px 0;
      border: 2px solid #e9ecef;
      border-left: none;
    }
    
    .input-group .form-control {
      border-radius: 12px 0 0 12px;
    }
    
    .form-check-input:checked {
      background-color: #667eea;
      border-color: #667eea;
    }
    
    .demo-credentials {
      background: linear-gradient(135deg, #f8f9fa 0%, #e9ecef 100%);
      border-radius: 15px;
      border: 1px solid #dee2e6;
      transition: all 0.3s ease;
    }
    
    .demo-credentials:hover {
      transform: translateY(-2px);
      box-shadow: 0 5px 15px rgba(0,0,0,0.1);
    }
    
    .back-link {
      transition: all 0.3s ease;
    }
    
    .back-link:hover {
      transform: translateX(-5px);
    }
    
    /* Responsividade melhorada */
    @media (max-width: 576px) {
      .login-container {
        margin: 1rem;
        border-radius: 15px;
      }
      
      .login-header {
        padding: 2rem 1.5rem;
      }
      
      .login-form {
        padding: 2rem 1.5rem;
      }
    }
    
    /* Loading state */
    .btn-login.loading {
      pointer-events: none;
    }
    
    .btn-login.loading::after {
      content: '';
      position: absolute;
      width: 20px;
      height: 20px;
      top: 50%;
      left: 50%;
      margin-left: -10px;
      margin-top: -10px;
      border: 2px solid transparent;
      border-top: 2px solid white;
      border-radius: 50%;
      animation: spin 1s linear infinite;
    }
    
    @keyframes spin {
      0% { transform: rotate(0deg); }
      100% { transform: rotate(360deg); }
    }
  </style>
</head>
<body>
  <div class="container">
    <div class="row justify-content-center">
      <div class="col-lg-5 col-md-7 col-sm-9">
        <div class="login-container">
          
          <!-- Header -->
          <div class="login-header">
            <div class="mb-3">
              <i class="bi bi-shield-lock display-3 mb-3"></i>
            </div>
            <h2 class="mb-2 fw-bold">Bem-vindo de volta!</h2>
            <p class="mb-0 opacity-90">Acesse sua conta para continuar</p>
          </div>
          
          <!-- Form -->
          <div class="login-form">
            
            <!-- Mensagem de erro -->
            <c:if test="${not empty erro}">
              <div class="alert alert-danger alert-dismissible fade show" role="alert">
                <div class="d-flex align-items-center">
                  <i class="bi bi-exclamation-triangle-fill me-2 fs-5"></i>
                  <div>
                    <strong>Ops!</strong> ${erro}
                  </div>
                </div>
                <button type="button" class="btn-close" data-bs-dismiss="alert"></button>
              </div>
            </c:if>
            
            <!-- Mensagem de sucesso (se houver) -->
            <c:if test="${not empty sucesso}">
              <div class="alert alert-success alert-dismissible fade show" role="alert">
                <div class="d-flex align-items-center">
                  <i class="bi bi-check-circle-fill me-2 fs-5"></i>
                  <div>
                    <strong>Sucesso!</strong> ${sucesso}
                  </div>
                </div>
                <button type="button" class="btn-close" data-bs-dismiss="alert"></button>
              </div>
            </c:if>
            
            <form method="post" action="${pageContext.request.contextPath}/login" id="loginForm"">
              
              <!-- Email -->
              <div class="mb-4">
                <label for="email" class="form-label">
                  <i class="bi bi-envelope-fill me-2 text-primary"></i>Endereço de Email
                </label>
                <input type="email" 
                       class="form-control form-control-lg" 
                       id="email" 
                       name="email" 
                       value="${email}"
                       placeholder="Digite seu email" 
                       required
                       autocomplete="email">
                <div class="invalid-feedback">
                  Por favor, insira um email válido.
                </div>
              </div>
              
              <!-- Senha -->
              <div class="mb-4">
                <label for="senha" class="form-label">
                  <i class="bi bi-lock-fill me-2 text-primary"></i>Senha
                </label>
                <div class="input-group">
                  <input type="password" 
                         class="form-control form-control-lg" 
                         id="senha" 
                         name="senha" 
                         placeholder="Digite sua senha" 
                         required
                         autocomplete="current-password">
                  <button class="btn btn-outline-secondary" 
                          type="button" 
                          onclick="togglePassword()"
                          title="Mostrar/Ocultar senha">
                    <i class="bi bi-eye" id="toggle-icon"></i>
                  </button>
                </div>
                <div class="invalid-feedback">
                  A senha é obrigatória.
                </div>
              </div>
              
              <!-- Lembrar-me e Esqueci senha -->
              <div class="d-flex justify-content-between align-items-center mb-4">
                <div class="form-check">
                  <input class="form-check-input" 
                         type="checkbox" 
                         id="lembrar" 
                         name="lembrar">
                  <label class="form-check-label" for="lembrar">
                    Lembrar-me
                  </label>
                </div>
                <a href="#" class="text-decoration-none text-muted small" onclick="showForgotPassword()">
                  <i class="bi bi-question-circle me-1"></i>Esqueci minha senha
                </a>
              </div>
              
              <!-- Botão Login -->
              <div class="d-grid mb-4">
                <button type="submit" class="btn btn-login btn-lg" id="btnLogin">
                  <i class="bi bi-box-arrow-in-right me-2"></i>
                  <span id="btnText">Entrar</span>
                </button>
              </div>
              
            </form>
            
            <!-- Links -->
            <div class="text-center mb-4">
              <a href="${pageContext.request.contextPath}/" class="text-decoration-none back-link">
                <i class="bi bi-arrow-left me-2"></i>Voltar ao início
              </a>
            </div>
            
            <!-- Credenciais de demonstração -->
            <div class="demo-credentials p-4">
              <div class="d-flex align-items-center mb-3">
                <i class="bi bi-info-circle-fill text-primary me-2"></i>
                <strong class="text-dark">Credenciais de Demonstração</strong>
              </div>
              <div class="row">
                <div class="col-12 mb-2">
                  <div class="d-flex align-items-center">
                    <i class="bi bi-person-fill text-muted me-2"></i>
                    <code class="bg-transparent text-dark">admin@meuapp.com</code>
                  </div>
                </div>
                <div class="col-12 mb-3">
                  <div class="d-flex align-items-center">
                    <i class="bi bi-key-fill text-muted me-2"></i>
                    <code class="bg-transparent text-dark">Admin@123</code>
                  </div>
                </div>
                <div class="col-12">
                  <button type="button" class="btn btn-outline-primary btn-sm w-100" onclick="fillDemoCredentials()">
                    <i class="bi bi-magic me-1"></i>Preencher Automaticamente
                  </button>
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
  
  <script>
    // Toggle password visibility
    function togglePassword() {
      const senhaInput = document.getElementById('senha');
      const toggleIcon = document.getElementById('toggle-icon');
      
      if (senhaInput.type === 'password') {
        senhaInput.type = 'text';
        toggleIcon.classList.remove('bi-eye');
        toggleIcon.classList.add('bi-eye-slash');
      } else {
        senhaInput.type = 'password';
        toggleIcon.classList.remove('bi-eye-slash');
        toggleIcon.classList.add('bi-eye');
      }
    }
    
    // Constantes demo centralizadas
    const DEMO_EMAIL = 'admin@meuapp.com';
    const DEMO_PASSWORD = 'Admin@123';

    // Fill demo credentials
    function fillDemoCredentials() {
      document.getElementById('email').value = DEMO_EMAIL;
      document.getElementById('senha').value = DEMO_PASSWORD;
      
      // Focus no botão de login
      document.getElementById('btnLogin').focus();
      
      // Mostrar feedback visual
      const button = event.target;
      const originalText = button.innerHTML;
      button.innerHTML = '<i class="bi bi-check me-1"></i>Preenchido!';
      button.classList.add('btn-success');
      button.classList.remove('btn-outline-primary');
      
      setTimeout(() => {
        button.innerHTML = originalText;
        button.classList.remove('btn-success');
        button.classList.add('btn-outline-primary');
      }, 2000);
    }
    
    // Show forgot password modal/alert
    function showForgotPassword() {
      alert('💡 Para demonstração, use as credenciais:\n\nEmail: ' + DEMO_EMAIL + '\nSenha: ' + DEMO_PASSWORD + '\n\nEm um sistema real, aqui seria enviado um link para recuperação de senha.');
    }
    
    // Form submission with loading state
    document.getElementById('loginForm').addEventListener('submit', function(e) {
      const btnLogin = document.getElementById('btnLogin');
      const btnText = document.getElementById('btnText');
      
      // Add loading state
      btnLogin.classList.add('loading');
      btnLogin.disabled = true;
      btnText.textContent = 'Entrando...';
      
      // Simulate minimum loading time for better UX
      setTimeout(() => {
        // Form will submit normally after this
      }, 500);
    });
    
    // Auto focus on email field
    document.addEventListener('DOMContentLoaded', function() {
      document.getElementById('email').focus();
      
      // Add floating labels effect
      const inputs = document.querySelectorAll('.form-control');
      inputs.forEach(input => {
        input.addEventListener('focus', function() {
          this.classList.add('focused');
        });
        
        input.addEventListener('blur', function() {
          if (!this.value) {
            this.classList.remove('focused');
          }
        });
      });
    });
    
    // Keyboard shortcuts
    document.addEventListener('keydown', function(e) {
      // Enter key anywhere on the page submits the form
      if (e.key === 'Enter' && !e.shiftKey) {
        const form = document.getElementById('loginForm');
        if (form && document.activeElement.tagName !== 'BUTTON') {
          e.preventDefault();
          form.submit();
        }
      }
      
      // Ctrl+D fills demo credentials
      if (e.ctrlKey && e.key === 'd') {
        e.preventDefault();
        fillDemoCredentials();
      }
    });
    
    // Real-time validation
    document.getElementById('email').addEventListener('input', function() {
      const email = this.value;
      const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
      
      if (email && !emailRegex.test(email)) {
        this.classList.add('is-invalid');
        this.classList.remove('is-valid');
      } else if (email) {
        this.classList.remove('is-invalid');
        this.classList.add('is-valid');
      } else {
        this.classList.remove('is-invalid', 'is-valid');
      }
    });
    
    document.getElementById('senha').addEventListener('input', function() {
      const senha = this.value;
      
      if (senha.length > 0 && senha.length < 3) {
        this.classList.add('is-invalid');
        this.classList.remove('is-valid');
      } else if (senha.length >= 3) {
        this.classList.remove('is-invalid');
        this.classList.add('is-valid');
      } else {
        this.classList.remove('is-invalid', 'is-valid');
      }
    });
  </script>
</body>
</html>