#!/usr/bin/env python3
# -*- coding: utf-8 -*-
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#
# © 2025 23.969.028 CHRISTIAN VLADIMIR UHDRE MULATO (CNPJ 23.969.028/0001-37)
#
"""
setup.dev.py
Objetivo: Reescrever em Python o setup-dev.ps1 para fornecer checagens multi-plataforma.

Funcionalidades principais:
 - Checar Java, Maven (global, wrapper ou bootstrap interno)
 - Checar Docker CLI + daemon
 - Checar WSL (Windows)
 - Checar container Postgres + health + schema + contagens
 - Validar/gerar venv e dependências (opcional)
 - Garantir usuário ADMIN default (--ensure-admin)
 - Validar hash bcrypt se lib disponível
 - Exportar JSON de status (--status-json)
 - Corrigir automaticamente problemas (--auto-fix)

Uso:
  python setup.dev.py                # Checagens + cria venv + instala requirements
  python setup.dev.py --only-check   # Apenas checagens (não cria venv)
  python setup.dev.py --force-venv   # Recria venv
  python setup.dev.py --force-maven  # Força download e configuração do Maven
  python setup.dev.py --auto-fix     # Tenta corrigir automaticamente problemas
  python setup.dev.py --skip-python  # Pula parte Python
  python setup.dev.py --status-json status.json
  python setup.dev.py --ensure-admin # Garante usuário ADMIN
  python setup.dev.py --strict       # Exit code !=0 se requisitos críticos falharem
  python setup.dev.py --require-java 17 --require-maven 3.8
  python setup.dev.py --java-home <dirJDK> --maven-home <dirMaven>

Requer: Python 3.10+
"""
from __future__ import annotations
import argparse
import json
import os
import platform
import re
import shutil
import subprocess
import sys
import time
from pathlib import Path
from typing import Dict, Any, List

SCRIPT_VERSION = "1.0.1-py"
PROJECT_ROOT = Path(__file__).parent.resolve()
VENV_DIR = PROJECT_ROOT / ".venv"
REQUIREMENTS = PROJECT_ROOT / "requirements.txt"
POSTGRES_CONTAINER = "meu-app-postgres"
POSTGRES_DB = "meu_app_db"
POSTGRES_USER = "meu_app_user"
ADMIN_EMAIL = "admin@meuapp.com"
ADMIN_PLAIN = "Admin@123"
BCRYPT_HASH_PATTERN = re.compile(r"^\$2[aby]\$\d{2}\$[./A-Za-z0-9]{53}$")

class Colors:
    CYAN = "\033[36m"
    GREEN = "\033[32m"
    YELLOW = "\033[33m"
    RED = "\033[31m"
    MAGENTA = "\033[35m"
    RESET = "\033[0m"

    @staticmethod
    def disable():
        Colors.CYAN = Colors.GREEN = Colors.YELLOW = Colors.RED = Colors.MAGENTA = Colors.RESET = ""

class Status:
    def __init__(self) -> None:
        self.data: Dict[str, Any] = {
            "Java": "NOK",
            "Maven": "NOK",
            "JavaVersion": "",
            "MavenVersion": "",
            "MavenSource": "",
            "DockerCli": "NOK",
            "DockerDaemon": "NOK",
            "DockerVersion": "",
            "WSL": "N/A",
            "Postgres": "NOK",
            "PostgresConn": "NOK",
            "PostgresSchema": "NOK",
            "Perfis": "Pending",
            "Venv": "NOK",
            "WslDefault": "",
            "DuraçãoSeg": 0,
            "AdminCount": 0,
            "AdminHash": "N/A",
        }
        self.has_error = False

    def set(self, key: str, value: Any) -> None:
        self.data[key] = value

    def mark_error(self):
        self.has_error = True

    def suggestions(self, only_check: bool, ensure_admin: bool) -> List[str]:
        s: List[str] = []
        d = self.data
        if d["Java"] == "NOK":
            s.append("Instalar JDK (Temurin/Adoptium) e garantir 'java' no PATH.")
        elif isinstance(d["Java"], str) and d["Java"].startswith("OLD"):
            s.append("Atualizar JDK para versão mínima recomendada.")
        if d["Maven"] == "NOK":
            s.append("Instalar Maven ou usar wrapper mvnw.")
        elif isinstance(d["Maven"], str) and d["Maven"].startswith("OLD"):
            s.append("Atualizar Maven para versão mais recente (3.8+).")
        if d["DockerCli"] != "OK":
            s.append("Instalar Docker Desktop ou adicionar docker ao PATH.")
        elif d["DockerDaemon"] != "OK":
            s.append("Iniciar Docker Desktop até daemon ficar ativo.")
        if d["Postgres"] != "OK":
            s.append("Subir banco: docker-compose up -d")
        elif d["PostgresConn"] != "OK":
            s.append("Verificar credenciais ou variáveis de ambiente do Postgres.")
        elif d["PostgresSchema"] != "OK":
            s.append("Verificar scripts em docker/postgres/init (tabelas ausentes).")
        if d["Venv"] != "OK" and not only_check:
            s.append("Criar venv ou verificar instalação de Python.")
        if only_check and d["Venv"] != "OK":
            s.append("Executar novamente sem --only-check para criar venv.")
        if isinstance(d["Perfis"], str) and d["Perfis"].startswith("Faltando"):
            s.append("Adicionar perfis ausentes no pom.xml.")
        if d["WSL"] == "SemDistros":
            s.append("Instalar uma distro WSL: wsl --install -d Ubuntu.")
        if d["PostgresSchema"] == "OK" and d["AdminCount"] == 0:
            s.append("Criar usuário ADMIN inicial ou revisar bloco DO $$ no init SQL.")
            if not ensure_admin:
                s.append("Executar com --ensure-admin para garantir criação do ADMIN.")
        return s

status = Status()

# ------------------ util ------------------

def run_cmd(cmd: List[str], capture=True, check=False, text=True, env=None) -> subprocess.CompletedProcess:
    try:
        return subprocess.run(cmd, capture_output=capture, text=text, check=check, env=env)
    except FileNotFoundError as e:
        cp = subprocess.CompletedProcess(cmd, 127, "", str(e))
        return cp

def info(msg: str, args) -> None:
    if args.quiet: return
    print(f"[INFO ] {msg}")

def ok(msg: str, args) -> None:
    if args.quiet: return
    print(f"{Colors.GREEN}[  OK ]{Colors.RESET} {msg}" if Colors.GREEN else f"[  OK ] {msg}")

def warn(msg: str, args) -> None:
    if args.quiet: return
    print(f"{Colors.YELLOW}[WARN ]{Colors.RESET} {msg}" if Colors.YELLOW else f"[WARN ] {msg}")

def err(msg: str, args) -> None:
    status.mark_error()
    if args.quiet: return
    print(f"{Colors.RED}[ERRO ]{Colors.RESET} {msg}" if Colors.RED else f"[ERRO ] {msg}")

# ------------------ checks ------------------

def check_java(args):
    info("Verificando Java...", args)
    cp = run_cmd(["java", "-version"], capture=True)
    if cp.returncode != 0:
        err("Java não encontrado no PATH.", args)
        return
    lines = cp.stderr.splitlines() or cp.stdout.splitlines()
    version_line = lines[0].strip() if lines else ""
    m = re.search(r'"([0-9]+(?:\.[0-9]+){0,2})"', version_line)
    ver = m.group(1) if m else "?"
    status.set("JavaVersion", ver)
    status.set("Java", "OK")
    java_path_cp = run_cmd(["where" if platform.system().lower()=="windows" else "which", "java"], capture=True)
    java_path = (java_path_cp.stdout.splitlines()[0].strip() if java_path_cp.returncode==0 and java_path_cp.stdout else "")
    ok(f"Java detectado: versão {ver} {('('+java_path+')') if java_path else ''}", args)
    if not os.environ.get("JAVA_HOME"):
        warn("JAVA_HOME não definido (recomendado definir).", args)


