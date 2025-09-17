<#
    Script: setup-dev.ps1
        Objetivo: Automatizar preparação de ambiente para o projeto:
            - Java / Maven
            - Docker (cliente + daemon ativo) / Container postgres
            - WSL (detecção de distros)
            - Virtualenv Python + dependências
    Uso básico:
        ./setup-dev.ps1               # Checagens + criação de venv + instalação requisitos Python
        ./setup-dev.ps1 -Force        # Recria venv mesmo se existir
        ./setup-dev.ps1 -SkipPython   # Pula parte Python
        ./setup-dev.ps1 -NoColor      # Saída sem cores
        ./setup-dev.ps1 -OnlyCheck    # Apenas checagens, sem criar nada
        ./setup-dev.ps1 -StatusJson .\status-ambiente.json  # Exporta resultado das checagens em JSON
        ./setup-dev.ps1 -Quiet -StatusJson out\status.json  # Execução silenciosa com export JSON
    ./setup-dev.ps1 -EnsureAdmin        # Garante criação do usuário ADMIN default se ausente

        Validações adicionais:
            - Conta ADMIN default: contagem e verificação de hash (senha padrão esperada: Admin@123)
            - Hash bcrypt validado se biblioteca 'bcrypt' estiver instalada na venv

        Requer: PowerShell 5+ (recomendado 7+), permissões de execução (Set-ExecutionPolicy RemoteSigned)
        Notas:
            - Se Docker instalado mas daemon inativo → iniciar Docker Desktop
            - Se WSL ausente e planeja usar Docker WSL2 → instalar: wsl --install
            - Para reset da venv: usar -Force
#>
[CmdletBinding()]
param(
    [switch]$Force,
    [switch]$SkipPython,
    [switch]$OnlyCheck,
    [switch]$NoColor,
    [string]$StatusJson,
    [switch]$Quiet,
    [switch]$EnsureAdmin
)

$SCRIPT_VERSION = '1.1.0'

# ==========================
#   Funções utilitárias
# ==========================
$ErrorActionPreference = 'Stop'
$global:HAS_ERROR = $false
$global:STATUS = [ordered]@{
    Java = 'NOK'
    Maven = 'NOK'
    DockerCli = 'NOK'
    DockerDaemon = 'NOK'
    WSL = 'N/A'
    Postgres = 'NOK'
    PostgresConn = 'NOK'
    PostgresSchema = 'NOK'
    Perfis = 'Pending'
    Venv = 'NOK'
    WslDefault = ''
    DuraçãoSeg = 0
    AdminCount = 0
    AdminHash = 'N/A'
}

function Write-Info($msg){ if($Quiet){ return } ; if($NoColor){Write-Host "[INFO ] $msg"} else {Write-Host "[INFO ]" -ForegroundColor Cyan -NoNewline; Write-Host " $msg"} }
function Write-Ok($msg){ if($Quiet){ return } ; if($NoColor){Write-Host "[ OK  ] $msg"} else {Write-Host "[  OK ]" -ForegroundColor Green -NoNewline; Write-Host " $msg"} }
function Write-Warn($msg){ if($Quiet){ return } ; if($NoColor){Write-Host "[WARN ] $msg"} else {Write-Host "[WARN ]" -ForegroundColor Yellow -NoNewline; Write-Host " $msg"} }
function Write-Err($msg){ $global:HAS_ERROR = $true; if($NoColor){Write-Host "[ERRO ] $msg"} else {Write-Host "[ERRO ]" -ForegroundColor Red -NoNewline; Write-Host " $msg"} }

# ==========================
#   Checagem Java
# ==========================
function Test-Java {
    Write-Info 'Verificando Java...'
    try {
        $out = & java -version 2>&1
        if($LASTEXITCODE -eq 0){
            $line = ($out -split "`n")[0].Trim()
            Write-Ok "Java detectado: $line"
            $global:STATUS.Java = 'OK'
            if(-not $env:JAVA_HOME){ Write-Warn 'JAVA_HOME não definido (opcional, mas recomendado).'}
        } else { Write-Err 'Java não executou corretamente.' }
    } catch { Write-Err 'Java não encontrado no PATH.' }
}

