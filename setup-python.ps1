<#
Licensed under the Apache License, Version 2.0 (the "License");
You may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.

© 2025 23.969.028 CHRISTIAN VLADIMIR UHDRE MULATO (CNPJ 23.969.028/0001-37)
#>
<#
    Script: setup-python.ps1
    Objetivo: Configurar ou validar o ambiente Python deste projeto para automação com setup.dev.py.

    Funcionalidades:
      - Detecta Python no PATH (ou usa caminho informado)
      - Verifica versao minima (3.10+)
      - Cria venv em .venv (ou caminho customizado)
      - Instala dependencias do requirements.txt (incluindo bcrypt e outras necessárias)
      - Valida pacotes instalados para garantir compatibilidade com setup.dev.py
      - Exporta status em JSON (opcional)
      - Suporta execução direta do setup.dev.py após configuração

    Uso:
      ./setup-python.ps1                   # Cria/valida venv e instala deps
      ./setup-python.ps1 -OnlyCheck        # Apenas valida sem criar/instalar
      ./setup-python.ps1 -Force            # Recria venv
      ./setup-python.ps1 -Python python3   # Forca executavel Python
      ./setup-python.ps1 -SkipPipUpgrade   # Nao roda pip install --upgrade pip
      ./setup-python.ps1 -StatusJson out\status-python.json
      ./setup-python.ps1 -RunSetupDev      # Executa setup.dev.py após configuração
      ./setup-python.ps1 -RunSetupDev -SetupDevArgs "--only-check" # Passa argumentos ao setup.dev.py

    Requer: Windows PowerShell 5.1+ (ou PowerShell 7), acesso a internet para instalar pacotes.
#>
[CmdletBinding()]
param(
    [switch]$OnlyCheck,
    [switch]$Force,
    [string]$Python,
    [string]$Requirements,
    [string]$VenvPath,
    [switch]$NoColor,
    [switch]$Quiet,
    [switch]$SkipPipUpgrade,
    [string]$StatusJson,
    [switch]$RunSetupDev,
    [string]$SetupDevArgs
)

# Fallback robusto para diretório do script
$ScriptRoot = $PSScriptRoot
if([string]::IsNullOrWhiteSpace($ScriptRoot)){
    $ScriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
}
if([string]::IsNullOrWhiteSpace($Requirements)){
    $Requirements = Join-Path $ScriptRoot 'requirements.txt'
}
if([string]::IsNullOrWhiteSpace($VenvPath)){
    $VenvPath = Join-Path $ScriptRoot '.venv'
}

$ErrorActionPreference = 'Stop'
$SCRIPT_VERSION = '1.0.0'

# ------------------------ util saída ------------------------
function Write-Info($msg){ if($Quiet){ return } ; if($NoColor){Write-Host "[INFO ] $msg"} else {Write-Host "[INFO ]" -ForegroundColor Cyan -NoNewline; Write-Host " $msg"} }
function Write-Ok($msg){ if($Quiet){ return } ; if($NoColor){Write-Host "[ OK  ] $msg"} else {Write-Host "[  OK ]" -ForegroundColor Green -NoNewline; Write-Host " $msg"} }
function Write-Warn($msg){ if($Quiet){ return } ; if($NoColor){Write-Host "[WARN ] $msg"} else {Write-Host "[WARN ]" -ForegroundColor Yellow -NoNewline; Write-Host " $msg"} }
function Write-Err($msg){ if($NoColor){Write-Host "[ERRO ] $msg"} else {Write-Host "[ERRO ]" -ForegroundColor Red -NoNewline; Write-Host " $msg"} }

$global:STATUS = [ordered]@{
    Python = 'NOK'
    PythonVersion = ''
    Venv = 'NOK'
    Pip = 'NOK'
    Requirements = (Split-Path -Leaf $Requirements)
    MissingPackages = @()
    DuracaoSeg = 0
}
$global:HAS_ERROR = $false

# ------------------------ helpers ------------------------
function Resolve-PythonExe {
    param([string]$Preferred)
    $candidates = @()
    if($Preferred){ $candidates += $Preferred }
    $candidates += 'python'
    if($IsWindows){ $candidates += 'py -3'; $candidates += 'py' }

    foreach($cmd in $candidates){
        try {
            & $cmd --version 2>&1 | Out-Null
            if($LASTEXITCODE -eq 0){ return $cmd }
        } catch { continue }
    }
    return $null
}