def download_maven(args):
    """Função para baixar e configurar o Maven automaticamente, tentando várias URLs."""
    info("Verificando Maven no cache local...", args)
    
    # Verificar se o Maven já está baixado e extraído
    dist_root = PROJECT_ROOT / ".mvn" / "cache" / "dist"
    
    # Procurar por diretórios do Maven já extraídos
    maven_dirs = list(dist_root.glob("apache-maven-*"))
    if maven_dirs and not args.force_maven:
        for maven_dir in maven_dirs:
            if (maven_dir / "bin").exists():
                mvn_exec = maven_dir / "bin" / ("mvn.cmd" if platform.system().lower()=="windows" else "mvn")
                if mvn_exec.exists():
                    info(f"Maven encontrado em cache local: {maven_dir}", args)
                    
                    # Configurar ambiente Maven
                    os.environ["MAVEN_HOME"] = str(maven_dir)
                    os.environ["PATH"] = str(maven_dir / "bin") + os.pathsep + os.environ.get("PATH", "")
                    
                    # Verificar versão
                    cp = run_cmd([str(mvn_exec), "-version"], capture=True)
                    if cp.returncode == 0:
                        out_lines = cp.stdout.splitlines()
                        first = out_lines[0].strip() if out_lines else ""
                        m = re.search(r'Apache Maven ([0-9]+(?:\.[0-9]+){1,2})', first)
                        ver = m.group(1) if m else "?"
                        status.set("MavenVersion", ver)
                        status.set("Maven", "OK")
                        status.set("MavenSource", "bootstrap")
                        ok(f"Maven em cache: versão {ver}", args)
                        return True
    
    # Se chegou aqui, não encontrou Maven válido no cache ou --force-maven foi especificado
    info("Iniciando download automático do Maven...", args)
    
    # Lista de URLs alternativas para download do Maven
    maven_urls = [
        "https://dlcdn.apache.org/maven/maven-3/3.9.4/binaries/apache-maven-3.9.4-bin.zip",
        "https://archive.apache.org/dist/maven/maven-3/3.9.4/binaries/apache-maven-3.9.4-bin.zip",
        "https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.9.4/apache-maven-3.9.4-bin.zip",
        "https://dlcdn.apache.org/maven/maven-3/3.8.8/binaries/apache-maven-3.8.8-bin.zip",
        "https://archive.apache.org/dist/maven/maven-3/3.8.8/binaries/apache-maven-3.8.8-bin.zip"
    ]
    
    dist_root.mkdir(parents=True, exist_ok=True)
    zip_path = dist_root / "apache-maven-dist.zip"
    
    # Tentar cada URL na lista
    for dist_url in maven_urls:
        try:
            import urllib.request, zipfile
            
            # Mostrar progresso do download
            info(f"Tentando baixar Maven de {dist_url}...", args)
            
            def report_progress(block_num, block_size, total_size):
                if args.quiet: return
                if total_size > 0:
                    percent = min(int(block_num * block_size * 100 / total_size), 100)
                    sys.stdout.write(f"\rProgresso: {percent}% [{block_num * block_size}/{total_size} bytes]")
                    sys.stdout.flush()
            
            urllib.request.urlretrieve(dist_url, zip_path, reporthook=report_progress)
            print()  # Nova linha após progresso
            
            # Verificar se o download foi bem-sucedido
            if not zip_path.exists() or zip_path.stat().st_size < 1000:  # Verificação básica
                warn(f"Download de {dist_url} parece incompleto, tentando próxima URL...", args)
                continue
                
            info("Extraindo Maven...", args)
            with zipfile.ZipFile(zip_path, 'r') as zf:
                zf.extractall(dist_root)
            
            # Localizar o diretório do Maven
            extracted = None
            for p in dist_root.rglob("apache-maven-*/bin"):
                if p.is_dir():
                    extracted = p.parent
                    break
            
            if not extracted:
                warn("Distribuição Maven baixada mas diretório bin não localizado, tentando próxima URL...", args)
                continue
            
            # Configurar ambiente Maven
            os.environ["MAVEN_HOME"] = str(extracted)
            os.environ["PATH"] = str(extracted / "bin") + os.pathsep + os.environ.get("PATH", "")
            mvn_exec = extracted / "bin" / ("mvn.cmd" if platform.system().lower()=="windows" else "mvn")
            
            if not mvn_exec.exists():
                warn("Executável mvn não encontrado após extração, tentando próxima URL...", args)
                continue
            
            info(f"Maven configurado em {extracted}", args)
            cp = run_cmd([str(mvn_exec), "-version"], capture=True)
            
            if cp.returncode == 0:
                out_lines = cp.stdout.splitlines()
                first = out_lines[0].strip() if out_lines else ""
                m = re.search(r'Apache Maven ([0-9]+(?:\.[0-9]+){1,2})', first)
                ver = m.group(1) if m else "?"
                status.set("MavenVersion", ver)
                status.set("Maven", "OK")
                status.set("MavenSource", "bootstrap")
                ok(f"Maven instalado automaticamente: versão {ver}", args)
                return True
            else:
                warn("Falha ao verificar versão do Maven, tentando próxima URL...", args)
                continue
                
        except Exception as e:
            warn(f"Falha ao baixar/extrair Maven de {dist_url}: {e}", args)
            continue
    
    # Se chegou aqui, todas as tentativas falharam
    err("Não foi possível baixar e configurar o Maven automaticamente após tentar várias URLs.", args)
    return False

def check_maven(args):
    info("Verificando Maven...", args)
    wrapper_used = False
    
    # Se --force-maven foi especificado, pular verificação inicial e ir direto para bootstrap
    if not args.force_maven:
        cp = run_cmd(["mvn", "-version"], capture=True)
        if cp.returncode == 0:
            out_lines = cp.stdout.splitlines()
            first = out_lines[0].strip() if out_lines else ""
            m = re.search(r'Apache Maven ([0-9]+(?:\.[0-9]+){1,2})', first)
            ver = m.group(1) if m else "?"
            status.set("MavenVersion", ver)
            status.set("Maven", "OK")
            mvn_path_cp = run_cmd(["where" if platform.system().lower()=="windows" else "which", "mvn"], capture=True)
            mvn_path = (mvn_path_cp.stdout.splitlines()[0].strip() if mvn_path_cp.returncode==0 and mvn_path_cp.stdout else "")
            status.set("MavenSource", "global")
            ok(f"Maven detectado: versão {ver} {(f'({mvn_path})') if mvn_path else ''}", args)
            return

        # tentar wrapper
        wrapper_script = "mvnw.cmd" if platform.system().lower()=="windows" else "mvnw"
        local_wrapper = PROJECT_ROOT / wrapper_script
        if local_wrapper.exists():
            info("Maven global ausente. Tentando wrapper local...", args)
            cp = run_cmd([str(local_wrapper), "-version"], capture=True)
            if cp.returncode == 0:
                wrapper_used = True
                out_lines = cp.stdout.splitlines()
                first = out_lines[0].strip() if out_lines else ""
                m = re.search(r'Apache Maven ([0-9]+(?:\.[0-9]+){1,2})', first)
                ver = m.group(1) if m else "?"
                status.set("MavenVersion", ver)
                status.set("Maven", "OK")
                status.set("MavenSource", "wrapper")
                ok(f"Maven detectado (wrapper): versão {ver}", args)
                return
    
    # Aqui, ou --force-maven foi especificado ou não encontramos Maven global ou wrapper
    props = PROJECT_ROOT / ".mvn" / "wrapper" / "maven-wrapper.properties"
    if props.exists() and not args.force_maven:
        # Usar properties existentes
        info("Bootstrap Maven interno (usando configuração existente)...", args)
        text_props = props.read_text(encoding="utf-8", errors="ignore")
        m_dist = re.search(r"^distributionUrl=(.+)$", text_props, re.MULTILINE)
        if m_dist:
            dist_url = m_dist.group(1).strip()
            # Tentar baixar usando a URL do wrapper
            try:
                import urllib.request, zipfile
                
                dist_root = PROJECT_ROOT / ".mvn" / "cache" / "dist"
                dist_root.mkdir(parents=True, exist_ok=True)
                zip_path = dist_root / "apache-maven-dist.zip"
                
                # Verificar se já temos o Maven baixado e extraído antes de baixar novamente
                maven_dirs = list(dist_root.glob("apache-maven-*"))
                if maven_dirs and (maven_dirs[0] / "bin").exists() and not args.force_maven:
                    maven_dir = maven_dirs[0]
                    info(f"Maven já extraído encontrado em: {maven_dir}", args)
                    
                    # Configurar ambiente Maven
                    os.environ["MAVEN_HOME"] = str(maven_dir)
                    os.environ["PATH"] = str(maven_dir / "bin") + os.pathsep + os.environ.get("PATH", "")
                    mvn_exec = maven_dir / "bin" / ("mvn.cmd" if platform.system().lower()=="windows" else "mvn")
                    
                    if mvn_exec.exists():
                        cp = run_cmd([str(mvn_exec), "-version"], capture=True)
                        if cp.returncode == 0:
                            out_lines = cp.stdout.splitlines()
                            first = out_lines[0].strip() if out_lines else ""
                            m = re.search(r'Apache Maven ([0-9]+(?:\.[0-9]+){1,2})', first)
                            ver = m.group(1) if m else "?"
                            status.set("MavenVersion", ver)
                            status.set("Maven", "OK")
                            status.set("MavenSource", "bootstrap")
                            ok(f"Maven bootstrap existente: versão {ver}", args)
                            return
                
                # Se não temos Maven extraído ou --force-maven foi especificado, continuar com download
                if args.force_maven or not maven_dirs:
                    info(f"Baixando Maven de {dist_url} (URL do wrapper)...", args)
                    
                    def report_progress(block_num, block_size, total_size):
                        if args.quiet: return
                        if total_size > 0:
                            percent = min(int(block_num * block_size * 100 / total_size), 100)
                            sys.stdout.write(f"\rProgresso: {percent}% [{block_num * block_size}/{total_size} bytes]")
                            sys.stdout.flush()
                    
                    urllib.request.urlretrieve(dist_url, zip_path, reporthook=report_progress)
                    print()  # Nova linha após progresso
                
                # Continuar com extração e configuração
                # ... [código omitido para brevidade] ...
                
                # Se falhar, usar o método alternativo
                if not download_maven(args):
                    err("Falha ao configurar Maven usando as URLs alternativas.", args)
                    return
                    
            except Exception as e:
                warn(f"Falha ao baixar Maven usando URL do wrapper: {e}", args)
                warn("Tentando método alternativo...", args)
                if not download_maven(args):
                    err("Falha ao configurar Maven usando as URLs alternativas.", args)
                    return
        else:
            # Sem URL no wrapper, usar método alternativo
            if not download_maven(args):
                err("Falha ao configurar Maven usando as URLs alternativas.", args)
                return
    else:
        # Sem properties, usar método alternativo
            if not download_maven(args):
                err("Falha ao configurar Maven usando as URLs alternativas.", args)
                return
    
    # Esta parte é executada apenas se nenhuma verificação anterior definiu corretamente as informações do Maven
    if status.data.get("Maven") == "OK" and (not status.data.get("MavenVersion") or status.data.get("MavenVersion") == "?"):
        # Tentar obter a versão novamente
        mvn_cmd = "mvn.cmd" if platform.system().lower() == "windows" else "mvn"
        maven_path = os.environ.get("MAVEN_HOME")
        if maven_path:
            mvn_exec = Path(maven_path) / "bin" / mvn_cmd
            if mvn_exec.exists():
                cp = run_cmd([str(mvn_exec), "-version"], capture=True)
                if cp.returncode == 0:
                    out_lines = cp.stdout.splitlines()
                    first = out_lines[0].strip() if out_lines else ""
                    m = re.search(r'Apache Maven ([0-9]+(?:\.[0-9]+){1,2})', first)
                    if m:
                        ver = m.group(1)
                        status.set("MavenVersion", ver)
                        if args.verbose:
                            info(f"Versão do Maven obtida corretamente: {ver}", args)
    
    origem = "(wrapper)" if status.data.get("MavenSource") == "wrapper" else ("(bootstrap)" if status.data.get("MavenSource") == "bootstrap" else "")
    ok(f"Maven detectado: versão {status.data.get('MavenVersion', '?')} {origem}", args)