# ==========================
#   Checagem Maven
# ==========================
function Test-Maven {
    Write-Info 'Verificando Maven...'
    try {
        $out = & mvn -version 2>&1
        if($LASTEXITCODE -eq 0){
            $first = ($out -split "`n")[0].Trim()
            Write-Ok "Maven detectado: $first"
            $global:STATUS.Maven = 'OK'
            if(-not $env:MAVEN_HOME -and -not $env:M2_HOME){ Write-Warn 'MAVEN_HOME/M2_HOME não definidos (não obrigatório).'}
        } else { Write-Err 'Maven não executou corretamente.' }
    } catch { Write-Err 'Maven não encontrado no PATH.' }
}

# ==========================
#   Checagem Docker (opcional)
# ==========================
function Test-Docker {
    Write-Info 'Verificando Docker...'
    try {
        $ver = & docker --version 2>&1
        if($LASTEXITCODE -ne 0){ Write-Warn 'Docker não disponível.'; return }
        $global:STATUS.DockerCli = 'OK'
        $info = & docker info --format '{{.ServerVersion}}' 2>$null
        if([string]::IsNullOrWhiteSpace($info)){
            Write-Warn 'Docker CLI ok mas daemon inativo (abrir Docker Desktop).'
        } else {
            Write-Ok ("Docker ativo - Versão Servidor: {0}" -f $info)
            $global:STATUS.DockerDaemon = 'OK'
        }
    } catch { Write-Warn 'Docker não encontrado (ignorado).' }
}

# ==========================
#   Checagem WSL
# ==========================
function Test-WSL {
    Write-Info 'Verificando WSL (Subsistema Linux)...'
    try {
        $null = (Get-Command wsl.exe -ErrorAction Stop).Source
        $distros = & wsl.exe -l -q 2>$null | Where-Object { -not [string]::IsNullOrWhiteSpace($_) } | ForEach-Object { $_.Trim() } | Where-Object { $_ -ne '' } | Sort-Object -Unique
        if(-not $distros){
            Write-Warn 'WSL instalado mas nenhuma distro configurada.'
            $global:STATUS.WSL = 'SemDistros'
        } else {
            $defaultLine = (& wsl.exe -l -v 2>$null | Select-String '\*')
            $cleanDefault = $null
            if($defaultLine){
                $raw = $defaultLine.ToString()
                $cleanDefault = ($raw -replace '\*','')
                $cleanDefault = ($cleanDefault -split '\s{2,}')[0].Trim()
            }
            Write-Ok ("WSL disponível. Distros: {0}" -f (($distros | Where-Object { $_ }) -join ', '))
            if($cleanDefault){ Write-Info ("Distro default: $cleanDefault"); $global:STATUS.WslDefault = $cleanDefault }
            $global:STATUS.WSL = 'OK'
        }
        # Checar integração Docker/WSL (heurística simples: se docker ativo e WSL sem distros)
    } catch {
        Write-Warn 'WSL não detectado (ignorando).'
        $global:STATUS.WSL = 'Ausente'
    }
}

# ==========================
#   Checagem PostgreSQL container (nome: postgres)
# ==========================
function Test-PostgresContainer {
    Write-Info 'Verificando container postgres...'
    try {
        $name = (& docker ps --filter "name=meu-app-postgres" --format "{{.Names}}" 2>$null)
        if([string]::IsNullOrWhiteSpace($name)){
            Write-Warn 'Container postgres não está em execução (docker-compose up -d).'
        } else {
            # Health (se disponível)
            $health = (& docker inspect --format '{{json .State.Health.Status}}' $name 2>$null) -replace '"',''
            if([string]::IsNullOrWhiteSpace($health)){ $health = 'unknown' }
            Write-Ok ("Container ativo: {0} (health={1})" -f $name, $health)
            if($health -eq 'healthy'){ $global:STATUS.Postgres = 'OK' } else { $global:STATUS.Postgres = $health }
        }
    } catch { Write-Warn 'Docker indisponível para verificar container.' }
}