function Get-PythonVersion {
    param([string]$Py)
    try {
        $ver = & $Py -c "import sys;print(sys.version.split()[0])" 2>$null
        if($LASTEXITCODE -eq 0){ return $ver.Trim() }
    } catch {}
    return ''
}

function Compare-Version($a, $b){
    $pa = ($a -split '\.') | ForEach-Object { [int]$_ }
    $pb = ($b -split '\.') | ForEach-Object { [int]$_ }
    $len = [Math]::Max($pa.Count, $pb.Count)
    for($i=0;$i -lt $len;$i++){
        $va = if($i -lt $pa.Count){ $pa[$i] } else { 0 }
        $vb = if($i -lt $pb.Count){ $pb[$i] } else { 0 }
        if($va -gt $vb){ return 1 }
        if($va -lt $vb){ return -1 }
    }
    return 0
}

function Get-VenvPython([string]$Path){
    $exe = Join-Path $Path (Join-Path 'Scripts' 'python.exe')
    if(-not (Test-Path $exe)){
        $exe = Join-Path $Path (Join-Path 'bin' 'python')
    }
    return $exe
}

function Initialize-Venv {
    param([string]$Py,[string]$Path,[switch]$Force)
    if(Test-Path $Path){
        if($Force){
            Write-Warn "Forcando recriacao da venv..."
            Remove-Item $Path -Recurse -Force -ErrorAction SilentlyContinue
        }
    }
    if(-not (Test-Path $Path)){
        Write-Info "Criando venv em $Path ..."
        & $Py -m venv $Path
        if($LASTEXITCODE -ne 0){ throw 'Falha ao criar venv.' }
        Write-Ok 'Venv criada.'
    } else {
        Write-Ok 'Venv ja existe.'
    }
    $global:STATUS.Venv = 'OK'
}