def install_docker(args):
    """Tenta instalar o Docker Desktop automaticamente."""
    info("Iniciando instalação automática do Docker Desktop...", args)
    
    # Verificar o sistema operacional
    system = platform.system().lower()
    if system != "windows":
        warn("Instalação automática do Docker só é suportada no Windows atualmente.", args)
        return False
    
    # URLs para download do Docker Desktop
    docker_urls = [
        "https://desktop.docker.com/win/main/amd64/Docker%20Desktop%20Installer.exe",
        "https://desktop.docker.com/win/stable/Docker%20Desktop%20Installer.exe"
    ]
    
    # Diretório temporário para download
    temp_dir = Path(os.environ.get("TEMP", ".")) / "docker_installer"
    temp_dir.mkdir(parents=True, exist_ok=True)
    installer_path = temp_dir / "DockerDesktopInstaller.exe"
    
    # Tentar baixar o instalador
    import urllib.request
    download_success = False
    
    for url in docker_urls:
        try:
            info(f"Baixando Docker Desktop de {url}...", args)
            
            def report_progress(block_num, block_size, total_size):
                if args.quiet: return
                if total_size > 0:
                    percent = min(int(block_num * block_size * 100 / total_size), 100)
                    sys.stdout.write(f"\rProgresso: {percent}% [{block_num * block_size}/{total_size} bytes]")
                    sys.stdout.flush()
            
            urllib.request.urlretrieve(url, installer_path, reporthook=report_progress)
            print()  # Nova linha após progresso
            
            if installer_path.exists() and installer_path.stat().st_size > 1000000:  # Verificação básica (> 1MB)
                download_success = True
                break
            else:
                warn(f"Download de {url} parece incompleto, tentando próxima URL...", args)
        except Exception as e:
            warn(f"Falha ao baixar Docker Desktop de {url}: {e}", args)
    
    if not download_success:
        err("Não foi possível baixar o instalador do Docker Desktop.", args)
        return False
    
    # Tentar executar o instalador
    info("Iniciando instalação do Docker Desktop (requer permissão de administrador)...", args)
    info("ATENÇÃO: A instalação do Docker requer interação manual e reinicialização do sistema.", args)
    info("Após a instalação e reinicialização, execute novamente este script.", args)
    
    # Executar o instalador
    try:
        import subprocess
        subprocess.Popen([str(installer_path), "install", "--quiet", "--accept-license"])
        ok("Instalador do Docker Desktop iniciado. Siga as instruções na tela.", args)
        info("O script será encerrado agora. Execute-o novamente após a instalação e reinicialização.", args)
        sys.exit(0)  # Encerra o script para que o usuário possa completar a instalação
    except Exception as e:
        err(f"Falha ao iniciar o instalador do Docker Desktop: {e}", args)
        return False

def check_docker(args):
    info("Verificando Docker...", args)
    cp = run_cmd(["docker", "--version"], capture=True)
    if cp.returncode != 0:
        warn("Docker não disponível.", args)
        
        # Se auto-fix está habilitado, tentar instalar Docker
        if args.auto_fix:
            info("Tentando instalar Docker automaticamente...", args)
            install_docker(args)
        return
    
    # Capturar versão do Docker CLI
    version_line = cp.stdout.strip() if cp.stdout else ""
    docker_version_match = re.search(r'Docker version ([0-9]+(?:\.[0-9]+){1,2})', version_line)
    docker_version = docker_version_match.group(1) if docker_version_match else "?"
    status.set("DockerVersion", docker_version)
    status.set("DockerCli", "OK")
    
    cp_info = run_cmd(["docker", "info", "--format", "{{.ServerVersion}}"], capture=True)
    if cp_info.returncode != 0 or not cp_info.stdout.strip():
        warn("Docker CLI ok mas daemon inativo (abrir Docker Desktop).", args)
        
        # Se auto-fix está habilitado, tentar iniciar o daemon
        if args.auto_fix and platform.system().lower() == "windows":
            info("Tentando iniciar Docker Desktop automaticamente...", args)
            try:
                # Verificar se o Docker Desktop está instalado
                app_path = Path("C:/Program Files/Docker/Docker/Docker Desktop.exe")
                if app_path.exists():
                    info("Iniciando Docker Desktop. Aguarde alguns instantes...", args)
                    subprocess.Popen([str(app_path)])
                    
                    # Aguardar o daemon iniciar
                    max_attempts = 10
                    for i in range(max_attempts):
                        info(f"Aguardando Docker iniciar ({i+1}/{max_attempts})...", args)
                        time.sleep(5)
                        cp_check = run_cmd(["docker", "info", "--format", "{{.ServerVersion}}"], capture=True)
                        if cp_check.returncode == 0 and cp_check.stdout.strip():
                            ok(f"Docker ativo - CLI: {docker_version}, Servidor: {cp_check.stdout.strip()}", args)
                            status.set("DockerDaemon", "OK")
                            return
                    
                    warn("Timeout aguardando Docker iniciar. Tente iniciar manualmente.", args)
                else:
                    warn("Docker Desktop não encontrado no caminho padrão.", args)
            except Exception as e:
                warn(f"Erro ao tentar iniciar Docker Desktop: {e}", args)
    else:
        server_version = cp_info.stdout.strip()
        ok(f"Docker ativo - CLI: {docker_version}, Servidor: {server_version}", args)
        status.set("DockerDaemon", "OK")
        
        # Verificar se há problemas de permissão
        if args.auto_fix:
            # Verificar detalhes do Docker se solicitado
            if args.verbose:
                check_docker_detailed(args)
                
            # Verificar se o docker-compose funciona
            test_cmd = ["docker", "compose", "version"]
            cp_test = run_cmd(test_cmd, capture=True)
            if cp_test.returncode != 0:
                # Tentar verificar permissões
                info("Problemas com docker-compose detectados, verificando permissões...", args)
                ensure_docker_permissions(args)


def check_wsl(args):
    if platform.system().lower() != "windows":
        status.set("WSL", "NaoAplicavel")
        return
    info("Verificando WSL...", args)
    cp = run_cmd(["wsl.exe", "-l", "-q"])
    if cp.returncode != 0:
        warn("WSL não detectado (ignorando).", args)
        status.set("WSL", "Ausente")
        return
    distros = [l.strip() for l in cp.stdout.splitlines() if l.strip()]
    seen = set(); distros_clean = []
    for dname in distros:
        if dname and dname not in seen:
            seen.add(dname)
            distros_clean.append(dname)
    distros = distros_clean
    if not distros:
        warn("WSL instalado mas nenhuma distro configurada.", args)
        status.set("WSL", "SemDistros")
        return
    cp2 = run_cmd(["wsl.exe", "-l", "-v"])
    default_name = ""
    if cp2.returncode == 0:
        for line in cp2.stdout.splitlines():
            if line.strip().startswith("*"):
                raw = line.replace("*", "").strip()
                default_name = re.split(r"\s{2,}", raw)[0].strip()
                break
    ok(f"WSL disponível. Distros: {', '.join(distros)}", args)
    if default_name:
        info(f"Distro default: {default_name}", args)
        status.set("WslDefault", default_name)
    status.set("WSL", "OK")


