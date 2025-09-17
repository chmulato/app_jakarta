#!/usr/bin/env python3
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
    if props.exists():
        # Usar properties existentes
        info("Bootstrap Maven interno (baixando distribuição)...", args)
        text_props = props.read_text(encoding="utf-8", errors="ignore")
        m_dist = re.search(r"^distributionUrl=(.+)$", text_props, re.MULTILINE)
        if m_dist:
            dist_url = m_dist.group(1).strip()
        else:
            # URL padrão do Maven 3.9.4
            dist_url = "https://dlcdn.apache.org/maven/maven-3/3.9.4/binaries/apache-maven-3.9.4-bin.zip"
        else:
            # Sem properties, usar URL padrão
            info("Baixando e configurando Maven automaticamente...", args)
            dist_url = "https://dlcdn.apache.org/maven/maven-3/3.9.4/binaries/apache-maven-3.9.4-bin.zip"    dist_root = PROJECT_ROOT / ".mvn" / "cache" / "dist"
    dist_root.mkdir(parents=True, exist_ok=True)
    zip_path = dist_root / "apache-maven-dist.zip"
    try:
        import urllib.request, zipfile
        
        # Mostrar progresso do download
        info(f"Baixando Maven de {dist_url}...", args)
        
        def report_progress(block_num, block_size, total_size):
            if args.quiet: return
            if total_size > 0:
                percent = min(int(block_num * block_size * 100 / total_size), 100)
                sys.stdout.write(f"\rProgresso: {percent}% [{block_num * block_size}/{total_size} bytes]")
                sys.stdout.flush()
        
        urllib.request.urlretrieve(dist_url, zip_path, reporthook=report_progress)
        print()  # Nova linha após progresso
        
        info("Extraindo Maven...", args)
        with zipfile.ZipFile(zip_path, 'r') as zf:
            zf.extractall(dist_root)
        extracted = None
        for p in dist_root.rglob("apache-maven-*/bin"):
            if p.is_dir():
                extracted = p.parent
                break
        if not extracted:
            err("Distribuição Maven baixada mas diretório bin não localizado.", args)
            return
        
        # Configurar ambiente Maven
        os.environ["MAVEN_HOME"] = str(extracted)
        os.environ["PATH"] = str(extracted / "bin") + os.pathsep + os.environ.get("PATH", "")
        mvn_exec = extracted / "bin" / ("mvn.cmd" if platform.system().lower()=="windows" else "mvn")
        if not mvn_exec.exists():
            err("Executável mvn não encontrado após bootstrap.", args)
            return
        
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
        else:
            err("Falha após bootstrap Maven. Saída: " + (cp.stderr.strip() if cp.stderr else "?"), args)
            return
    except Exception as e:
        err(f"Falha bootstrap Maven: {e}", args)
        return
    out_lines = cp.stdout.splitlines()
    first = out_lines[0].strip() if out_lines else ""
    m = re.search(r'Apache Maven ([0-9]+(?:\.[0-9]+){1,2})', first)
    ver = m.group(1) if m else "?"
    status.set("MavenVersion", ver)
    status.set("Maven", "OK")
    mvn_path_cp = run_cmd(["where" if platform.system().lower()=="windows" else "which", "mvn"], capture=True)
    mvn_path = (mvn_path_cp.stdout.splitlines()[0].strip() if mvn_path_cp.returncode==0 and mvn_path_cp.stdout else "")
    if not status.data.get("MavenSource"):
        status.set("MavenSource", "wrapper" if wrapper_used else "global")
    origem = "(wrapper)" if status.data["MavenSource"] == "wrapper" else ("(bootstrap)" if status.data["MavenSource"]=="bootstrap" else (f"({mvn_path})" if mvn_path else ""))
    ok(f"Maven detectado: versão {ver} {origem}", args)