# ==========================
#   Checagem Perfis Maven
# ==========================
function Test-MavenProfiles {
    $pom = Join-Path $PSScriptRoot 'meu-projeto-java' 'pom.xml'
    if(-not (Test-Path $pom)){ Write-Err 'pom.xml não encontrado em meu-projeto-java.'; return }
    $content = Get-Content $pom -Raw
    $profiles = @('tomcat','wildfly','run')
    $missing = @()
    foreach($p in $profiles){
        if($content -match "<id>$p</id>"){ Write-Ok "Perfil Maven encontrado: $p" } else { Write-Warn "Perfil Maven AUSENTE: $p"; $missing += $p }
    }
    if($missing.Count -eq 0){ $global:STATUS.Perfis = 'OK' } else { $global:STATUS.Perfis = ('Faltando: ' + ($missing -join ',')) }
}

# ==========================
#   Python / Virtualenv
# ==========================
function Setup-Python {
    if($SkipPython){ Write-Warn 'Pulado setup Python.'; return }
    Write-Info 'Preparando ambiente Python (venv)...'
    $venvPath = Join-Path $PSScriptRoot '.venv'
    if(Test-Path $venvPath){
        if($Force){
            Write-Warn 'Forçando recriação da venv...'
            Remove-Item $venvPath -Recurse -Force
        }
    }
    if(-not (Test-Path $venvPath)){
        try {
            & python -m venv $venvPath
            if($LASTEXITCODE -ne 0){ Write-Err 'Falha ao criar venv.'; return }
            Write-Ok 'Venv criada.'
            $global:STATUS.Venv = 'OK'
        } catch { Write-Err 'Python não encontrado ou erro ao criar venv.'; return }
    } else { Write-Ok 'Venv já existe.'; $global:STATUS.Venv = 'OK' }

    $activate = Join-Path $venvPath 'Scripts' 'Activate.ps1'
    if(-not (Test-Path $activate)){ Write-Err 'Script de ativação não encontrado.'; return }

    Write-Info 'Instalando dependências (requirements.txt)...'
    $reqFile = Join-Path $PSScriptRoot 'requirements.txt'
    if(-not (Test-Path $reqFile)){ Write-Warn 'requirements.txt não encontrado, pulando.'; return }

    $pipLog = & powershell -NoLogo -NoProfile -ExecutionPolicy Bypass -Command "& {`n  . '$activate'; `n  pip install --upgrade pip; `n  pip install -r '$reqFile' `n}" 2>&1
    if($LASTEXITCODE -eq 0){
        Write-Ok 'Dependências Python instaladas.'
    } else {
        Write-Err 'Falha ao instalar dependências Python.'
        Write-Warn ($pipLog | Select-Object -First 20 -Join "`n")
    }
}