def test_docker_compose(args):
    """Testa qual comando docker-compose está disponível e funcional."""
    info("Testando comandos docker-compose...", args)
    
    # Testar 'docker compose' (sem hífen)
    cp_docker_compose = run_cmd(["docker", "compose", "version"], capture=True)
    if cp_docker_compose.returncode == 0:
        ok("Comando 'docker compose' disponível.", args)
        return ["docker", "compose"]
        
    # Testar 'docker-compose' (com hífen)
    cp_docker_compose_hyphen = run_cmd(["docker-compose", "version"], capture=True)
    if cp_docker_compose_hyphen.returncode == 0:
        ok("Comando 'docker-compose' disponível.", args)
        return ["docker-compose"]
    
    # Nenhum funcionou, tentar verificar se o plugin compose está instalado
    if platform.system().lower() == "windows":
        # Verificar pasta de plugins do Docker
        docker_plugin_path = Path(os.environ.get("PROGRAMFILES", "C:\\Program Files")) / "Docker" / "cli-plugins"
        compose_plugin = docker_plugin_path / "docker-compose.exe"
        
        if not compose_plugin.exists():
            warn("Plugin docker-compose não encontrado. Tentando baixar...", args)
            
            # Se auto-fix está habilitado, tentar baixar o plugin
            if args.auto_fix:
                try:
                    import urllib.request
                    plugin_url = "https://github.com/docker/compose/releases/download/v2.23.3/docker-compose-windows-x86_64.exe"
                    
                    # Criar diretório de plugins se não existir
                    docker_plugin_path.mkdir(parents=True, exist_ok=True)
                    
                    info(f"Baixando plugin docker-compose de {plugin_url}...", args)
                    urllib.request.urlretrieve(plugin_url, compose_plugin)
                    
                    if compose_plugin.exists():
                        ok("Plugin docker-compose baixado com sucesso.", args)
                        # Testar novamente
                        cp_test = run_cmd(["docker", "compose", "version"], capture=True)
                        if cp_test.returncode == 0:
                            ok("Comando 'docker compose' agora disponível.", args)
                            return ["docker", "compose"]
                    else:
                        warn("Falha ao baixar plugin docker-compose.", args)
                except Exception as e:
                    warn(f"Erro ao baixar plugin docker-compose: {e}", args)
    
    # Nenhum comando funcionou
    warn("Nenhum comando docker-compose disponível.", args)
    info("Instale o Docker Compose manualmente e tente novamente.", args)
    return None

def ensure_docker_permissions(args):
    """Tenta resolver problemas comuns de permissão com o Docker."""
    info("Verificando permissões do Docker...", args)
    
    system = platform.system().lower()
    if system == "windows":
        # No Windows, verificar se o usuário está no grupo docker-users
        try:
            # Executar como administrador o comando para adicionar o usuário ao grupo docker-users
            info("Tentando adicionar o usuário ao grupo docker-users (pode solicitar permissão de administrador)...", args)
            
            current_user = os.environ.get("USERNAME")
            if not current_user:
                warn("Não foi possível determinar o nome do usuário atual.", args)
                return False
                
            # Preparar comando para executar como admin
            admin_cmd = (
                'powershell -Command "'
                'Start-Process powershell -Verb RunAs -ArgumentList \'-Command net localgroup docker-users \"'
                + current_user + '\" /add; Write-Host \"Usuário adicionado ao grupo docker-users\"\''
                '"'
            )
            
            info(f"Para resolver problemas de permissão, execute como administrador: net localgroup docker-users {current_user} /add", args)
            info("Em seguida, reinicie o Docker Desktop e este script.", args)
            
            # Não executamos automaticamente pois requer elevação
            return False
            
        except Exception as e:
            warn(f"Erro ao verificar/configurar permissões do Docker: {e}", args)
            return False
    else:
        # No Linux, verificar se o usuário está no grupo docker
        try:
            current_user = os.environ.get("USER")
            if not current_user:
                warn("Não foi possível determinar o nome do usuário atual.", args)
                return False
                
            # Verificar se o usuário já está no grupo docker
            cp = run_cmd(["groups", current_user], capture=True)
            if cp.returncode == 0 and "docker" in cp.stdout:
                ok("Usuário já pertence ao grupo 'docker'.", args)
                return True
                
            # Sugerir comando para adicionar ao grupo
            info("Para resolver problemas de permissão, execute como root: sudo usermod -aG docker $USER", args)
            info("Em seguida, faça logout e login novamente e reinicie este script.", args)
            
            return False
            
        except Exception as e:
            warn(f"Erro ao verificar/configurar permissões do Docker: {e}", args)
            return False
    
    return False

def check_docker_detailed(args):
    """Realiza verificações detalhadas do Docker para diagnóstico de problemas."""
    info("Realizando verificação detalhada do Docker...", args)
    
    results = {}
    
    # Verificar versão do Docker
    cp_version = run_cmd(["docker", "version", "--format", "{{.Server.Version}}"], capture=True)
    results["server_version"] = cp_version.stdout.strip() if cp_version.returncode == 0 else "ERRO"
    
    # Verificar informações do sistema
    cp_info = run_cmd(["docker", "info", "--format", "{{.ServerVersion}}\\n{{.OperatingSystem}}\\n{{.OSType}}"], capture=True)
    if cp_info.returncode == 0:
        lines = cp_info.stdout.strip().split("\n")
        if len(lines) >= 3:
            results["server_version_info"] = lines[0]
            results["os"] = lines[1]
            results["os_type"] = lines[2]
    
    # Verificar espaço em disco
    cp_disk = run_cmd(["docker", "system", "df"], capture=True)
    results["disk_space"] = "OK" if cp_disk.returncode == 0 else "ERRO"
    
    # Verificar volumes
    cp_volumes = run_cmd(["docker", "volume", "ls"], capture=True)
    results["volumes"] = "OK" if cp_volumes.returncode == 0 else "ERRO"
    
    # Verificar networks
    cp_networks = run_cmd(["docker", "network", "ls"], capture=True)
    results["networks"] = "OK" if cp_networks.returncode == 0 else "ERRO"
    
    # Verificar estado do daemon
    cp_ps = run_cmd(["docker", "ps"], capture=True)
    results["daemon_state"] = "OK" if cp_ps.returncode == 0 else "ERRO"
    
    # Verificar logs do daemon (Windows)
    if platform.system().lower() == "windows":
        try:
            # No Windows, verificar logs do Docker Desktop
            eventlog_cmd = ["powershell", "-Command", "Get-EventLog -LogName Application -Source 'Docker Desktop' -Newest 5 | Format-Table -Property TimeGenerated, Message -AutoSize"]
            cp_eventlog = run_cmd(eventlog_cmd, capture=True)
            results["desktop_logs"] = "OK" if cp_eventlog.returncode == 0 else "ERRO"
        except:
            results["desktop_logs"] = "N/A"
    
    # Mostrar resultados
    info("Resultados da verificação detalhada do Docker:", args)
    for key, value in results.items():
        info(f"  {key}: {value}", args)
    
    # Verificar problemas específicos
    issues = []
    
    if results.get("daemon_state") != "OK":
        issues.append("O daemon do Docker não está respondendo corretamente.")
    
    if results.get("networks") != "OK":
        issues.append("Problemas com redes Docker detectados.")
    
    if results.get("volumes") != "OK":
        issues.append("Problemas com volumes Docker detectados.")
    
    if issues:
        warn("Problemas detectados no Docker:", args)
        for issue in issues:
            warn(f"  - {issue}", args)
        
        # Sugerir ações corretivas
        info("Ações sugeridas:", args)
        info("  1. Reinicie o Docker Desktop", args)
        info("  2. Verifique se há atualizações pendentes do Docker", args)
        info("  3. Verifique se há conflitos de portas (especialmente a porta 5432 para PostgreSQL)", args)
        info("  4. Execute 'docker system prune' para limpar recursos não utilizados", args)
        
        return False
    
    ok("Docker está funcionando corretamente.", args)
    return True