def check_docker(args):
    info("Verificando Docker...", args)
    cp = run_cmd(["docker", "--version"], capture=True)
    if cp.returncode != 0:
        warn("Docker não disponível.", args)
        return
    status.set("DockerCli", "OK")
    cp_info = run_cmd(["docker", "info", "--format", "{{.ServerVersion}}"], capture=True)
    if cp_info.returncode != 0 or not cp_info.stdout.strip():
        warn("Docker CLI ok mas daemon inativo (abrir Docker Desktop).", args)
    else:
        ok(f"Docker ativo - Versão Servidor: {cp_info.stdout.strip()}", args)
        status.set("DockerDaemon", "OK")


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


def start_postgres_container(args):
    """Inicia o container Postgres usando docker-compose."""
    if status.data.get("DockerCli") != "OK" or status.data.get("DockerDaemon") != "OK":
        warn("Docker não está disponível. Não é possível iniciar container Postgres.", args)
        return False
    
    # Verificar se o container já está em execução
    cp = run_cmd(["docker", "ps", "--filter", f"name={POSTGRES_CONTAINER}", "--format", "{{.Names}}"])
    if cp.returncode == 0 and cp.stdout.strip():
        # Já está em execução
        return True
    
    info("Iniciando container Postgres com docker-compose...", args)
    docker_compose_file = PROJECT_ROOT / "docker-compose.yml"
    if not docker_compose_file.exists():
        err("Arquivo docker-compose.yml não encontrado.", args)
        return False
    
    # Tentar iniciar o container
    cmd = []
    if platform.system().lower() == "windows":
        # No Windows, usar o comando docker-compose
        cmd = ["docker-compose", "-f", str(docker_compose_file), "up", "-d", "postgres"]
    else:
        # Em outros sistemas, pode ser docker compose (sem hífen)
        cmd = ["docker", "compose", "-f", str(docker_compose_file), "up", "-d", "postgres"]
    
    cp = run_cmd(cmd)
    if cp.returncode != 0:
        # Tentar o comando alternativo
        if platform.system().lower() == "windows":
            alt_cmd = ["docker", "compose", "-f", str(docker_compose_file), "up", "-d", "postgres"]
        else:
            alt_cmd = ["docker-compose", "-f", str(docker_compose_file), "up", "-d", "postgres"]
        
        cp = run_cmd(alt_cmd)
        if cp.returncode != 0:
            err("Falha ao iniciar container Postgres com docker-compose.", args)
            return False
    
    # Aguardar container ficar saudável
    info("Aguardando container Postgres inicializar (pode levar alguns segundos)...", args)
    max_attempts = 10
    for i in range(max_attempts):
        if args.quiet:
            time.sleep(3)
        else:
            for j in range(3):
                sys.stdout.write(".")
                sys.stdout.flush()
                time.sleep(1)
            print("")
        
        # Verificar estado do container
        cp_health = run_cmd(["docker", "inspect", "--format", "{{json .State.Health.Status}}", POSTGRES_CONTAINER])
        if cp_health.returncode == 0:
            health = cp_health.stdout.strip().strip('"')
            if health == "healthy":
                ok("Container Postgres iniciado e saudável.", args)
                status.set("Postgres", "OK")
                return True
    
    warn("Container Postgres iniciado, mas não ficou saudável no tempo esperado.", args)
    status.set("Postgres", "starting")
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
    cp_counts = run_cmd(["docker", "exec", POSTGRES_CONTAINER, "psql", "-U", POSTGRES_USER, "-d", POSTGRES_DB, "-A", "-F", ",", "-t", "-c", "SELECT (SELECT COUNT(*) FROM usuarios),(SELECT COUNT(*) FROM produtos);"])
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
    try:
        import bcrypt  # type: ignore
    except Exception:
        warn("Biblioteca bcrypt não instalada na venv (pular validação).", args)
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
    
    # Verificar se já existe ADMIN
    if status.data.get("AdminCount", 0) > 0:
        info("Já existe ADMIN, não será criado novo.", args)
        return
    
    info("Criando usuário ADMIN default...", args)
    cp_col = run_cmd(["docker", "exec", POSTGRES_CONTAINER, "psql", "-U", POSTGRES_USER, "-d", POSTGRES_DB, "-t", "-A", "-c", "SELECT 1 FROM information_schema.columns WHERE table_name='usuarios' AND column_name='perfil' LIMIT 1;"])
    has_perfil = (cp_col.returncode == 0 and cp_col.stdout.strip() == '1')
    if not has_perfil:
        warn("Coluna 'perfil' ausente. Aplicando migração leve (ALTER TABLE).", args)
        cp_alter = run_cmd(["docker", "exec", POSTGRES_CONTAINER, "psql", "-U", POSTGRES_USER, "-d", POSTGRES_DB, "-c", "ALTER TABLE usuarios ADD COLUMN IF NOT EXISTS perfil VARCHAR(20) DEFAULT 'USUARIO' CHECK (perfil IN ('ADMIN','USUARIO'));" ])
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
        count_admins(args)
        validate_admin_hash(args)
    else:
        err("Falha ao garantir usuário ADMIN.", args)


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
        "Java","JavaVersion","Maven","MavenVersion","MavenSource","DockerCli","DockerDaemon","WSL","WslDefault","Postgres","PostgresConn","PostgresSchema","AdminCount","AdminHash","Perfis","Venv","DuraçãoSeg"
    ]
    d["DuraçãoSeg"] = round(time.time() - start_time, 2)
    for k in ordered_keys:
        if k == "WslDefault" and not d.get(k):
            continue
        print(f"{k:13}: {d.get(k)}")
    sugg = status.suggestions(args.only_check, args.ensure_admin)
    if sugg:
        print("\nSugestões:")
        for s in sugg:
            print(f" - {s}")
    if status.has_error:
        err("Concluído com avisos/erros. Verifique mensagens acima.", args)
    else:
        ok("Setup concluído.", args)

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
    info("Iniciando setup de ambiente...", args)
    
    # Explicar o modo de auto-fix se estiver ativado
    if args.auto_fix:
        info("Modo auto-fix ativado: tentarei resolver problemas automaticamente.", args)
    
    check_java(args)
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

    check_docker(args)
    check_wsl(args)
    
    # Verificar e possivelmente iniciar o container Postgres
    check_postgres_container(args)
    
    # Se em modo auto-fix e container foi iniciado, aguardar um pouco para o banco ficar disponível
    if args.auto_fix and status.data.get("Postgres") == "OK" and status.data.get("PostgresConn") != "OK":
        info("Aguardando banco de dados ficar disponível...", args)
        time.sleep(2)  # Espera adicional para o banco inicializar completamente
    
    check_postgres_db(args)
    count_admins(args)
    validate_admin_hash(args)
    ensure_admin(args)
    check_maven_profiles(args)
    create_or_validate_venv(args)
    
    # Se em modo auto-fix, verificar se todos os componentes estão OK e tentar corrigir novamente se necessário
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
        if status.data.get("Postgres") != "OK" and status.data.get("DockerCli") == "OK" and status.data.get("DockerDaemon") == "OK":
            info("Tentando corrigir Postgres automaticamente...", args)
            start_postgres_container(args)
            check_postgres_db(args)
            fixed_something = True
            
            # Se Postgres agora está OK, verificar/criar ADMIN
            if status.data.get("Postgres") == "OK" and status.data.get("PostgresSchema") == "OK":
                count_admins(args)
                validate_admin_hash(args)
                ensure_admin(args)
        
        # Se algo foi corrigido, mostrar resumo atualizado
        if fixed_something:
            info("Algumas correções foram aplicadas, verificando estado final...", args)
    
    show_summary(args, start)
    if args.status_json:
        export_json(args.status_json, args)
    
    # Dicas baseadas no estado final
    if status.data.get("Venv") == "OK":
        info('Dica: execute "python main.py --only-check" após ativar a venv.', args)
    
    if args.auto_fix and (status.data.get("Maven") != "OK" or status.data.get("Postgres") != "OK"):
        info("Algumas correções automáticas não foram bem-sucedidas. Verifique as mensagens acima.", args)
        info("Para melhor experiência, execute novamente com '--auto-fix' após resolver os problemas pendentes.", args)
    
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