function Install-Requirements {
    param([string]$VenvPy,[string]$ReqPath,[switch]$SkipUpgrade)
    if(-not (Test-Path $VenvPy)){ throw 'Executavel Python da venv nao encontrado.' }
    if($SkipUpgrade){ Write-Info 'Pulando upgrade do pip.' }
    else {
        Write-Info 'Atualizando pip...'
        & $VenvPy -m pip install --upgrade pip | Out-Null
    }
    
    # Pacotes críticos que devem estar presentes para o tooling do projeto
    # - bcrypt: validações de hash
    # - requests: HTTP client usado pelo main.py/setup
    # - colorama: saída colorida do console
    # - psutil: métricas e diagnóstico do sistema
    # - PyYAML: leitura de arquivos YAML (módulo de importação 'yaml')
    $criticalPackages = @('bcrypt','requests','colorama','psutil','yaml')
    
    if(Test-Path $ReqPath){
        Write-Info "Instalando dependencias ($([System.IO.Path]::GetFileName($ReqPath)))..."
        & $VenvPy -m pip install -r $ReqPath
        if($LASTEXITCODE -ne 0){ throw 'Falha ao instalar dependencias.' }
        Write-Ok 'Dependencias instaladas.'
        $global:STATUS.Pip = 'OK'
    } else {
        Write-Warn 'Arquivo requirements.txt nao encontrado, pulando instalacao.'
    }
    
    # Nota: 'builtins' é módulo embutido (stdlib) do Python e não requer instalação via pip.

    # Verificar e instalar pacotes críticos do projeto
    $missingCritical = @()
    foreach($pkg in $criticalPackages) {
        Write-Info "Verificando pacote crítico: $pkg..."
        try {
            $checkCmd = "import importlib.util; print('installed' if importlib.util.find_spec('$pkg') else 'missing')"
            $checkResult = & $VenvPy -c $checkCmd 2>$null
            if($checkResult -ne 'installed') {
                $missingCritical += $pkg
                Write-Info "Pacote $pkg não encontrado, será instalado."
            } else {
                Write-Ok "Pacote $pkg já está instalado."
            }
        } catch {
            $missingCritical += $pkg
            Write-Warn "Erro ao verificar $pkg, será instalado."
        }
    }
    
    if($missingCritical.Count -gt 0) {
        Write-Info "Instalando pacotes críticos do projeto: $($missingCritical -join ', ')..."
        # Mapeamento de nomes de módulo (import) -> nomes de pacote do pip
        $moduleToPip = @{
            'yaml' = 'pyyaml'
        }
        foreach($pkg in $missingCritical) {
            $pipName = if($moduleToPip.ContainsKey($pkg)) { $moduleToPip[$pkg] } else { $pkg }
            Write-Info "Instalando $pipName (módulo '$pkg')..."
            & $VenvPy -m pip install $pipName
            if($LASTEXITCODE -ne 0) {
                Write-Err "Falha ao instalar $pipName. Algumas funcionalidades do setup/dev podem falhar."
                $global:HAS_ERROR = $true
            } else {
                Write-Ok "Pacote $pipName instalado com sucesso."
            }
        }
    } else {
        Write-Ok "Todos os pacotes críticos do projeto estão instalados."
    }

    # Garantir driver PostgreSQL (psycopg2) instalado
    Write-Info "Verificando driver PostgreSQL (psycopg2)..."
    $hasPsy = '0'
    try {
        $checkCmd = "import importlib.util; print('1' if importlib.util.find_spec('psycopg2') else '0')"
        $hasPsy = & $VenvPy -c $checkCmd 2>$null
    } catch {
        $hasPsy = '0'
    }
    if($hasPsy -ne '1'){
        if($IsWindows){
            Write-Info "Instalando psycopg2-binary (Windows)..."
            & $VenvPy -m pip install psycopg2-binary
            if($LASTEXITCODE -ne 0){
                Write-Warn "Falha ao instalar psycopg2-binary. Tentando psycopg2..."
                & $VenvPy -m pip install psycopg2
            }
        } else {
            Write-Info "Instalando psycopg2 (Linux/Mac)..."
            & $VenvPy -m pip install psycopg2
            if($LASTEXITCODE -ne 0){
                Write-Warn "Falha ao instalar psycopg2. Tentando psycopg2-binary..."
                & $VenvPy -m pip install psycopg2-binary
            }
        }
        if($LASTEXITCODE -ne 0){
            Write-Err "Falha ao instalar driver PostgreSQL (psycopg2)."
            $global:HAS_ERROR = $true
        } else {
            Write-Ok "Driver PostgreSQL instalado."
        }
    } else {
        Write-Ok "Driver PostgreSQL já presente."
    }
}