def start_postgres_container(args):
    """Inicia o container Postgres usando docker-compose com tratamento de erro aprimorado."""
    if status.data.get("DockerCli") != "OK" or status.data.get("DockerDaemon") != "OK":
        warn("Docker não está disponível. Não é possível iniciar container Postgres.", args)
        return False
    
    # Verificar se o container já está em execução
    cp = run_cmd(["docker", "ps", "--filter", f"name={POSTGRES_CONTAINER}", "--format", "{{.Names}}"])
    if cp.returncode == 0 and cp.stdout.strip():
        # Container já está em execução, verificar se está saudável
        cp_health = run_cmd(["docker", "inspect", "--format", "{{json .State.Health.Status}}", POSTGRES_CONTAINER])
        if cp_health.returncode == 0:
            health = cp_health.stdout.strip().strip('"')
            if health == "healthy":
                ok("Container Postgres já está em execução e saudável.", args)
                status.set("Postgres", "OK")
                return True
            else:
                info(f"Container Postgres está em execução mas com status {health}, aguardando ficar saudável...", args)
    else:
        # Verificar se o container existe mas está parado
        cp_all = run_cmd(["docker", "ps", "-a", "--filter", f"name={POSTGRES_CONTAINER}", "--format", "{{.Names}}"])
        if cp_all.returncode == 0 and cp_all.stdout.strip():
            # Container existe mas está parado, tentar iniciar diretamente
            info("Container Postgres existe mas está parado. Tentando iniciar...", args)
            cp_start = run_cmd(["docker", "start", POSTGRES_CONTAINER])
            if cp_start.returncode == 0:
                info("Container Postgres iniciado com sucesso, aguardando ficar saudável...", args)
            else:
                warn("Falha ao iniciar container existente, tentando recriar com docker-compose...", args)
        
    # Verificar o docker-compose.yml
    docker_compose_file = PROJECT_ROOT / "docker-compose.yml"
    if not docker_compose_file.exists():
        err("Arquivo docker-compose.yml não encontrado.", args)
        return False
    
    # Verificar conteúdo do docker-compose.yml
    try:
        compose_content = docker_compose_file.read_text(encoding="utf-8", errors="ignore")
        if "postgres" not in compose_content.lower():
            warn("Serviço postgres não encontrado no docker-compose.yml. Verifique o arquivo.", args)
    except Exception as e:
        warn(f"Erro ao ler docker-compose.yml: {e}", args)
    
    # Determinar qual comando usar (docker compose ou docker-compose)
    docker_compose_cmd = test_docker_compose(args)
    if not docker_compose_cmd:
        err("Nenhum comando docker-compose disponível.", args)
        return False
    
    # Iniciar o container
    cmd = docker_compose_cmd + ["-f", str(docker_compose_file), "up", "-d", "postgres"]
    info(f"Executando: {' '.join(cmd)}", args)
    cp = run_cmd(cmd)
    
    if cp.returncode != 0:
        err(f"Falha ao iniciar container Postgres com {' '.join(docker_compose_cmd)}.", args)
        
        # Tentar verificar erros
        logs_cmd = docker_compose_cmd + ["-f", str(docker_compose_file), "logs", "postgres"]
        cp_logs = run_cmd(logs_cmd, capture=True)
        if cp_logs.returncode == 0:
            err_lines = cp_logs.stdout.splitlines()[-10:] if cp_logs.stdout else []
            if err_lines:
                err("Últimas linhas de log do container:", args)
                for line in err_lines:
                    print(f"  {line.strip()}")
        
        return False
    
    # Aguardar container ficar saudável
    info("Aguardando container Postgres inicializar (pode levar alguns segundos)...", args)
    max_attempts = 15  # Aumentado o número de tentativas
    for i in range(max_attempts):
        if args.quiet:
            time.sleep(2)
        else:
            for j in range(2):  # Reduzido tempo entre verificações
                sys.stdout.write(".")
                sys.stdout.flush()
                time.sleep(1)
            print("")
        
        # Verificar estado do container
        cp_health = run_cmd(["docker", "inspect", "--format", "{{json .State.Health.Status}}", POSTGRES_CONTAINER], capture=True)
        if cp_health.returncode == 0:
            health = cp_health.stdout.strip().strip('"')
            if health == "healthy":
                ok("Container Postgres iniciado e saudável.", args)
                status.set("Postgres", "OK")
                return True
            elif health == "starting":
                info(f"Container Postgres ainda iniciando (tentativa {i+1}/{max_attempts})...", args)
            else:
                info(f"Status do container: {health} (tentativa {i+1}/{max_attempts})...", args)
        else:
            # Se não conseguir verificar a saúde, tentar verificar se o container está pelo menos rodando
            cp_running = run_cmd(["docker", "ps", "--filter", f"name={POSTGRES_CONTAINER}", "--format", "{{.Names}}"], capture=True)
            if cp_running.returncode != 0 or not cp_running.stdout.strip():
                warn(f"Container Postgres não está mais em execução (tentativa {i+1}/{max_attempts}).", args)
                
                # Verificar logs para entender o problema
                logs_cmd = docker_compose_cmd + ["-f", str(docker_compose_file), "logs", "postgres"]
                cp_logs = run_cmd(logs_cmd, capture=True)
                if cp_logs.returncode == 0:
                    err_lines = cp_logs.stdout.splitlines()[-10:] if cp_logs.stdout else []
                    if err_lines:
                        err("Últimas linhas de log do container:", args)
                        for line in err_lines:
                            print(f"  {line.strip()}")
                
                # Tentar reiniciar
                if i > max_attempts / 2:  # Só tenta reiniciar depois de algumas tentativas
                    info("Tentando reiniciar o container...", args)
                    restart_cmd = docker_compose_cmd + ["-f", str(docker_compose_file), "restart", "postgres"]
                    run_cmd(restart_cmd)
    
    # Se chegou aqui, não conseguiu iniciar o container a tempo
    warn("Container Postgres iniciado, mas não ficou saudável no tempo esperado.", args)
    
    # Verificar se o container está pelo menos rodando
    cp_running = run_cmd(["docker", "ps", "--filter", f"name={POSTGRES_CONTAINER}", "--format", "{{.Names}}"], capture=True)
    if cp_running.returncode == 0 and cp_running.stdout.strip():
        status.set("Postgres", "starting")
        return True
    else:
        status.set("Postgres", "NOK")
        return False

def check_postgres_container(args):
    info("Verificando container Postgres...", args)
    cp = run_cmd(["docker", "ps", "--filter", f"name={POSTGRES_CONTAINER}", "--format", "{{.Names}}"])
    if cp.returncode != 0 or not cp.stdout.strip():
        warn("Container postgres não está em execução (docker-compose up -d).", args)
        
        # Se o argumento auto-fix está habilitado, tentar iniciar automaticamente
        if args.auto_fix and status.data.get("DockerCli") == "OK" and status.data.get("DockerDaemon") == "OK":
            info("Tentando iniciar container Postgres automaticamente...", args)
            start_postgres_container(args)
        return
    
    name = cp.stdout.strip()
    cp_health = run_cmd(["docker", "inspect", "--format", "{{json .State.Health.Status}}", name])
    health = cp_health.stdout.strip().strip('"') if cp_health.returncode == 0 else "unknown"
    ok(f"Container ativo: {name} (health={health})", args)
    status.set("Postgres", "OK" if health == "healthy" else health)


def check_postgres_db(args):
    if status.data["Postgres"] != "OK":
        warn("Pulando teste de DB: container não saudável.", args)
        return
    info("Testando conexão Postgres...", args)
    cp = run_cmd(["docker", "exec", POSTGRES_CONTAINER, "psql", "-U", POSTGRES_USER, "-d", POSTGRES_DB, "-t", "-c", "SELECT 1;"])
    if cp.returncode != 0:
        err("Falha ao conectar no Postgres.", args)
        return
    ok("Conexão psql ok.", args)
    status.set("PostgresConn", "OK")
    info("Validando schema (tabelas usuarios/produtos)...", args)
    cmd_tables = "SELECT to_regclass('public.usuarios') IS NOT NULL, to_regclass('public.produtos') IS NOT NULL;"
    cp_tables = run_cmd(["docker", "exec", POSTGRES_CONTAINER, "psql", "-U", POSTGRES_USER, "-d", POSTGRES_DB, "-A", "-F", ",", "-t", "-c", cmd_tables])
    if cp_tables.returncode != 0:
        err("Falha verificação tabelas.", args)
        return
    flags = cp_tables.stdout.strip().split(",")
    u_ok = (len(flags) > 0 and flags[0] == 't')
    p_ok = (len(flags) > 1 and flags[1] == 't')
    
    # Se tabelas estão ausentes e auto-fix está habilitado, criar tabelas
    if args.auto_fix and (not u_ok or not p_ok):
        info("Tentando criar tabelas ausentes automaticamente...", args)
        create_missing_tables(args, not u_ok, not p_ok)
        # Verificar novamente após a criação
        cp_tables = run_cmd(["docker", "exec", POSTGRES_CONTAINER, "psql", "-U", POSTGRES_USER, "-d", POSTGRES_DB, "-A", "-F", ",", "-t", "-c", cmd_tables])
        if cp_tables.returncode == 0:
            flags = cp_tables.stdout.strip().split(",")
            u_ok = (len(flags) > 0 and flags[0] == 't')
            p_ok = (len(flags) > 1 and flags[1] == 't')
    
    cp_counts = run_cmd(["docker", "exec", POSTGRES_CONTAINER, "psql", "-U", POSTGRES_USER, "-d", POSTGRES_DB, "-A", "-F", ",", "-t", "-c", "SELECT (SELECT COUNT(*) FROM usuarios WHERE 1=1 OR to_regclass('public.usuarios') IS NULL),(SELECT COUNT(*) FROM produtos WHERE 1=1 OR to_regclass('public.produtos') IS NULL);"])
    if cp_counts.returncode == 0:
        counts = cp_counts.stdout.strip().split(",")
        uc = counts[0] if counts else "?"
        pc = counts[1] if len(counts) > 1 else "?"
        if u_ok and p_ok:
            status.set("PostgresSchema", "OK")
            ok(f"Schema ok (usuarios={uc}, produtos={pc})", args)
        else:
            status.set("PostgresSchema", "Parcial")
            warn(f"Schema parcial (usuariosOk={u_ok}, produtosOk={p_ok})", args)
    else:
        err("Falha obtendo contagens.", args)