function Validate-PythonEnv {
    Write-Info 'Validando ambiente Python (sem criar)...'
    $venvPath = Join-Path $PSScriptRoot '.venv'
    if(-not (Test-Path $venvPath)){
        Write-Warn 'Venv não encontrada.'
        return
    }
    $activate = Join-Path $venvPath 'Scripts' 'Activate.ps1'
    if(-not (Test-Path $activate)){
        Write-Err 'Venv presente porém sem Scripts/Activate.ps1.'
        return
    }
    $pythonExe = Join-Path $venvPath 'Scripts' 'python.exe'
    if(-not (Test-Path $pythonExe)){
        Write-Err 'python.exe não encontrado dentro da venv.'
        return
    }
    $ver = & $pythonExe -c "import sys;print(sys.version.split()[0])" 2>$null
    if($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($ver)){
        Write-Warn 'Falha ao executar Python dentro da venv.'
    } else {
        Write-Ok ("Venv válida - Python {0}" -f $ver.Trim())
        $global:STATUS.Venv = 'OK'
    }
    # Verificar dependências mínimas
    $reqFile = Join-Path $PSScriptRoot 'requirements.txt'
    if(Test-Path $reqFile){
        $missing = @()
        $pkgs = & $pythonExe -m pip freeze 2>$null
        foreach($line in Get-Content $reqFile){
            if($line -match '^(#|\s*$)'){ continue }
            $base = ($line -split '==|>=')[0].Trim()
            if(-not ($pkgs -match "^$base==")){ $missing += $base }
        }
        if($missing.Count -gt 0){ Write-Warn ("Dependências ausentes na venv: {0}" -f ($missing -join ',')) } else { Write-Ok 'Dependências requeridas presentes.' }
    }
}

# ==========================
#   Checagem PostgreSQL - Teste de Conexão e Schema
# ==========================
function Test-PostgresDB {
    if($global:STATUS.Postgres -ne 'OK') { Write-Warn 'Pulando teste de DB: container não saudável.'; return }
    Write-Info 'Testando conexão Postgres (psql)...'
    try {
        $out = & docker exec meu-app-postgres psql -U meu_app_user -d meu_app_db -t -c 'SELECT 1;' 2>&1
        if($LASTEXITCODE -ne 0){ Write-Err 'Falha ao conectar no Postgres.'; return } else { Write-Ok 'Conexão psql ok.'; $global:STATUS.PostgresConn = 'OK' }
    } catch { Write-Err 'Erro executando psql (conexão).'; return }
    Write-Info 'Validando schema (tabelas usuarios/produtos)...'
    try {
    $tablesRes = & docker exec meu-app-postgres psql -U meu_app_user -d meu_app_db -A -F "," -t -c "SELECT to_regclass('public.usuarios') IS NOT NULL, to_regclass('public.produtos') IS NOT NULL;" 2>&1
        if($LASTEXITCODE -ne 0){ Write-Err 'Falha verificação tabelas.'; return }
        $flags = $tablesRes.Trim() -split ','
        $uOk = ($flags[0] -eq 't'); $pOk = ($flags[1] -eq 't')
    $countLine = & docker exec meu-app-postgres psql -U meu_app_user -d meu_app_db -A -F "," -t -c "SELECT (SELECT COUNT(*) FROM usuarios),(SELECT COUNT(*) FROM produtos);" 2>&1
        if($LASTEXITCODE -eq 0){
            $counts = $countLine.Trim() -split ','
            $uc = $counts[0]; $pc = $counts[1]
            if($uOk -and $pOk){
                $global:STATUS.PostgresSchema = 'OK'
                Write-Ok ("Schema ok (usuarios={0}, produtos={1})" -f $uc, $pc)
            } else {
                $global:STATUS.PostgresSchema = 'Parcial'
                Write-Warn ("Schema parcial (usuariosOk={0}, produtosOk={1})" -f $uOk, $pOk)
            }
        } else { Write-Err 'Falha obtendo contagens.' }
    } catch { Write-Err 'Erro validando schema.' }
}