function Test-Requirements {
    param([string]$VenvPy,[string]$ReqPath)
    if(-not (Test-Path $ReqPath)){ return }
    # Sanidade: o executavel Python existe?
    if(-not (Test-Path $VenvPy)){ 
        Write-Warn "Executavel Python ($VenvPy) nao encontrado."; 
        return 
    }
    
    # Tenta executar Python da venv com tratamento de erro robusto
    try {
        $probe = & $VenvPy -c "print('ok')" 2>&1
        if($LASTEXITCODE -ne 0){ 
            Write-Warn 'Python da venv nao executou corretamente (venv pode estar quebrada).'; 
            return 
        }
    }
    catch {
        Write-Warn "Erro ao executar Python da venv: $_"
        return
    }
    
    # Tenta obter lista de pacotes instalados
    try {
        $freeze = & $VenvPy -m pip freeze 2>&1
        if($LASTEXITCODE -ne 0){ 
            Write-Warn 'Nao foi possivel obter pip freeze.'; 
            return 
        }
    }
    catch {
        Write-Warn "Erro ao executar pip freeze: $_"
        return
    }
    
    # Pacotes críticos necessários para o tooling do projeto
    # (usar nomes de módulos importáveis)
    $criticalPackages = @('bcrypt','requests','colorama','psutil','yaml')
    
    $installed = @{}
    foreach($line in ($freeze -split "`n")){
        if([string]::IsNullOrWhiteSpace($line)){ continue }
        $name = ($line -split '==')[0].Trim().ToLowerInvariant()
        if(-not $installed.ContainsKey($name)){ $installed[$name] = $true }
    }
    
    $missing = @()
    foreach($req in Get-Content -Path $ReqPath){
        if($req -match '^(#|\s*$)'){ continue }
        $base = ($req -split '==|>=' )[0].Trim().ToLowerInvariant()
        if(-not $installed.ContainsKey($base)){ $missing += $base }
    }
    
    # Verifica pacotes críticos adicionais que podem não estar no requirements.txt
    # Considerar mapeamentos entre nome do módulo e nome do pacote do pip
    $moduleToPip = @{
        'yaml' = 'pyyaml'
    }
    foreach($pkg in $criticalPackages) {
        $key = $pkg.ToLowerInvariant()
        $pipName = if($moduleToPip.ContainsKey($key)) { $moduleToPip[$key] } else { $key }
        if(-not $installed.ContainsKey($key) -and -not $installed.ContainsKey($pipName) -and -not $missing.Contains($pipName)) {
            $missing += $pipName
            Write-Warn "Pacote crítico do projeto '$pkg' não encontrado (pip: '$pipName')."
        }
    }
    
    $global:STATUS.MissingPackages = $missing
    if($missing.Count -gt 0){ Write-Warn ("Dependencias ausentes: {0}" -f ($missing -join ', ')) } else { Write-Ok 'Dependencias requeridas presentes.' }

    # Validar psycopg2 via import (pode ter sido instalado como psycopg2-binary)
    try {
        $checkPsy = & $VenvPy -c "import importlib.util; print('1' if importlib.util.find_spec('psycopg2') else '0')" 2>$null
        if($checkPsy -ne '1'){
            Write-Warn "Driver PostgreSQL 'psycopg2' não encontrado (instale 'psycopg2' ou 'psycopg2-binary')."
            if(-not $global:STATUS.MissingPackages.Contains('psycopg2')){ $global:STATUS.MissingPackages += 'psycopg2' }
        } else {
            Write-Ok "psycopg2 disponível na venv."
        }
    } catch {
        Write-Warn "Falha ao verificar psycopg2."
        if(-not $global:STATUS.MissingPackages.Contains('psycopg2')){ $global:STATUS.MissingPackages += 'psycopg2' }
    }
}