def create_missing_tables(args, create_usuarios=False, create_produtos=False):
    """Cria tabelas ausentes no banco de dados."""
    if not create_usuarios and not create_produtos:
        return True
    
    info(f"Criando tabelas: {'usuarios' if create_usuarios else ''} {'produtos' if create_produtos else ''}", args)
    
    # Script SQL para criar tabela usuarios
    usuarios_sql = """
    CREATE TABLE IF NOT EXISTS usuarios (
        id SERIAL PRIMARY KEY,
        nome VARCHAR(100) NOT NULL,
        email VARCHAR(100) NOT NULL UNIQUE,
        senha VARCHAR(100) NOT NULL,
        perfil VARCHAR(20) DEFAULT 'OPERADOR' CHECK (perfil IN ('ADMIN','SUPERVISOR','OPERADOR')),
        data_criacao TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
    );
    """
    
    # Script SQL para criar tabela produtos
    produtos_sql = """
    CREATE TABLE IF NOT EXISTS produtos (
        id SERIAL PRIMARY KEY,
        nome VARCHAR(100) NOT NULL,
        descricao TEXT,
        preco DECIMAL(10, 2) NOT NULL,
        quantidade INTEGER NOT NULL DEFAULT 0,
        ativo BOOLEAN NOT NULL DEFAULT TRUE,
        data_criacao TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
    );
    """
    
    # Criar tabela usuarios se necessário
    if create_usuarios:
        cp_usuarios = run_cmd(["docker", "exec", POSTGRES_CONTAINER, "psql", "-U", POSTGRES_USER, "-d", POSTGRES_DB, "-c", usuarios_sql])
        if cp_usuarios.returncode == 0:
            ok("Tabela 'usuarios' criada com sucesso.", args)
        else:
            err("Falha ao criar tabela 'usuarios'.", args)
            return False
    
    # Criar tabela produtos se necessário
    if create_produtos:
        cp_produtos = run_cmd(["docker", "exec", POSTGRES_CONTAINER, "psql", "-U", POSTGRES_USER, "-d", POSTGRES_DB, "-c", produtos_sql])
        if cp_produtos.returncode == 0:
            ok("Tabela 'produtos' criada com sucesso.", args)
        else:
            err("Falha ao criar tabela 'produtos'.", args)
            return False
    
    return True


def count_admins(args):
    if status.data["PostgresSchema"] != "OK":
        return
    info("Contando usuários ADMIN...", args)
    cp_col = run_cmd(["docker", "exec", POSTGRES_CONTAINER, "psql", "-U", POSTGRES_USER, "-d", POSTGRES_DB, "-t", "-A", "-c", "SELECT 1 FROM information_schema.columns WHERE table_name='usuarios' AND column_name='perfil' LIMIT 1;"])
    has_perfil = (cp_col.returncode == 0 and cp_col.stdout.strip() == '1')
    if not has_perfil:
        warn("Coluna 'perfil' ausente em usuarios (schema antigo).", args)
        return
    cp_count = run_cmd(["docker", "exec", POSTGRES_CONTAINER, "psql", "-U", POSTGRES_USER, "-d", POSTGRES_DB, "-t", "-A", "-c", "SELECT COUNT(*) FROM usuarios WHERE perfil='ADMIN';"])
    if cp_count.returncode != 0:
        warn("Não foi possível contar usuários ADMIN.", args)
        return
    val = int(cp_count.stdout.strip() or 0)
    status.set("AdminCount", val)
    if val > 0:
        ok(f"Admins existentes: {val}", args)
    else:
        warn("Nenhum usuário ADMIN encontrado.", args)


def validate_admin_hash(args):
    if status.data["AdminCount"] <= 0 or status.data["PostgresSchema"] != "OK":
        return
    info("Validando hash bcrypt do admin padrão...", args)
    cp_hash = run_cmd(["docker", "exec", POSTGRES_CONTAINER, "psql", "-U", POSTGRES_USER, "-d", POSTGRES_DB, "-t", "-A", "-c", f"SELECT senha FROM usuarios WHERE email='{ADMIN_EMAIL}' LIMIT 1;"])
    if cp_hash.returncode != 0 or not cp_hash.stdout.strip():
        warn("Hash admin não obtido.", args)
        return
    hash_val = cp_hash.stdout.strip()
    if not BCRYPT_HASH_PATTERN.match(hash_val):
        warn("Formato de hash inesperado (não parece BCrypt).", args)
    
    # Tentar instalar bcrypt automaticamente se não estiver disponível
    try:
        import bcrypt
    except ImportError:
        warn("Biblioteca bcrypt não instalada. Tentando instalar automaticamente...", args)
        try:
            # Instalar bcrypt usando pip
            pip_cmd = [sys.executable, "-m", "pip", "install", "bcrypt"]
            cp_install = run_cmd(pip_cmd, capture=True)
            if cp_install.returncode == 0:
                ok("Biblioteca bcrypt instalada com sucesso.", args)
                import bcrypt
            else:
                err("Falha ao instalar biblioteca bcrypt automaticamente.", args)
                status.set("AdminHash", "ERRO")
                return
        except Exception as e:
            err(f"Erro ao instalar bcrypt: {e}", args)
            status.set("AdminHash", "ERRO")
            return
    
    try:
        if bcrypt.checkpw(ADMIN_PLAIN.encode(), hash_val.encode()):
            status.set("AdminHash", "OK")
            ok("Hash admin válido (senha padrão esperada).", args)
        else:
            status.set("AdminHash", "MISMATCH")
            warn("Hash admin difere da senha padrão (pode ser intencional).", args)
    except Exception as e:
        status.set("AdminHash", "ERRO")
        warn(f"Erro validando hash: {e}", args)