# ==========================
#   Checagem Usuários ADMIN
# ==========================
function Test-AdminUsers {
    if($global:STATUS.PostgresSchema -ne 'OK'){ return }
    Write-Info 'Contando usuários ADMIN...'
    try {
        # Verificar se coluna 'perfil' existe
        $col = & docker exec meu-app-postgres psql -U meu_app_user -d meu_app_db -t -A -c "SELECT 1 FROM information_schema.columns WHERE table_name='usuarios' AND column_name='perfil' LIMIT 1;" 2>$null
            $colVal = if($col){ $col.Trim() } else { '' }
            $hasPerfil = ($LASTEXITCODE -eq 0 -and $colVal -eq '1')
        if(-not $hasPerfil){
            Write-Warn "Coluna 'perfil' ausente em usuarios (schema antigo)."
            # Contagem genérica de usuários para referência
            $ucount = & docker exec meu-app-postgres psql -U meu_app_user -d meu_app_db -t -A -c "SELECT COUNT(*) FROM usuarios;" 2>$null
            if($LASTEXITCODE -eq 0){ $global:STATUS.AdminCount = 0; Write-Info ("Total usuarios (sem coluna perfil): {0}" -f $ucount.Trim()) }
            return
        }
        $count = & docker exec meu-app-postgres psql -U meu_app_user -d meu_app_db -t -A -c "SELECT COUNT(*) FROM usuarios WHERE perfil='ADMIN';" 2>&1
        if($LASTEXITCODE -ne 0){ Write-Warn 'Não foi possível contar usuários ADMIN.'; return }
        $val = [int]($count.Trim())
        $global:STATUS.AdminCount = $val
        if($val -gt 0){ Write-Ok ("Admins existentes: {0}" -f $val) } else { Write-Warn 'Nenhum usuário ADMIN encontrado.' }
    } catch { Write-Warn 'Erro ao consultar usuários ADMIN.' }
}

function Test-AdminHash {
    if($global:STATUS.AdminCount -le 0 -or $global:STATUS.PostgresSchema -ne 'OK'){ return }
    Write-Info 'Validando hash bcrypt do admin padrão...'
    try {
        $hash = & docker exec meu-app-postgres psql -U meu_app_user -d meu_app_db -t -A -c "SELECT senha FROM usuarios WHERE email='admin@meuapp.com' LIMIT 1;" 2>&1
        if($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($hash)){ Write-Warn 'Hash admin não obtido.'; return }
        $hash = $hash.Trim()
        $expectedPlain = 'Admin@123'
        $py = @"
import bcrypt, sys
pwd = b'$expectedPlain'
stored = b'$hash'
try:
    ok = bcrypt.checkpw(pwd, stored)
    print('OK' if ok else 'MISMATCH')
except Exception as e:
    print('ERROR:'+str(e))
"@
        $venvPython = Join-Path $PSScriptRoot '.venv' 'Scripts' 'python.exe'
        $pythonExe = (Test-Path $venvPython) ? $venvPython : 'python'
        $result = & $pythonExe -c $py 2>&1
        if($LASTEXITCODE -ne 0){ Write-Warn 'Falha executando checagem de hash.'; return }
        if($result -match '^OK$'){ $global:STATUS.AdminHash = 'OK'; Write-Ok 'Hash admin válido (senha padrão esperada).'; }
        elseif($result -match '^MISMATCH$'){ $global:STATUS.AdminHash = 'MISMATCH'; Write-Warn 'Hash admin difere da senha padrão (isso pode ser intencional).'; }
        else { $global:STATUS.AdminHash = 'DESCONHECIDO'; Write-Warn ("Resultado checagem hash: $result") }
    } catch { Write-Warn 'Erro validando hash admin.' }
}