function Export-StatusJson {
    param([Parameter(Mandatory=$true)][string]$Path)
    try {
        $obj = [ordered]@{
            timestamp = (Get-Date).ToString('o')
            scriptVersion = $SCRIPT_VERSION
            status = $global:STATUS
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

# ------------------------ fluxo principal ------------------------
$_start = Get-Date
Write-Info 'Iniciando setup Python...'

# 1) Detectar Python
$abort = $false
$pyExe = Resolve-PythonExe -Preferred $Python
if(-not $pyExe){ Write-Err 'Python não encontrado no PATH.'; $global:HAS_ERROR = $true; $abort = $true }
if(-not $abort){
    $ver = Get-PythonVersion -Py $pyExe
    if([string]::IsNullOrWhiteSpace($ver)){ Write-Err 'Falha ao obter versão do Python.'; $global:HAS_ERROR = $true; $abort = $true }
}
if(-not $abort){
    $global:STATUS.Python = 'OK'
    $global:STATUS.PythonVersion = $ver
    Write-Ok ("Python detectado: $ver ($pyExe)")

    # 2) Verificar versão mínima 3.10
    $cmp = Compare-Version $ver '3.10.0'
    if($cmp -lt 0){ Write-Warn 'Versão mínima recomendada é 3.10+. Alguns recursos podem falhar.' }

    # 3) Criar/validar venv
    $venvPy = Get-VenvPython -Path $VenvPath
    if($OnlyCheck){
        if(-not (Test-Path $VenvPath -PathType Container)){
            Write-Warn 'Venv não encontrada (modo OnlyCheck).'
        } else {
            if(-not (Test-Path $venvPy -PathType Leaf)){
                Write-Warn 'Venv presente porem sem executavel Python.'
            } else {
                try {
                    $probe = & $venvPy -c "print('ok')" 2>&1
                    if($LASTEXITCODE -eq 0){
                        $global:STATUS.Venv = 'OK'
                        Write-Ok "Venv valida: $VenvPath"
                    } else {
                        Write-Warn "Venv presente mas Python nao executa corretamente."
                    }
                } catch {
                    Write-Warn "Erro ao verificar Python da venv: $_"
                }
            }
        }
    } else {
        try {
            Initialize-Venv -Py $pyExe -Path $VenvPath -Force:$Force
            $venvPy = Get-VenvPython -Path $VenvPath
            Install-Requirements -VenvPy $venvPy -ReqPath $Requirements -SkipUpgrade:$SkipPipUpgrade
        } catch {
            Write-Err $_.Exception.Message
            $global:HAS_ERROR = $true
        }
    }

    # 4) Validar dependências
    if((Test-Path $VenvPath) -and (Test-Path $venvPy)){
        Test-Requirements -VenvPy $venvPy -ReqPath $Requirements
    }
}

$_end = Get-Date
$global:STATUS.DuracaoSeg = [math]::Round(($_end - $_start).TotalSeconds,2)

# Resumo
Write-Host ''
if($NoColor){ Write-Host '========== RESUMO PYTHON ==========' } else { Write-Host '========== RESUMO PYTHON ==========' -ForegroundColor Magenta }

# Sugestões baseadas no estado
$suggestions = @()
if($global:STATUS.Python -ne 'OK'){ $suggestions += "Instalar Python e garantir que esta no PATH." }
if($OnlyCheck -and $global:STATUS.Venv -ne 'OK'){ $suggestions += "Execute sem -OnlyCheck para criar venv." }
elseif(-not $OnlyCheck -and $global:STATUS.Venv -ne 'OK'){ $suggestions += "Verificar permissoes da pasta ou problemas na instalacao Python." }
if($global:STATUS.Pip -ne 'OK' -and $global:STATUS.Venv -eq 'OK'){ $suggestions += "Executar pip manualmente para instalar dependencias." }

Write-Host ("Python:        {0}" -f $global:STATUS.Python)
Write-Host ("Py Version:    {0}" -f $global:STATUS.PythonVersion)
Write-Host ("Venv:          {0}" -f $global:STATUS.Venv)
Write-Host ("Pip:           {0}" -f $global:STATUS.Pip)
Write-Host ("Requirements:  {0}" -f $global:STATUS.Requirements)
if($global:STATUS.MissingPackages -and $global:STATUS.MissingPackages.Count -gt 0){ Write-Host ("Missing:       {0}" -f ($global:STATUS.MissingPackages -join ', ')) }
Write-Host ("Duracao (s):   {0}" -f $global:STATUS.DuracaoSeg)

# Exibir sugestões se houver
if($suggestions.Count -gt 0){
    Write-Host ""
    Write-Host "Sugestoes:"
    foreach($sugg in $suggestions){
        Write-Host " - $sugg"
    }
}

if($StatusJson){ Export-StatusJson -Path $StatusJson }

# Executar setup.dev.py se solicitado e o ambiente estiver pronto
if($RunSetupDev -and $global:STATUS.Venv -eq 'OK' -and -not $global:HAS_ERROR){
    $setupDevPath = Join-Path $ScriptRoot 'setup.dev.py'
    if(Test-Path $setupDevPath){
        Write-Host ""
        Write-Info "Executando setup.dev.py para verificar/configurar o ambiente completo..."
        $venvPy = Get-VenvPython -Path $VenvPath
        
        $setupCmd = "$venvPy `"$setupDevPath`""
        if(-not [string]::IsNullOrWhiteSpace($SetupDevArgs)){
            $setupCmd += " $SetupDevArgs"
        }
        
        Write-Host ""
        Write-Host "========== INICIANDO SETUP.DEV.PY ==========" -ForegroundColor Cyan
        
        # Executar o comando
        Invoke-Expression $setupCmd
        
        if($LASTEXITCODE -eq 0){
            Write-Ok "setup.dev.py concluído com sucesso."
        }
        else {
            Write-Warn "setup.dev.py concluído com código de saída $LASTEXITCODE. Verifique os logs acima."
        }
    }
    else {
        Write-Warn "setup.dev.py não encontrado em $setupDevPath"
    }
}

# No modo OnlyCheck, falhar apenas se Python global não está disponível
if($OnlyCheck){
    if($global:STATUS.Python -ne 'OK'){ 
        exit 1 
    } else { 
        # No modo OnlyCheck, venv ausente não é erro fatal
        exit 0 
    }
} else {
    if($global:HAS_ERROR){ exit 1 } else { exit 0 }
}