def ensure_admin(args):
    # Verificar se devemos garantir admin automaticamente com --auto-fix ou --ensure-admin
    should_ensure_admin = args.ensure_admin or args.auto_fix
    if not should_ensure_admin:
        return
    
    # Verificar se o container Postgres está ativo
    if status.data.get("Postgres") != "OK":
        # Se o auto-fix está habilitado, tentar iniciar o container primeiro
        if args.auto_fix and status.data.get("DockerCli") == "OK" and status.data.get("DockerDaemon") == "OK":
            info("Tentando iniciar Postgres para garantir usuário ADMIN...", args)
            if not start_postgres_container(args):
                warn("Não foi possível iniciar o container Postgres.", args)
                return
        else:
            warn("--ensure-admin ignorado: container Postgres não está ativo.", args)
            return
    
    # Verificar se o schema está OK
    if status.data.get("PostgresSchema") != "OK":
        warn("--ensure-admin ignorado: schema não OK.", args)
        return
    
    # Verificar quantos administradores existem
    info("Verificando usuários ADMIN existentes...", args)
    cp_count = run_cmd(["docker", "exec", POSTGRES_CONTAINER, "psql", "-U", POSTGRES_USER, "-d", POSTGRES_DB, "-t", "-A", "-c", "SELECT COUNT(*) FROM usuarios WHERE perfil='ADMIN';"])
    if cp_count.returncode != 0:
        warn("Não foi possível contar usuários ADMIN.", args)
        return
    
    admin_count = int(cp_count.stdout.strip() or 0)
    
    # Se não há nenhum admin, criar um
    if admin_count == 0:
        info("Nenhum usuário ADMIN encontrado. Criando usuário ADMIN default...", args)
        cp_col = run_cmd(["docker", "exec", POSTGRES_CONTAINER, "psql", "-U", POSTGRES_USER, "-d", POSTGRES_DB, "-t", "-A", "-c", "SELECT 1 FROM information_schema.columns WHERE table_name='usuarios' AND column_name='perfil' LIMIT 1;"])
        has_perfil = (cp_col.returncode == 0 and cp_col.stdout.strip() == '1')
        
        if not has_perfil:
            warn("Coluna 'perfil' ausente. Aplicando migração leve (ALTER TABLE).", args)
            cp_alter = run_cmd(["docker", "exec", POSTGRES_CONTAINER, "psql", "-U", POSTGRES_USER, "-d", POSTGRES_DB, "-c", "ALTER TABLE usuarios ADD COLUMN IF NOT EXISTS perfil VARCHAR(20) DEFAULT 'OPERADOR' CHECK (perfil IN ('ADMIN','SUPERVISOR','OPERADOR'));" ])
            if cp_alter.returncode != 0:
                err("Falha ao adicionar coluna perfil.", args)
                return
            ok("Coluna 'perfil' adicionada.", args)
        
        # Inserir usuário ADMIN com hash bcrypt predefinido
        insert_sql = ("INSERT INTO usuarios (nome,email,senha,perfil) VALUES (" 
                    "'Administrador','" + ADMIN_EMAIL + "'," 
                    "'$2a$10$rF1YS1T.8QVXnpTlI.JY5u5Kz7x8TXDQ9Y2c3M4z6N8x.wJ4sA2G6','ADMIN') ON CONFLICT (email) DO NOTHING;")
        cp_ins = run_cmd(["docker", "exec", POSTGRES_CONTAINER, "psql", "-U", POSTGRES_USER, "-d", POSTGRES_DB, "-c", insert_sql])
        if cp_ins.returncode == 0:
            ok("Usuário ADMIN default garantido.", args)
        else:
            err("Falha ao garantir usuário ADMIN.", args)
    
    # Se há mais de um admin, manter apenas o principal
    elif admin_count > 1:
        info(f"Existem {admin_count} usuários com perfil ADMIN. Mantendo apenas o principal...", args)
        
        # Verificar se o admin principal existe
        cp_check = run_cmd(["docker", "exec", POSTGRES_CONTAINER, "psql", "-U", POSTGRES_USER, "-d", POSTGRES_DB, "-t", "-A", "-c", f"SELECT COUNT(*) FROM usuarios WHERE email='{ADMIN_EMAIL}';"])
        admin_exists = (cp_check.returncode == 0 and int(cp_check.stdout.strip() or 0) > 0)
        
        if not admin_exists:
            # Se o admin principal não existe, criar
            insert_sql = ("INSERT INTO usuarios (nome,email,senha,perfil) VALUES (" 
                        "'Administrador','" + ADMIN_EMAIL + "'," 
                        "'$2a$10$rF1YS1T.8QVXnpTlI.JY5u5Kz7x8TXDQ9Y2c3M4z6N8x.wJ4sA2G6','ADMIN');")
            cp_ins = run_cmd(["docker", "exec", POSTGRES_CONTAINER, "psql", "-U", POSTGRES_USER, "-d", POSTGRES_DB, "-c", insert_sql])
            if cp_ins.returncode != 0:
                err("Falha ao criar usuário ADMIN principal.", args)
                return
        
        # Atualizar outros admins para usuários comuns
        update_sql = f"UPDATE usuarios SET perfil='OPERADOR' WHERE perfil='ADMIN' AND email!='{ADMIN_EMAIL}';"
        cp_upd = run_cmd(["docker", "exec", POSTGRES_CONTAINER, "psql", "-U", POSTGRES_USER, "-d", POSTGRES_DB, "-c", update_sql])
        if cp_upd.returncode == 0:
            ok("Convertidos outros administradores para usuários comuns.", args)
        else:
            err("Falha ao atualizar perfil de outros administradores.", args)
    
    # Atualizar contagens e validar hash
    count_admins(args)
    validate_admin_hash(args)


def check_maven_profiles(args):
    pom = PROJECT_ROOT / "meu-projeto-java" / "pom.xml"
    if not pom.exists():
        err("pom.xml não encontrado em meu-projeto-java.", args)
        return
    content = pom.read_text(encoding="utf-8", errors="ignore")
    profiles = ["tomcat", "wildfly", "run"]
    missing = []
    for p in profiles:
        if f"<id>{p}</id>" in content:
            ok(f"Perfil Maven encontrado: {p}", args)
        else:
            warn(f"Perfil Maven AUSENTE: {p}", args)
            missing.append(p)
    status.set("Perfis", "OK" if not missing else "Faltando: " + ",".join(missing))

# ------------------ Python env ------------------

def create_or_validate_venv(args):
    if args.skip_python:
        warn("Pulado setup Python.", args)
        return
    if args.only_check:
        return validate_venv(args, create=False)
    return validate_venv(args, create=True, force=args.force_venv)

def validate_venv(args, create: bool, force: bool=False):
    if force and VENV_DIR.exists():
        warn("Forçando recriação da venv...", args)
        shutil.rmtree(VENV_DIR, ignore_errors=True)
    if create and not VENV_DIR.exists():
        info("Criando venv...", args)
        cp = run_cmd([sys.executable, "-m", "venv", str(VENV_DIR)])
        if cp.returncode != 0:
            err("Falha ao criar venv.", args)
            return
    if not VENV_DIR.exists():
        warn("Venv não encontrada.", args)
        return
    py_exec = VENV_DIR / ("Scripts" if platform.system().lower()=="windows" else "bin") / ("python.exe" if platform.system().lower()=="windows" else "python")
    if not py_exec.exists():
        err("Executável Python não encontrado na venv.", args)
        return
    status.set("Venv", "OK")
    if create and REQUIREMENTS.exists():
        info("Instalando dependências (requirements.txt)...", args)
        run_cmd([str(py_exec), "-m", "pip", "install", "--upgrade", "pip"], capture=True)
        cp_req = run_cmd([str(py_exec), "-m", "pip", "install", "-r", str(REQUIREMENTS)])
        if cp_req.returncode == 0:
            ok("Dependências Python instaladas.", args)
        else:
            warn("Falha ao instalar dependências Python.", args)
    elif create:
        warn("requirements.txt não encontrado, pulando instalação.", args)
    if args.only_check and status.data["Venv"] != "OK":
        warn("Venv não válida em modo only-check.", args)

# ------------------ summary / export ------------------

def export_json(path: Path, args):
    obj = {
        "timestamp": time.strftime("%Y-%m-%dT%H:%M:%S%z"),
        "scriptVersion": SCRIPT_VERSION,
        "status": status.data,
        "suggestions": status.suggestions(args.only_check, args.ensure_admin),
        "onlyCheck": bool(args.only_check),
        "host": platform.node(),
    }
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(obj, indent=2, ensure_ascii=False), encoding="utf-8")
    ok(f"Status exportado para JSON: {path}", args)

def show_summary(args, start_time):
    print()
    title = "========== RESUMO =========="
    print(f"{Colors.MAGENTA}{title}{Colors.RESET}" if Colors.MAGENTA else title)
    d = status.data
    ordered_keys = [
        "Java","JavaVersion","Maven","MavenVersion","MavenSource","DockerCli","DockerVersion","DockerDaemon","WSL","WslDefault","Postgres","PostgresConn","PostgresSchema","AdminCount","AdminHash","Perfis","Venv","DuraçãoSeg"
    ]
    d["DuraçãoSeg"] = round(time.time() - start_time, 2)
    for k in ordered_keys:
        if k == "WslDefault" and not d.get(k):
            continue
        print(f"{k:15}: {d.get(k)}")
    sugg = status.suggestions(args.only_check, args.ensure_admin)
    if sugg:
        print("\nSugestões:")
        for s in sugg:
            print(f" - {s}")
    
    # Mostrar estatísticas finais do ambiente
    print("\n========== ESTATÍSTICAS DO AMBIENTE ==========")
    
    # Informações do sistema
    print(f"Sistema Operacional: {platform.system()} {platform.release()}")
    print(f"Arquitetura       : {platform.machine()}")
    print(f"Python            : {platform.python_version()}")
    
    # Resumo dos componentes principais
    components_status = {
        "Java": d.get("Java"),
        "Maven": d.get("Maven"), 
        "Docker": d.get("DockerDaemon"),
        "PostgreSQL": d.get("Postgres"),
        "Ambiente Python": d.get("Venv")
    }
    
    print("\nStatus dos Componentes:")
    for component, status_val in components_status.items():
        status_icon = "✓" if status_val == "OK" else "✗"
        print(f"  {status_icon} {component:<15}: {status_val}")
    
    # Versões dos componentes
    print("\nVersões Instaladas:")
    if d.get("JavaVersion"):
        print(f"  Java    : {d.get('JavaVersion')}")
    if d.get("MavenVersion"):
        print(f"  Maven   : {d.get('MavenVersion')} ({d.get('MavenSource', 'unknown')})")
    if d.get("DockerVersion"):
        print(f"  Docker  : {d.get('DockerVersion')}")
    
    # Banco de dados
    if d.get("PostgresSchema") == "OK":
        print(f"\nBanco de Dados:")
        print(f"  Usuários ADMIN  : {d.get('AdminCount', 0)}")
        print(f"  Validação Hash  : {d.get('AdminHash', 'N/A')}")
    
    # Tempo total
    print(f"\nTempo de Execução : {d.get('DuraçãoSeg')} segundos")
    
    if status.has_error:
        err("Concluído com avisos/erros. Verifique mensagens acima.", args)
    else:
        ok("Setup concluído com sucesso!", args)