function Ensure-AdminUser {
    if(-not $EnsureAdmin){ return }
    if($global:STATUS.PostgresSchema -ne 'OK'){ Write-Warn 'EnsureAdmin ignorado: schema não OK.'; return }
    if($global:STATUS.AdminCount -gt 0){ Write-Info 'Já existe ADMIN, não será criado novo.'; return }
    Write-Info 'Criando usuário ADMIN default (EnsureAdmin)...'
    # Detectar coluna perfil
    $col = & docker exec meu-app-postgres psql -U meu_app_user -d meu_app_db -t -A -c "SELECT 1 FROM information_schema.columns WHERE table_name='usuarios' AND column_name='perfil' LIMIT 1;" 2>$null
        $colVal = if($col){ $col.Trim() } else { '' }
        $hasPerfil = ($LASTEXITCODE -eq 0 -and $colVal -eq '1')
    if(-not $hasPerfil){
        Write-Warn "Coluna 'perfil' ausente. Aplicando migração leve (ALTER TABLE)."
        $alter = & docker exec meu-app-postgres psql -U meu_app_user -d meu_app_db -c "ALTER TABLE usuarios ADD COLUMN IF NOT EXISTS perfil VARCHAR(20) DEFAULT 'USUARIO' CHECK (perfil IN ('ADMIN','USUARIO'));" 2>&1
        if($LASTEXITCODE -ne 0){ Write-Err 'Falha ao adicionar coluna perfil.'; Write-Warn ($alter | Select-Object -First 5); return }
        Write-Ok "Coluna 'perfil' adicionada."
    }
    $cmd = "INSERT INTO usuarios (nome,email,senha,perfil) VALUES ('Administrador','admin@meuapp.com','$2a$10$rF1YS1T.8QVXnpTlI.JY5u5Kz7x8TXDQ9Y2c3M4z6N8x.wJ4sA2G6','ADMIN') ON CONFLICT (email) DO NOTHING;"
    $res = & docker exec meu-app-postgres psql -U meu_app_user -d meu_app_db -c $cmd 2>&1
    if($LASTEXITCODE -eq 0){
        Write-Ok 'Usuário ADMIN default garantido.'
        $count = & docker exec meu-app-postgres psql -U meu_app_user -d meu_app_db -t -A -c "SELECT COUNT(*) FROM usuarios WHERE perfil='ADMIN';" 2>$null
        if($LASTEXITCODE -eq 0){ $global:STATUS.AdminCount = [int]($count.Trim()) }
            # Recontar via função existente
            Test-AdminUsers
            Test-AdminHash
    } else {
        Write-Err 'Falha ao garantir usuário ADMIN.'
        Write-Warn ($res | Select-Object -First 5)
    }
}

# ==========================
#   Sugestões / Export JSON
# ==========================
function Get-Suggestions {
    $suggest = @()
    if($global:STATUS.DockerCli -ne 'OK'){ $suggest += 'Instalar Docker Desktop ou adicionar docker ao PATH.' }
    elseif($global:STATUS.DockerDaemon -ne 'OK'){ $suggest += 'Iniciar Docker Desktop até daemon ficar ativo.' }
    if($global:STATUS.Postgres -ne 'OK'){ $suggest += 'Subir banco: docker-compose up -d' }
    elseif($global:STATUS.PostgresConn -ne 'OK'){ $suggest += 'Verificar credenciais (meu_app_user / senha) ou variáveis.' }
    elseif($global:STATUS.PostgresSchema -ne 'OK'){ $suggest += 'Verificar scripts em docker/postgres/init (tabelas ausentes).' }
    if($global:STATUS.Venv -ne 'OK' -and -not $OnlyCheck){ $suggest += 'Rever criação da venv (checar Python no PATH).' }
    if($OnlyCheck -and $global:STATUS.Venv -ne 'OK'){ $suggest += 'Executar novamente sem -OnlyCheck para criar venv.' }
    if($global:STATUS.Perfis -like 'Faltando*'){ $suggest += 'Adicionar perfis ausentes no pom.xml.' }
    if($global:STATUS.WSL -eq 'SemDistros'){ $suggest += 'Instalar uma distro WSL: wsl --install -d Ubuntu.' }
    if($global:STATUS.PostgresSchema -eq 'OK' -and $global:STATUS.AdminCount -eq 0){ $suggest += 'Criar usuário ADMIN inicial ou revisar bloco DO $$ no init SQL.' }
    if($global:STATUS.PostgresSchema -eq 'OK' -and $global:STATUS.AdminCount -eq 0 -and -not $EnsureAdmin){ $suggest += 'Executar com -EnsureAdmin para garantir criação do ADMIN.' }
    return $suggest
}

function Export-StatusJson {
    param([Parameter(Mandatory)][string]$Path)
    try {
        $obj = [ordered]@{
            timestamp = (Get-Date).ToString('o')
            scriptVersion = $SCRIPT_VERSION
            status = $global:STATUS
            suggestions = Get-Suggestions
            onlyCheck = [bool]$OnlyCheck
            host = $env:COMPUTERNAME
        }
        $json = $obj | ConvertTo-Json -Depth 6
        $dir = Split-Path $Path -Parent
        if($dir -and -not (Test-Path $dir)){ New-Item -ItemType Directory -Path $dir | Out-Null }
        Set-Content -Path $Path -Value $json -Encoding UTF8
        Write-Ok ("Status exportado para JSON: {0}" -f $Path)
    } catch {
        Write-Warn ("Falha ao exportar JSON: {0}" -f $_.Exception.Message)
    }
}

# ==========================
#   Resumo Final
# ==========================
function Show-Summary {
    Write-Host ''
    if($NoColor){ Write-Host '========== RESUMO ==========' } else { Write-Host '========== RESUMO ==========' -ForegroundColor Magenta }
    Write-Host ("Java:          {0}" -f $global:STATUS.Java)
    Write-Host ("Maven:         {0}" -f $global:STATUS.Maven)
    Write-Host ("Docker CLI:    {0}" -f $global:STATUS.DockerCli)
    Write-Host ("Docker Daemon: {0}" -f $global:STATUS.DockerDaemon)
    Write-Host ("WSL:           {0}" -f $global:STATUS.WSL)
    if($global:STATUS.WslDefault){ Write-Host ("WSL Default:   {0}" -f $global:STATUS.WslDefault) }
    Write-Host ("Postgres:      {0}" -f $global:STATUS.Postgres)
    Write-Host ("Pg Conn:       {0}" -f $global:STATUS.PostgresConn)
    Write-Host ("Pg Schema:     {0}" -f $global:STATUS.PostgresSchema)
    if($global:STATUS.AdminCount -ge 0 -and $global:STATUS.PostgresSchema -eq 'OK') { Write-Host ("Admins:        {0}" -f $global:STATUS.AdminCount) }
    if($global:STATUS.PostgresSchema -eq 'OK' -and $global:STATUS.AdminCount -gt 0) { Write-Host ("AdminHash:     {0}" -f $global:STATUS.AdminHash) }
    Write-Host ("Perfis:        {0}" -f $global:STATUS.Perfis)
    Write-Host ("Venv:          {0}" -f $global:STATUS.Venv)
    Write-Host ("Duração (s):   {0}" -f $global:STATUS.DuraçãoSeg)
    # Sugestões
    $suggest = Get-Suggestions
    if($suggest.Count -gt 0){
        Write-Host ''
        Write-Host 'Sugestões:'
        $suggest | ForEach-Object { Write-Host ' - '$_ }
    }
    if($HAS_ERROR){ Write-Err 'Concluído com avisos/erros. Verifique mensagens acima.' } else { Write-Ok 'Setup concluído.' }
}

# ==========================
#   Execução
# ==========================
$_start = Get-Date
Write-Info 'Iniciando setup de ambiente...'
Test-Java
Test-Maven
Test-Docker
Test-WSL
Test-PostgresContainer
Test-PostgresDB
Test-AdminUsers
Test-AdminHash
Ensure-AdminUser
Test-MavenProfiles
if(-not $OnlyCheck){
    Setup-Python
} else {
    Write-Info 'Modo OnlyCheck: validando ambiente Python existente.'
    Validate-PythonEnv
}
$_end = Get-Date
$global:STATUS.DuraçãoSeg = [math]::Round(($_end - $_start).TotalSeconds,2)
Show-Summary

if($StatusJson){ Export-StatusJson -Path $StatusJson }

Write-Info 'Dica: execute "python .\\main.py --only-check" após ativar a venv.'