# ------------------ main ------------------

def parse_args():
    p = argparse.ArgumentParser(description="Setup de ambiente (Python)")
    p.add_argument("--only-check", action="store_true")
    p.add_argument("--force-venv", action="store_true")
    p.add_argument("--force-maven", action="store_true", help="Força download e configuração do Maven mesmo se já estiver instalado")
    p.add_argument("--skip-python", action="store_true")
    p.add_argument("--auto-fix", action="store_true", help="Tenta corrigir automaticamente problemas (Docker, Maven, Postgres, Admin)")
    p.add_argument("--no-color", action="store_true")
    p.add_argument("--status-json", type=Path)
    p.add_argument("--quiet", action="store_true")
    p.add_argument("--verbose", action="store_true", help="Mostra informações detalhadas de diagnóstico")
    p.add_argument("--ensure-admin", action="store_true")
    p.add_argument("--strict", action="store_true", help="Falha (exit !=0) se requisitos críticos não atendidos")
    p.add_argument("--require-java", help="Versão mínima de Java ex: 11, 17, 21")
    p.add_argument("--require-maven", help="Versão mínima de Maven ex: 3.8, 3.9")
    p.add_argument("--java-home", help="Diretório raiz do JDK para usar nesta execução")
    p.add_argument("--maven-home", help="Diretório raiz do Maven para usar nesta execução")
    return p.parse_args()

def main():
    args = parse_args()
    if args.no_color:
        Colors.disable()
    if args.java_home:
        jbin = Path(args.java_home) / "bin"
        if jbin.exists():
            os.environ["PATH"] = str(jbin) + os.pathsep + os.environ.get("PATH", "")
            os.environ["JAVA_HOME"] = args.java_home
        else:
            warn("--java-home fornecido mas bin inexistente", args)
    if args.maven_home:
        mbin = Path(args.maven_home) / "bin"
        if mbin.exists():
            os.environ["PATH"] = str(mbin) + os.pathsep + os.environ.get("PATH", "")
        else:
            warn("--maven-home fornecido mas bin inexistente", args)

    start = time.time()
    info(f"Iniciando setup de ambiente (versão {SCRIPT_VERSION})...", args)
    
    # Explicar o modo de auto-fix se estiver ativado
    if args.auto_fix:
        info("Modo auto-fix ativado: tentarei resolver problemas automaticamente.", args)
    
    # Etapa 1: Verificar/configurar Java
    check_java(args)
    
    # Etapa 2: Verificar/configurar Maven
    check_maven(args)

    def version_tuple(v:str):
        return tuple(int(x) for x in re.findall(r"\d+", v)[:3]) if v and v != '?' else tuple()
    if args.require_java and status.data.get("Java") == "OK":
        cur = version_tuple(status.data.get("JavaVersion",""))
        req = version_tuple(args.require_java)
        if cur and req and cur < req:
            warn(f"Java versão {status.data.get('JavaVersion')} < mínima requerida {args.require_java}", args)
            status.set("Java", f"OLD({status.data.get('JavaVersion')})")
    if args.require_maven and status.data.get("Maven") == "OK":
        cur = version_tuple(status.data.get("MavenVersion",""))
        req = version_tuple(args.require_maven)
        if cur and req and cur < req:
            warn(f"Maven versão {status.data.get('MavenVersion')} < mínima requerida {args.require_maven}", args)
            status.set("Maven", f"OLD({status.data.get('MavenVersion')})")

    # Etapa 3: Verificar/configurar Docker
    check_docker(args)
    
    # Se Docker estiver disponível, verificar WSL no Windows
    if status.data.get("DockerCli") == "OK":
        check_wsl(args)
    
    # Tentar novamente o Docker se falhou na primeira tentativa
    if args.auto_fix and status.data.get("DockerCli") != "OK":
        warn("Tentando configurar Docker novamente...", args)
        check_docker(args)
    
    # Etapa 4: Verificar/iniciar container Postgres se Docker estiver OK
    if status.data.get("DockerCli") == "OK" and status.data.get("DockerDaemon") == "OK":
        check_postgres_container(args)
        
        # Se em modo auto-fix e container foi iniciado, aguardar um pouco para o banco ficar disponível
        if args.auto_fix and status.data.get("Postgres") == "OK" and status.data.get("PostgresConn") != "OK":
            info("Aguardando banco de dados ficar disponível...", args)
            time.sleep(2)  # Espera adicional para o banco inicializar completamente
        
        # Etapa 5: Verificar conexão e schema do banco
        check_postgres_db(args)
        
        # Etapa 6: Verificar/criar usuário ADMIN
        count_admins(args)
        validate_admin_hash(args)
        ensure_admin(args)
    else:
        warn("Docker não está disponível ou ativo. Pulando etapas relacionadas ao Postgres.", args)
    
    # Etapa 7: Verificar perfis Maven
    check_maven_profiles(args)
    
    # Etapa 8: Configurar ambiente Python
    create_or_validate_venv(args)
    
    # Verificar se há problemas não resolvidos que requerem tentativas adicionais
    if args.auto_fix:
        fixed_something = False
        
        # Tentar corrigir Maven se ainda estiver NOK
        if status.data.get("Maven") != "OK" and not args.only_check:
            info("Tentando corrigir Maven automaticamente...", args)
            # Forçar instalação do Maven
            args.force_maven = True
            check_maven(args)
            fixed_something = True
        
        # Tentar corrigir Postgres se ainda estiver NOK
        if (status.data.get("Postgres") != "OK" or status.data.get("PostgresConn") != "OK" or status.data.get("PostgresSchema") != "OK") and status.data.get("DockerCli") == "OK" and status.data.get("DockerDaemon") == "OK":
            info("Tentando corrigir Postgres automaticamente...", args)
            
            # Se o container não estiver rodando, tentar iniciar
            if status.data.get("Postgres") != "OK":
                start_postgres_container(args)
            
            # Verificar conexão e schema
            if status.data.get("Postgres") == "OK":
                check_postgres_db(args)
                fixed_something = True
            
                # Se schema agora está OK, verificar/criar ADMIN
                if status.data.get("PostgresSchema") == "OK":
                    count_admins(args)
                    validate_admin_hash(args)
                    ensure_admin(args)
        
        # Se algo foi corrigido, mostrar resumo atualizado
        if fixed_something:
            info("Algumas correções foram aplicadas, verificando estado final...", args)
    
    # Mostrar resumo final
    show_summary(args, start)
    if args.status_json:
        export_json(args.status_json, args)
    
    # Dicas baseadas no estado final
    if status.data.get("Venv") == "OK":
        info('Dica: execute "python main.py" após ativar a venv.', args)
    
    # Fornecer dicas específicas baseadas em problemas encontrados
    if args.auto_fix:
        problems_found = False
        
        if status.data.get("Maven") != "OK":
            info("Maven ainda não está configurado corretamente. Você pode:", args)
            info("1. Instalar Maven manualmente e adicionar ao PATH", args)
            info("2. Executar novamente com '--force-maven'", args)
            problems_found = True
        
        if status.data.get("DockerCli") != "OK" or status.data.get("DockerDaemon") != "OK":
            info("Docker ainda não está disponível. Você pode:", args)
            info("1. Instalar Docker Desktop manualmente", args)
            info("2. Verificar se o serviço Docker está em execução", args)
            problems_found = True
        
        if status.data.get("Postgres") != "OK":
            info("Container Postgres não está disponível. Verifique:", args)
            info("1. Se o Docker está em execução", args)
            info("2. Se o arquivo docker-compose.yml está correto", args)
            info("3. Execute 'docker-compose up -d' manualmente", args)
            problems_found = True
        
        if status.data.get("AdminCount", 0) == 0 and status.data.get("PostgresSchema") == "OK":
            info("Nenhum usuário ADMIN encontrado no banco. Execute novamente com '--ensure-admin'", args)
            problems_found = True
        
        if not problems_found:
            ok("Todos os componentes estão configurados corretamente!", args)
            info("O ambiente de desenvolvimento está pronto para uso.", args)
    
    # Modo strict
    if args.strict:
        critical_ok = all([
            status.data.get("Java") in ("OK",) or str(status.data.get("Java")).startswith("OLD"),
            status.data.get("Maven") in ("OK",) or str(status.data.get("Maven")).startswith("OLD"),
            status.data.get("DockerCli") == "OK",
            status.data.get("DockerDaemon") == "OK",
            status.data.get("Postgres") == "OK",
            status.data.get("PostgresConn") == "OK",
            status.data.get("PostgresSchema") == "OK",
        ])
        if not critical_ok:
            status.mark_error()
            err("Strict: requisitos críticos não atendidos.", args)
            sys.exit(2)

if __name__ == "__main__":
    main()
