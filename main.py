#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
Script principal para automação de build e deploy da aplicação Java.
Substitui as funcionalidades do script PowerShell Start-App.ps1.

Este script permite:
- Compilação e empacotamento da aplicação com Maven
- Implantação no Tomcat 10.1.35 ou WildFly 37.0.1.Final
- Execução de testes com JaCoCo
- Gerenciamento de perfis Maven para diferentes ambientes
"""

import os
import sys
import subprocess
import platform
import shutil
import time
import logging
import tempfile
import glob

def validate_python_environment():
    """
    Valida se o script está sendo executado com o Python da venv, não o global.
    """
    # Detectar se estamos usando o Python global
    python_executable = sys.executable
    workspace_dir = os.path.dirname(os.path.abspath(__file__))
    venv_python = os.path.join(workspace_dir, ".venv", "Scripts", "python.exe")
    
    # Verificar se o Python atual é o da venv
    if not python_executable.lower().endswith(venv_python.lower()):
        # Verificar se a venv existe
        if os.path.exists(venv_python):
            print("=" * 80)
            print("⚠️  AVISO: Script executado com Python global!")
            print("=" * 80)
            print("Para evitar problemas de dependências, execute com o Python da venv:")
            print()
            print("Opção 1 - Ativar venv e executar:")
            print("  . .\\.venv\\Scripts\\Activate.ps1")
            print("  python .\\main.py")
            print()
            print("Opção 2 - Executar direto com Python da venv:")
            print("  .\\.venv\\Scripts\\python.exe .\\main.py")
            print()
            print("Se a venv não existir, crie com:")
            print("  .\\setup-python.ps1")
            print("=" * 80)
            
            # Perguntar se quer continuar mesmo assim
            try:
                choice = input("Deseja continuar mesmo assim? (S/N): ").strip().upper()
                if choice != 'S':
                    print("Execução cancelada. Use o Python da venv para melhor compatibilidade.")
                    sys.exit(1)
                else:
                    print("Continuando com Python global... (pode haver problemas de dependências)")
            except KeyboardInterrupt:
                print("\nExecução cancelada.")
                sys.exit(1)
        else:
            print("=" * 80)
            print("⚠️  AVISO: Virtual environment não encontrada!")
            print("=" * 80)
            print("Crie a venv primeiro com:")
            print("  .\\setup-python.ps1")
            print()
            print("Depois execute com:")
            print("  .\\.venv\\Scripts\\python.exe .\\main.py")
            print("=" * 80)
            sys.exit(1)

# Validar ambiente Python antes de prosseguir
validate_python_environment()

try:
    import requests
except ModuleNotFoundError:
    # Mensagem amigável quando o script é executado com o Python global sem a venv
    print("Erro: módulo 'requests' não encontrado.\n"
          "Provavelmente você executou com o Python global (fora da venv).\n"
          "Execute um dos comandos abaixo:\n\n"
          "1) Ativar a venv e rodar:\n"
          ". .\\.venv\\Scripts\\Activate.ps1; python .\\main.py\n\n"
          "2) Rodar direto com o Python da venv (sem ativar):\n"
          ".\\.venv\\Scripts\\python.exe .\\main.py\n\n"
          "Se a venv não existir, crie/atualize com: ./setup-python.ps1")
    import sys
    sys.exit(1)
import zipfile
import re
import argparse
from datetime import datetime
from pathlib import Path

# Variáveis globais
WORKSPACE_DIR = os.path.dirname(os.path.abspath(__file__))
PROJECT_DIR = os.path.join(WORKSPACE_DIR, "meu-projeto-java")
# Diretório base para servidores de aplicação
SERVER_DIR = os.path.join(WORKSPACE_DIR, "server")
# Ajuste conforme solicitado: servidores agora ficam dentro de /server
TOMCAT_DIR = os.path.join(SERVER_DIR, "apache-tomcat-10.1.35")
WILDFLY_DIR = os.path.join(SERVER_DIR, "wildfly-37.0.1.Final")
CURRENT_SERVER = None

def update_server_dirs(tomcat_dir_override=None, wildfly_dir_override=None):
    global TOMCAT_DIR, WILDFLY_DIR
    # Ordem de precedência: argumentos > variáveis de ambiente > defaults
    env_tomcat = os.environ.get("APP_TOMCAT_DIR")
    env_wildfly = os.environ.get("APP_WILDFLY_DIR")

    final_tomcat = tomcat_dir_override or env_tomcat or TOMCAT_DIR
    final_wildfly = wildfly_dir_override or env_wildfly or WILDFLY_DIR

    changed = []
    if final_tomcat != TOMCAT_DIR:
        TOMCAT_DIR = final_tomcat
        changed.append(f"Tomcat => {TOMCAT_DIR}")
    if final_wildfly != WILDFLY_DIR:
        WILDFLY_DIR = final_wildfly
        changed.append(f"WildFly => {WILDFLY_DIR}")
    if changed:
        log("Overrides de diretórios aplicados: " + "; ".join(changed), "INFO")
    else:
        log("Usando diretórios padrão de servidores (sem overrides)", "INFO")

def build_arg_parser():
    parser = argparse.ArgumentParser(
        description="Gerenciador de build e deploy (Tomcat / WildFly)")
    parser.add_argument("--tomcat-dir", dest="tomcat_dir", help="Caminho do Tomcat para override")
    parser.add_argument("--wildfly-dir", dest="wildfly_dir", help="Caminho do WildFly para override")
    parser.add_argument("--only-check", action="store_true", help="Apenas verificar ambiente e sair")
    return parser

# Configuração de portas para os servidores
WILDFLY_PORT = 8080
TOMCAT_PORT = 9090
WILDFLY_PORT_OFFSET = 0  # WildFly na porta padrão (8080), não precisa de offset
WILDFLY_MANAGEMENT_PORT = 9990  # Porta de administração padrão (9990)

# Configuração de logging
LOG_DIR = os.path.join(WORKSPACE_DIR, "log")
# Criar o diretório de log se não existir
if not os.path.exists(LOG_DIR):
    os.makedirs(LOG_DIR, exist_ok=True)

logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(levelname)s - %(message)s',
    handlers=[
        logging.FileHandler(os.path.join(LOG_DIR, "maven_deploy.log")),
        logging.StreamHandler()
    ]
)
logger = logging.getLogger(__name__)

# Cores para terminal (Windows e Linux)
class Colors:
    HEADER = '\033[95m'
    BLUE = '\033[94m'
    CYAN = '\033[96m'
    GREEN = '\033[92m'
    WARNING = '\033[93m'
    YELLOW = '\033[93m'  # Mesma cor do WARNING
    RED = '\033[91m'
    FAIL = '\033[91m'
    GRAY = '\033[90m'
    END = '\033[0m'
    BOLD = '\033[1m'
    UNDERLINE = '\033[4m'

def log(message, level="INFO"):
    """
    Função para registrar mensagens no log com formatação colorida.
    
    Args:
        message (str): Mensagem a ser registrada
        level (str): Nível de log (INFO, SUCCESS, WARNING, ERROR)
    """
    timestamp = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
    
    if level == "INFO":
        color = Colors.BLUE
        logger.info(message)
    elif level == "SUCCESS":
        color = Colors.GREEN
        logger.info(f"SUCCESS: {message}")
    elif level == "WARNING":
        color = Colors.WARNING
        logger.warning(message)
    elif level == "ERROR":
        color = Colors.FAIL
        logger.error(message)
    else:
        color = Colors.END
        logger.info(message)
    
    print(f"{timestamp} - {color}{level}{Colors.END}: {message}")

def http_download(url, dest_path, timeout=30, max_retries=3, backoff_seconds=2, chunk_size=8192):
    """
    Faz download HTTP com retries exponenciais e tratamento de exceções.

    Args:
        url (str): URL do recurso
        dest_path (str): Caminho do arquivo de destino
        timeout (int): Timeout em segundos por tentativa
        max_retries (int): Número máximo de tentativas
        backoff_seconds (int): Backoff base entre tentativas
        chunk_size (int): Tamanho do chunk em bytes

    Returns:
        bool: True em caso de sucesso, False em caso de falha definitiva
    """
    try:
        os.makedirs(os.path.dirname(dest_path) or ".", exist_ok=True)
    except Exception:
        pass

    attempt = 0
    while attempt < max_retries:
        attempt += 1
        try:
            log(f"Baixando: {url} (tentativa {attempt}/{max_retries})", "INFO")
            r = requests.get(url, stream=True, timeout=timeout)
            r.raise_for_status()
            with open(dest_path, "wb") as f:
                for chunk in r.iter_content(chunk_size):
                    if chunk:
                        f.write(chunk)
            return True
        except Exception as e:
            # Em erros de rede/HTTP, fazer retry (exceto último)
            if attempt >= max_retries:
                log(f"Falha ao baixar {url}: {e}", "ERROR")
                return False
            sleep_for = backoff_seconds * attempt
            log(f"Erro ao baixar {url}: {e} — tentando novamente em {sleep_for}s", "WARNING")
            try:
                time.sleep(sleep_for)
            except Exception:
                pass

def load_db_config_from_compose():
    """
    Lê credenciais do PostgreSQL a partir do `docker-compose.yml`.
    Prioridade: variáveis de ambiente APP_DB_* > docker-compose.yml > defaults.

    Retorna:
        dict: {host, port, name, user, password}
    """
    # Defaults
    cfg = {
        "host": os.environ.get("APP_DB_HOST", "localhost"),
        "port": os.environ.get("APP_DB_PORT", "5432"),
        "name": os.environ.get("APP_DB_NAME", "postgres"),
        "user": os.environ.get("APP_DB_USER", "postgres"),
        "password": os.environ.get("APP_DB_PASSWORD", "postgres"),
    }

    compose_path = os.path.join(WORKSPACE_DIR, "docker-compose.yml")
    if not os.path.exists(compose_path):
        return cfg

    try:
        try:
            import importlib
            yaml = importlib.import_module('yaml')  # dynamic import
        except Exception:
            log("PyYAML não instalado; usando defaults/env para DB.", "WARNING")
            return cfg

        with open(compose_path, "r", encoding="utf-8") as f:
            data = yaml.safe_load(f) or {}

        services = data.get("services", {}) if isinstance(data, dict) else {}
        svc = None
        for key in ("postgres", "db", "database"):
            if key in services:
                svc = services[key]
                break
        if not isinstance(svc, dict):
            return cfg

        # environment pode ser dict ou lista
        env_block = svc.get("environment", {})
        env_map = {}
        if isinstance(env_block, dict):
            env_map = env_block
        elif isinstance(env_block, list):
            for item in env_block:
                if isinstance(item, str) and "=" in item:
                    k, v = item.split("=", 1)
                    env_map[k] = v

        # ports: tentar extrair hostPort se mapeado "host:container" com container 5432
        host_port = None
        ports = svc.get("ports", [])
        if isinstance(ports, list):
            for p in ports:
                if isinstance(p, str) and ":" in p:
                    hp, cp = p.split(":", 1)
                    # remover aspas ou espaços
                    hp = hp.strip().strip('"')
                    cp = cp.strip().strip('"')
                    if cp == "5432" or cp.endswith("5432"):
                        host_port = hp
                        break

        cfg_from_compose = {
            "host": cfg["host"],
            "port": host_port or cfg["port"],
            "name": env_map.get("POSTGRES_DB", cfg["name"]),
            "user": env_map.get("POSTGRES_USER", cfg["user"]),
            "password": env_map.get("POSTGRES_PASSWORD", cfg["password"]),
        }

        # Aplicar overrides finais por variáveis de ambiente, se presentes
        for key, env_name in (
            ("host", "APP_DB_HOST"),
            ("port", "APP_DB_PORT"),
            ("name", "APP_DB_NAME"),
            ("user", "APP_DB_USER"),
            ("password", "APP_DB_PASSWORD"),
        ):
            val = os.environ.get(env_name)
            if val:
                cfg_from_compose[key] = val

        return cfg_from_compose
    except Exception as e:
        # Em caso de erro, manter defaults/env
        log(f"Falha ao ler docker-compose.yml para DB: {e}", "WARNING")
        return cfg

def check_maven_installed():
    """
    Verifica se o Maven está instalado e disponível no PATH.
    
    Returns:
        tuple: (bool, str) - True se o Maven estiver instalado + versão, False caso contrário + mensagem de erro
    """
    try:
        # Em Windows, procurar o Maven em locais comuns
        maven_cmd = "mvn"
        if platform.system() == "Windows":
            # Tentar caminho conhecido do Maven
            potential_paths = [
                "mvn",  # Caso esteja no PATH
                "C:\\Program Files\\Apache\\apache-maven-3.9.11\\bin\\mvn.cmd",
                "C:\\Program Files\\Apache\\Maven\\bin\\mvn.cmd",
                os.path.join(os.environ.get('MAVEN_HOME', ''), 'bin', 'mvn.cmd'),
                os.path.join(os.environ.get('M2_HOME', ''), 'bin', 'mvn.cmd')
            ]
            
            for path in potential_paths:
                try:
                    # Verificar se o comando existe
                    if path != "mvn" and not os.path.exists(path):
                        continue
                        
                    process = subprocess.run(
                        [path, "-version"], 
                        stdout=subprocess.PIPE, 
                        stderr=subprocess.PIPE,
                        text=True,
                        timeout=5
                    )
                    
                    if process.returncode == 0:
                        maven_cmd = path
                        # Extrair a versão do Maven
                        output = process.stdout
                        version = "Desconhecida"
                        if "Apache Maven" in output:
                            version_line = output.split('\n')[0]
                            version = version_line.split('Apache Maven ')[1].split(' ')[0]
                        return True, version, maven_cmd
                except:
                    continue
        
        # Verificar comando padrão se as tentativas anteriores falharam
        process = subprocess.run(
            [maven_cmd, "-version"], 
            stdout=subprocess.PIPE, 
            stderr=subprocess.PIPE,
            text=True,
            timeout=5
        )
        
        if process.returncode == 0:
            # Extrair a versão do Maven
            output = process.stdout
            version = "Desconhecida"
            if "Apache Maven" in output:
                version_line = output.split('\n')[0]
                version = version_line.split('Apache Maven ')[1].split(' ')[0]
            return True, version, maven_cmd
        return False, "Maven está instalado, mas retornou erro ao verificar a versão", maven_cmd
    except (subprocess.SubprocessError, FileNotFoundError):
        return False, "Maven não está instalado ou não está no PATH do sistema", maven_cmd

# Variável global para armazenar o comando Maven
MAVEN_CMD = "mvn"

def check_java_version(java_home=None):
    """
    Verifica a versão do Java instalado e exibe informações detalhadas.
    
    Args:
        java_home (str, optional): Caminho para o JAVA_HOME. Se None, tenta detectar.
        
    Returns:
        tuple: (is_installed, version_info, java_path)
    """
    java_cmd = "java"
    java_path = None
    
    # Se JAVA_HOME foi fornecido, use o java específico desse caminho
    if java_home:
        if platform.system() == "Windows":
            java_path = os.path.join(java_home, "bin", "java.exe")
        else:
            java_path = os.path.join(java_home, "bin", "java")
        
        if os.path.exists(java_path):
            java_cmd = f'"{java_path}"'
        else:
            log(f"Arquivo java não encontrado em {java_path}", "WARNING")
            java_path = None
    
    try:
        # Verificar a versão do Java
        if platform.system() == "Windows":
            version_output = subprocess.check_output(f'{java_cmd} -version', stderr=subprocess.STDOUT, shell=True, universal_newlines=True)
        else:
            version_output = subprocess.check_output([java_cmd, "-version"], stderr=subprocess.STDOUT, universal_newlines=True)
        
        # Extrair informações úteis
        version_info = {}
        
        # Extrair versão principal
        version_match = re.search(r'version "([^"]+)"', version_output)
        if version_match:
            version_info["version"] = version_match.group(1)
        
        # Extrair informações do fornecedor/implementação
        vendor_match = re.search(r'(Java\(TM\)|OpenJDK) (Runtime Environment|64-Bit Server VM)', version_output)
        if vendor_match:
            version_info["vendor"] = vendor_match.group(1)
            version_info["type"] = vendor_match.group(2)
        
        # Extrair informações do build
        build_match = re.search(r'build ([^\s]+)', version_output)
        if build_match:
            version_info["build"] = build_match.group(1)
        
        # Exibir informações
        log(f"Java encontrado: {version_info.get('vendor', 'Desconhecido')} {version_info.get('version', 'Versão desconhecida')}", "SUCCESS")
        log(f"Caminho do Java: {java_path or 'PATH do sistema'}", "INFO")
        
        return True, version_info, java_path or java_cmd
    except Exception as e:
        log(f"Erro ao verificar a versão do Java: {str(e)}", "ERROR")
        return False, str(e), None

def check_java_installed():
    """
    Verifica se o Java está instalado e disponível no PATH.
    
    Returns:
        tuple: (bool, str) - True se o Java estiver instalado + versão, False caso contrário + mensagem de erro
    """
    try:
        # Verifica se o comando 'java -version' pode ser executado
        process = subprocess.run(
            ["java", "-version"], 
            stdout=subprocess.PIPE, 
            stderr=subprocess.PIPE,
            text=True,
            timeout=5
        )
        
        # A saída do comando java -version geralmente vai para stderr, não stdout
        output = process.stderr if process.stderr else process.stdout
        
        if process.returncode == 0:
            # Extrair a versão do Java
            version = "Desconhecida"
            if "version" in output:
                version_line = output.split('\n')[0]
                # Extrair a versão do formato padrão: java version "1.8.0_XXX" ou "11.0.X"
                try:
                    version = version_line.split('version')[1].strip().strip('"').split(' ')[0]
                except:
                    pass
            return True, version
        return False, "Java está instalado, mas retornou erro ao verificar a versão"
    except (subprocess.SubprocessError, FileNotFoundError):
        return False, "Java não está instalado ou não está no PATH do sistema"

def check_docker_installed():
    """
    Verifica se o Docker está instalado e disponível no PATH.
    
    Returns:
        tuple: (bool, str) - True se o Docker estiver instalado + versão, False caso contrário + mensagem de erro
    """
    try:
        # Verifica se o comando 'docker --version' pode ser executado
        process = subprocess.run(
            ["docker", "--version"], 
            stdout=subprocess.PIPE, 
            stderr=subprocess.PIPE,
            text=True,
            timeout=5
        )
        
        output = process.stdout if process.stdout else process.stderr
        
        if process.returncode == 0:
            # Extrair a versão do Docker
            version = "Desconhecida"
            if "version" in output:
                version = output.strip()
            return True, version
        return False, "Docker está instalado, mas retornou erro ao verificar a versão"
    except (subprocess.SubprocessError, FileNotFoundError):
        return False, "Docker não está instalado ou não está no PATH do sistema"

def check_database_connection():
    """
    Verifica se é possível conectar ao banco de dados PostgreSQL.
    
    Returns:
        tuple: (bool, str) - True se a conexão foi bem-sucedida, False caso contrário + mensagem de erro
    """
    try:
        # Verificar se os contêineres Docker estão em execução
        process = subprocess.run(
            ["docker", "ps", "--filter", "name=postgres", "--format", "{{.Names}}"], 
            stdout=subprocess.PIPE, 
            stderr=subprocess.PIPE,
            text=True,
            timeout=5
        )
        
        output = process.stdout.strip()
        
        if not output:
            return False, "Contêiner PostgreSQL não está em execução"
            
        # Carregar dados do docker-compose.yml (ou env) para obter informações do banco
        db_cfg = load_db_config_from_compose()
        db_host = db_cfg["host"]
        db_port = str(db_cfg["port"]) if isinstance(db_cfg["port"], int) else db_cfg["port"]
        db_name = db_cfg["name"]
        db_user = db_cfg["user"]
        db_password = db_cfg["password"]
        
        # Aqui você poderia tentar conectar ao banco usando psycopg2 ou outro driver
        # Se o módulo psycopg2 estiver instalado
        try:
            import psycopg2
            conn = psycopg2.connect(
                host=db_host,
                port=db_port,
                database=db_name,
                user=db_user,
                password=db_password
            )
            conn.close()
            return True, f"Conectado ao banco de dados PostgreSQL: {db_host}:{db_port}/{db_name}"
        except ImportError:
            return True, "Módulo psycopg2 não instalado, mas contêiner PostgreSQL está em execução"
        except Exception as e:
            return False, f"Erro ao conectar ao banco de dados: {str(e)}"
            
    except (subprocess.SubprocessError, FileNotFoundError):
        return False, "Não foi possível verificar o status do PostgreSQL. Docker não está disponível."

def check_server_running(port):
    """
    Verifica se um servidor está em execução em uma determinada porta.
    
    Args:
        port (int): A porta a ser verificada
        
    Returns:
        bool: True se o servidor estiver em execução, False caso contrário
    """
    import socket
    try:
        s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        s.settimeout(1)
        result = s.connect_ex(('localhost', port))
        s.close()
        return result == 0
    except:
        return False

def configure_tomcat_port(tomcat_dir, port):
    """
    Configura a porta HTTP do Tomcat editando o arquivo server.xml.
    
    Args:
        tomcat_dir (str): Diretório de instalação do Tomcat
        port (int): Porta a ser configurada
        
    Returns:
        bool: True se a configuração foi bem-sucedida, False caso contrário
    """
    import xml.etree.ElementTree as ET
    try:
        server_xml_path = os.path.join(tomcat_dir, "conf", "server.xml")
        if not os.path.exists(server_xml_path):
            log(f"Arquivo server.xml não encontrado em: {server_xml_path}", "ERROR")
            return False
        
        # Fazer backup do server.xml original
        backup_path = os.path.join(tomcat_dir, "conf", "server.xml.bak")
        if not os.path.exists(backup_path):
            shutil.copy2(server_xml_path, backup_path)
            log(f"Backup do server.xml criado em: {backup_path}", "INFO")
        
        # Ler o arquivo XML
        tree = ET.parse(server_xml_path)
        root = tree.getroot()
        
        # Encontrar o Connector HTTP
        connector_found = False
        for connector in root.findall(".//Connector"):
            protocol = connector.get('protocol', '')
            if 'HTTP/1.1' in protocol or connector.get('port') == '8080':
                connector.set('port', str(port))
                connector_found = True
                log(f"Porta do Connector HTTP alterada para: {port}", "SUCCESS")
                break
        
        if not connector_found:
            log("Não foi possível encontrar o Connector HTTP no server.xml", "ERROR")
            return False
        
        # Salvar as alterações
        tree.write(server_xml_path)
        log(f"Arquivo server.xml atualizado com sucesso", "SUCCESS")
        return True
        
    except Exception as e:
        log(f"Erro ao configurar a porta do Tomcat: {str(e)}", "ERROR")
        return False

def setup_tomcat_environment(tomcat_dir):
    """
    Configura as variáveis de ambiente necessárias para o Tomcat funcionar corretamente.
    
    Args:
        tomcat_dir (str): Diretório de instalação do Tomcat
        
    Returns:
        dict: Dicionário com as variáveis de ambiente configuradas
    """
    env = os.environ.copy()
    
    # Verificar se o diretório do Tomcat existe
    if not os.path.exists(tomcat_dir):
        log(f"Diretório do Tomcat não encontrado: {tomcat_dir}", "ERROR")
        log("Verifique se o Tomcat está instalado corretamente.", "ERROR")
        return env
    
    log(f"Configurando ambiente para Tomcat em: {tomcat_dir}", "INFO")
    
    # Variáveis essenciais do Tomcat
    env["CATALINA_HOME"] = tomcat_dir
    env["CATALINA_BASE"] = tomcat_dir
    
    # Adicionar mais variáveis de ambiente úteis para o Tomcat
    env["CATALINA_TMPDIR"] = os.path.join(tomcat_dir, "temp")
    
    # Verificar se as pastas essenciais existem
    required_dirs = ["bin", "lib", "conf", "logs", "temp", "webapps", "work"]
    missing_dirs = [d for d in required_dirs if not os.path.exists(os.path.join(tomcat_dir, d))]
    
    if missing_dirs:
        log(f"Aviso: Diretórios essenciais não encontrados no Tomcat: {', '.join(missing_dirs)}", "WARNING")
    
    # Adicionar diretório bin do Tomcat ao PATH
    tomcat_bin = os.path.join(tomcat_dir, "bin")
    if os.path.exists(tomcat_bin):
        if platform.system() == "Windows":
            env["PATH"] = f"{tomcat_bin};{env.get('PATH', '')}"
        else:
            env["PATH"] = f"{tomcat_bin}:{env.get('PATH', '')}"
    
    # Configurar JAVA_HOME automaticamente se não estiver definido
    if "JAVA_HOME" not in env or not env["JAVA_HOME"] or not os.path.exists(env["JAVA_HOME"]):
        log("JAVA_HOME não definido ou inválido. Tentando detectar automaticamente...", "WARNING")
        java_home = detect_java_home()
        
        if java_home:
            env["JAVA_HOME"] = java_home
            log(f"JAVA_HOME configurado automaticamente: {java_home}", "SUCCESS")
            
            # Verificar se o Java é acessível
            java_installed, java_version, _ = check_java_version(java_home)
            if java_installed:
                log(f"Java verificado: {java_version.get('version', 'Versão desconhecida')}", "SUCCESS")
            else:
                log("O Java foi detectado, mas não é acessível. Verifique a instalação.", "ERROR")
        else:
            log("Não foi possível detectar o JAVA_HOME. O Tomcat pode não iniciar corretamente.", "ERROR")
            log("Por favor, instale o JDK e configure a variável JAVA_HOME manualmente.", "ERROR")
    else:
        log(f"JAVA_HOME já definido: {env['JAVA_HOME']}", "INFO")
        # Verificar a versão do Java
        java_installed, java_version, _ = check_java_version(env["JAVA_HOME"])
        if java_installed:
            log(f"Java verificado: {java_version.get('version', 'Versão desconhecida')}", "SUCCESS")
        else:
            log("O JAVA_HOME está definido, mas o Java não é acessível. Verifique a instalação.", "ERROR")
    
    # Configurar JRE_HOME se necessário
    if "JRE_HOME" not in env and "JAVA_HOME" in env:
        env["JRE_HOME"] = env["JAVA_HOME"]
    
    # Configurar CATALINA_OPTS para a porta personalizada
    env["CATALINA_OPTS"] = f"-Dport.http.nonssl={TOMCAT_PORT}"
    
    return env

def detect_java_home():
    """
    Detecta o diretório do Java instalado no sistema.
    
    Returns:
        str: Caminho para o JAVA_HOME ou None se não for encontrado
    """
    log("Tentando detectar JAVA_HOME automaticamente...", "INFO")
    
    # Verificar a variável de ambiente JAVA_HOME primeiro
    java_home = os.environ.get("JAVA_HOME")
    if java_home and os.path.exists(java_home):
        log(f"JAVA_HOME encontrado nas variáveis de ambiente: {java_home}", "SUCCESS")
        return java_home
    
    # Tenta encontrar usando javac ou java no PATH
    if platform.system() == "Windows":
        try:
            # Primeiro, tenta encontrar javac
            javac_path = None
            java_path = None
            
            try:
                javac_path = subprocess.check_output(["where", "javac"], universal_newlines=True).strip().split("\n")[0]
                log(f"Compilador Java (javac) encontrado em: {javac_path}", "INFO")
            except:
                log("Não foi possível encontrar javac no PATH", "WARNING")
            
            try:
                java_path = subprocess.check_output(["where", "java"], universal_newlines=True).strip().split("\n")[0]
                log(f"Java runtime (java) encontrado em: {java_path}", "INFO")
            except:
                log("Não foi possível encontrar java no PATH", "WARNING")
            
            # javac é mais confiável para encontrar o JDK
            if javac_path:
                # javac está em bin/javac, então voltar dois níveis para ter o JAVA_HOME
                java_home_path = os.path.dirname(os.path.dirname(javac_path))
                if os.path.exists(java_home_path):
                    log(f"JAVA_HOME detectado a partir de javac: {java_home_path}", "SUCCESS")
                    return java_home_path
            
            # Tentar com java se javac não funcionou
            if java_path:
                # java está em bin/java, então voltar dois níveis para ter o JAVA_HOME
                java_home_path = os.path.dirname(os.path.dirname(java_path))
                if os.path.exists(java_home_path):
                    log(f"JAVA_HOME detectado a partir de java: {java_home_path}", "SUCCESS")
                    return java_home_path
        except Exception as e:
            log(f"Erro ao detectar JAVA_HOME via PATH: {str(e)}", "ERROR")
    else:
        try:
            # Para sistemas Unix-like
            try:
                javac_path = subprocess.check_output(["which", "javac"], universal_newlines=True).strip()
                log(f"Compilador Java (javac) encontrado em: {javac_path}", "INFO")
            except:
                log("Não foi possível encontrar javac no PATH", "WARNING")
            
            if javac_path:
                # No Unix, javac pode estar em bin/javac ou em /usr/bin como um link simbólico
                real_path = os.path.realpath(javac_path)
                java_home_path = os.path.dirname(os.path.dirname(real_path))
                if os.path.exists(java_home_path):
                    log(f"JAVA_HOME detectado a partir de javac: {java_home_path}", "SUCCESS")
                    return java_home_path
            
            # Tentar comandos específicos do Unix para encontrar o Java
            try:
                # No MacOS, use /usr/libexec/java_home
                if platform.system() == "Darwin":
                    java_home_path = subprocess.check_output(["/usr/libexec/java_home"], universal_newlines=True).strip()
                    if java_home_path and os.path.exists(java_home_path):
                        log(f"JAVA_HOME detectado via /usr/libexec/java_home: {java_home_path}", "SUCCESS")
                        return java_home_path
                
                # Em distribuições baseadas em Debian/Ubuntu
                if os.path.exists("/etc/alternatives/java"):
                    java_path = os.path.realpath("/etc/alternatives/java")
                    if java_path and os.path.exists(java_path):
                        # Normalmente /etc/alternatives/java aponta para /usr/lib/jvm/java-X-openjdk/bin/java
                        java_home_path = os.path.dirname(os.path.dirname(java_path))
                        if os.path.exists(java_home_path):
                            log(f"JAVA_HOME detectado via alternatives: {java_home_path}", "SUCCESS")
                            return java_home_path
            except Exception as e:
                log(f"Erro ao detectar JAVA_HOME via comandos específicos: {str(e)}", "WARNING")
        except Exception as e:
            log(f"Erro ao detectar JAVA_HOME em sistema Unix: {str(e)}", "ERROR")
    
    # Tentar caminhos comuns
    log("Tentando encontrar Java em caminhos comuns do sistema...", "INFO")
    common_paths = [
        "C:\\Program Files\\Java\\jdk*",
        "C:\\Program Files\\Java\\jre*",
        "C:\\Program Files (x86)\\Java\\jdk*",
        "C:\\Program Files (x86)\\Java\\jre*",
        "/usr/lib/jvm/java-*",
        "/usr/java/default",
        "/usr/java/latest",
        "/Library/Java/JavaVirtualMachines/*/Contents/Home"
    ]
    
    for pattern in common_paths:
        try:
            matches = glob.glob(pattern)
            if matches:
                # Ordenar para tentar obter a versão mais recente (lexicograficamente)
                matches.sort()
                java_home_path = matches[-1]  # Última é geralmente a mais recente
                log(f"JAVA_HOME encontrado em caminho comum: {java_home_path}", "SUCCESS")
                return java_home_path
        except Exception as e:
            log(f"Erro ao verificar caminho comum '{pattern}': {str(e)}", "WARNING")
    
    # Não foi possível encontrar o Java
    log("Não foi possível detectar JAVA_HOME no sistema. Configuração manual pode ser necessária.", "ERROR")
    log("Sugestão: Instale o JDK e configure a variável de ambiente JAVA_HOME.", "WARNING")
    return None

def diagnose_wildfly_issues(env=None):
    """
    Diagnostica problemas com a inicialização do WildFly e exibe informações úteis.
    
    Args:
        env (dict, optional): Variáveis de ambiente configuradas para o WildFly
    """
    # Adicionar diagnóstico adicional
    print(f"\n{Colors.YELLOW}=== Diagnóstico do WildFly ==={Colors.END}")
    print(f"{Colors.YELLOW}Verificando logs do WildFly para encontrar possíveis erros...{Colors.END}")
    
    # Verificar arquivo de log do WildFly
    server_log = os.path.join(WILDFLY_DIR, "standalone", "log", "server.log")
    if os.path.exists(server_log):
        # Ler as últimas 20 linhas do log
        try:
            with open(server_log, 'r', encoding='utf-8', errors='ignore') as f:
                log_lines = f.readlines()
                last_lines = log_lines[-20:] if len(log_lines) >= 20 else log_lines
                
                print(f"{Colors.YELLOW}Últimas linhas do log do WildFly:{Colors.END}")
                for line in last_lines:
                    # Destacar linhas de erro
                    if "ERROR" in line or "SEVERE" in line or "Exception" in line:
                        print(f"{Colors.RED}{line.strip()}{Colors.END}")
                    else:
                        print(f"{Colors.GRAY}{line.strip()}{Colors.END}")
        except Exception as e:
            print(f"{Colors.RED}Erro ao ler logs do WildFly: {str(e)}{Colors.END}")
    else:
        print(f"{Colors.YELLOW}Nenhum arquivo de log encontrado em: {server_log}{Colors.END}")
    
    # Verificar variáveis de ambiente
    if env:
        print(f"\n{Colors.YELLOW}Variáveis de ambiente do WildFly:{Colors.END}")
        print(f"{Colors.YELLOW}JBOSS_HOME: {env.get('JBOSS_HOME', 'Não definido')}{Colors.END}")
        print(f"{Colors.YELLOW}JAVA_HOME: {env.get('JAVA_HOME', 'Não definido')}{Colors.END}")
    
    # Verificar arquivos de implantação
    deployments_dir = os.path.join(WILDFLY_DIR, "standalone", "deployments")
    if os.path.exists(deployments_dir):
        print(f"\n{Colors.YELLOW}Status de deployments:{Colors.END}")
        deployment_files = os.listdir(deployments_dir)
        
        for file in deployment_files:
            if file.startswith("meu-projeto-java"):
                if file.endswith(".war"):
                    print(f"{Colors.YELLOW}Arquivo WAR: {file}{Colors.END}")
                if file.endswith(".deployed"):
                    print(f"{Colors.GREEN}Deployment confirmado: {file}{Colors.END}")
                if file.endswith(".failed"):
                    print(f"{Colors.RED}Deployment falhou: {file}{Colors.END}")
                    # Tentar ler o erro de deployment
                    try:
                        error_file = os.path.join(deployments_dir, file)
                        with open(error_file, 'r', encoding='utf-8', errors='ignore') as f:
                            error_content = f.read().strip()
                            print(f"{Colors.RED}Erro: {error_content}{Colors.END}")
                    except:
                        pass
    else:
        print(f"{Colors.YELLOW}Diretório de deployments não encontrado: {deployments_dir}{Colors.END}")
    
    # Verificar portas em uso
    try:
        import socket
        ports_to_check = [WILDFLY_PORT, 9990]  # HTTP e Management
        for port in ports_to_check:
            s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
            s.settimeout(1)
            result = s.connect_ex(('localhost', port))
            s.close()
            if result == 0:
                print(f"{Colors.GREEN}Porta {port} está em uso (provavelmente pelo WildFly){Colors.END}")
            else:
                print(f"{Colors.RED}Porta {port} não está em uso{Colors.END}")
    except:
        print(f"{Colors.YELLOW}Não foi possível verificar as portas{Colors.END}")
    
    # Sugerir soluções
    print(f"\n{Colors.YELLOW}Possíveis soluções:{Colors.END}")
    print(f"{Colors.YELLOW}1. Verifique se o WildFly já está em execução em outra instância{Colors.END}")
    print(f"{Colors.YELLOW}2. Verifique os logs para erros específicos{Colors.END}")
    print(f"{Colors.YELLOW}3. Tente remover os arquivos de deployment antigos{Colors.END}")
    print(f"{Colors.YELLOW}4. Verifique se o JAVA_HOME está configurado corretamente{Colors.END}")
    print(f"{Colors.YELLOW}5. Verifique se as portas necessárias estão disponíveis{Colors.END}")

def setup_wildfly_environment():
    """
    Configura as variáveis de ambiente para o WildFly e retorna o ambiente configurado.
    
    Returns:
        dict: Dicionário com as variáveis de ambiente configuradas
    """
    env = os.environ.copy()
    
    # Verificar se o diretório do WildFly existe
    if not os.path.exists(WILDFLY_DIR):
        log(f"Diretório do WildFly não encontrado: {WILDFLY_DIR}", "ERROR")
        log("Verifique se o WildFly está instalado corretamente.", "ERROR")
        return env
    
    log(f"Configurando ambiente para WildFly em: {WILDFLY_DIR}", "INFO")
    
    # Variáveis essenciais do WildFly
    env["JBOSS_HOME"] = WILDFLY_DIR
    
    # Verificar se JAVA_HOME está definido ou tentar definir
    if "JAVA_HOME" not in env or not env["JAVA_HOME"] or not os.path.exists(env["JAVA_HOME"]):
        log("JAVA_HOME não definido ou inválido. Tentando detectar automaticamente...", "WARNING")
        java_home = detect_java_home()
        
        if java_home:
            env["JAVA_HOME"] = java_home
            log(f"JAVA_HOME configurado automaticamente: {java_home}", "SUCCESS")
            
            # Verificar se o Java é acessível
            java_installed, java_version, _ = check_java_version(java_home)
            if java_installed:
                log(f"Java verificado: {java_version.get('version', 'Versão desconhecida')}", "SUCCESS")
            else:
                log("O Java foi detectado, mas não é acessível. Verifique a instalação.", "ERROR")
        else:
            log("Não foi possível detectar o JAVA_HOME. O WildFly pode não iniciar corretamente.", "ERROR")
            log("Por favor, instale o JDK e configure a variável JAVA_HOME manualmente.", "ERROR")
    else:
        log(f"JAVA_HOME já definido: {env['JAVA_HOME']}", "INFO")
    
    # Adicionar diretório bin do WildFly ao PATH
    wildfly_bin = os.path.join(WILDFLY_DIR, "bin")
    if os.path.exists(wildfly_bin):
        if platform.system() == "Windows":
            env["PATH"] = f"{wildfly_bin};{env.get('PATH', '')}"
        else:
            env["PATH"] = f"{wildfly_bin}:{env.get('PATH', '')}"
    
    # Configurações específicas do WildFly (porta bind address, etc.)
    env["JAVA_OPTS"] = env.get("JAVA_OPTS", "") + f" -Djboss.http.port={WILDFLY_PORT} -Djboss.bind.address=0.0.0.0"
    
    return env

def configure_wildfly_postgres_datasource():
    """
    Configura um datasource PostgreSQL no WildFly editando o arquivo standalone.xml
    e garante a presença do driver em modules/org/postgresql.
    Usa defaults e variáveis de ambiente: APP_DB_HOST, APP_DB_PORT, APP_DB_NAME, APP_DB_USER, APP_DB_PASSWORD.

    Returns:
        bool: True se configurado com sucesso, False caso contrário
    """
    try:
        standalone_xml = os.path.join(WILDFLY_DIR, "standalone", "configuration", "standalone.xml")
        if not os.path.exists(standalone_xml):
            log(f"Arquivo standalone.xml não encontrado: {standalone_xml}", "ERROR")
            return False

        # Parâmetros do banco: preferir docker-compose.yml com override por env
        db_cfg = load_db_config_from_compose()
        db_host = db_cfg["host"]
        db_port = str(db_cfg["port"]) if isinstance(db_cfg["port"], int) else db_cfg["port"]
        db_name = db_cfg["name"]
        db_user = db_cfg["user"]
        db_pass = db_cfg["password"]
        jndi_name = "java:/jdbc/PostgresDS"
        pool_name = "PostgresDS"
        driver_name = "postgresql"
        module_name = "org.postgresql"

        # Garantir módulo do driver
        module_dir = os.path.join(WILDFLY_DIR, "modules", "system", "layers", "base", *module_name.split("."), "main")
        os.makedirs(module_dir, exist_ok=True)
        driver_version = "42.7.4"
        jar_name = f"postgresql-{driver_version}.jar"
        jar_path = os.path.join(module_dir, jar_name)
        module_xml = os.path.join(module_dir, "module.xml")

        if not os.path.exists(jar_path):
            url = f"https://repo1.maven.org/maven2/org/postgresql/postgresql/{driver_version}/{jar_name}"
            if http_download(url, jar_path, timeout=30, max_retries=3, backoff_seconds=2):
                log("Driver PostgreSQL baixado para o módulo do WildFly", "SUCCESS")
            else:
                log("Falha ao baixar driver PostgreSQL (tente rodar novamente mais tarde ou verifique sua rede)", "ERROR")
                return False

        # Criar/atualizar module.xml do driver garantindo dependências essenciais
        desired_module_xml = f"""
<module xmlns=\"urn:jboss:module:1.9\" name=\"{module_name}\">
  <resources>
    <resource-root path=\"{jar_name}\"/>
  </resources>
  <dependencies>
    <module name=\"java.se\"/>
    <module name=\"jakarta.transaction.api\"/>
  </dependencies>
</module>
""".strip()
        write_module_xml = True
        if os.path.exists(module_xml):
            try:
                with open(module_xml, "r", encoding="utf-8") as f:
                    existing = f.read()
                # Regravar somente se faltar alguma das dependências críticas
                needed_tokens = ["java.se", "jakarta.transaction.api"]
                write_module_xml = any(tok not in existing for tok in needed_tokens)
            except Exception:
                write_module_xml = True
        if write_module_xml:
            with open(module_xml, "w", encoding="utf-8") as f:
                f.write(desired_module_xml)
            log("module.xml do driver PostgreSQL criado/atualizado com dependências necessárias", "SUCCESS")

        # Se existir um template preconfigurado, renderizar e substituir standalone.xml
        wildfly_conf_dir = os.path.join(WILDFLY_DIR, "standalone", "configuration")
        template_candidates = [
            os.path.join(WORKSPACE_DIR, 'bak', 'standalone_template_postgres.xml'),
            os.path.join(wildfly_conf_dir, 'standalone_template_postgres.xml'),
            os.path.join(wildfly_conf_dir, 'standalone.xml.bak'),
        ]
        template_path = next((p for p in template_candidates if os.path.exists(p)), None)
        if template_path:
            # Fazer backup do standalone.xml atual, se existir
            if os.path.exists(standalone_xml):
                backup_path = standalone_xml + ".bak"
                if not os.path.exists(backup_path):
                    shutil.copy2(standalone_xml, backup_path)
                    log(f"Backup criado: {backup_path}", "INFO")

            with open(template_path, "r", encoding="utf-8") as f:
                content = f.read()
            # Renderizar placeholders
            content = (content
                .replace("__DB_HOST__", db_host)
                .replace("__DB_PORT__", str(db_port))
                .replace("__DB_NAME__", db_name)
                .replace("__DB_USER__", db_user)
                .replace("__DB_PASSWORD__", db_pass)
            )
            # Escrever standalone.xml
            with open(standalone_xml, "w", encoding="utf-8") as f:
                f.write(content)
            log("standalone.xml gerado a partir do template de Postgres", "SUCCESS")
            return True

        # Caso nao exista template, seguir com a edicao XML incremental
        # Backup do standalone.xml
        backup_path = standalone_xml + ".bak"
        if not os.path.exists(backup_path):
            shutil.copy2(standalone_xml, backup_path)
            log(f"Backup criado: {backup_path}", "INFO")

        import xml.etree.ElementTree as ET
        tree = ET.parse(standalone_xml)
        root = tree.getroot()

        def localname(tag):
            return tag.split('}', 1)[1] if tag.startswith('{') else tag

        # Encontrar subsistema de datasources
        ds_subsystem = None
        for elem in root.iter():
            if localname(elem.tag) == 'subsystem' and elem.tag.startswith('{') and 'datasources' in elem.tag:
                ds_subsystem = elem
                break

        if ds_subsystem is None:
            log("Subsystem de datasources não encontrado no standalone.xml", "ERROR")
            return False

        # Encontrar nó <datasources>
        datasources_node = None
        for child in ds_subsystem:
            if localname(child.tag) == 'datasources':
                datasources_node = child
                break
        if datasources_node is None:
            # criar um nó datasources dentro do subsystem
            ns_uri = ds_subsystem.tag.split('}', 1)[0][1:] if ds_subsystem.tag.startswith('{') else ''
            tag = f"{{{ns_uri}}}datasources" if ns_uri else "datasources"
            datasources_node = ET.SubElement(ds_subsystem, tag)

        # Garantir drivers
        drivers_node = None
        for child in datasources_node:
            if localname(child.tag) == 'drivers':
                drivers_node = child
                break
        if drivers_node is None:
            ns_uri = datasources_node.tag.split('}', 1)[0][1:] if datasources_node.tag.startswith('{') else ''
            tag = f"{{{ns_uri}}}drivers" if ns_uri else "drivers"
            drivers_node = ET.SubElement(datasources_node, tag)

        # Verificar driver postgresql
        has_pg_driver = any(localname(d.tag) == 'driver' and d.get('name') == driver_name for d in drivers_node)
        if not has_pg_driver:
            ns_uri = drivers_node.tag.split('}', 1)[0][1:] if drivers_node.tag.startswith('{') else ''
            tag = f"{{{ns_uri}}}driver" if ns_uri else "driver"
            driver_el = ET.SubElement(drivers_node, tag, attrib={"name": driver_name, "module": module_name})
            log("Driver 'postgresql' adicionado à seção <drivers>", "SUCCESS")

        # Garantir datasource PostgresDS
        target_ds = None
        for n in datasources_node:
            if localname(n.tag) == 'datasource' and (n.get('pool-name') == pool_name or n.get('jndi-name') == jndi_name):
                target_ds = n
                break
        if target_ds is None:
            ns_uri = datasources_node.tag.split('}', 1)[0][1:] if datasources_node.tag.startswith('{') else ''
            ds_tag = f"{{{ns_uri}}}datasource" if ns_uri else "datasource"
            cu_tag = f"{{{ns_uri}}}connection-url" if ns_uri else "connection-url"
            sec_tag = f"{{{ns_uri}}}security" if ns_uri else "security"
            drv_tag = f"{{{ns_uri}}}driver" if ns_uri else "driver"

            ds_el = ET.SubElement(datasources_node, ds_tag, attrib={
                "jndi-name": jndi_name,
                "pool-name": pool_name,
                "enabled": "true",
                "use-java-context": "true"
            })
            cu_el = ET.SubElement(ds_el, cu_tag)
            cu_el.text = f"jdbc:postgresql://{db_host}:{db_port}/{db_name}"
            drv_el = ET.SubElement(ds_el, drv_tag)
            drv_el.text = driver_name
            # Em versões recentes do schema do WildFly, as credenciais devem ser atributos do elemento <security>
            sec_el = ET.SubElement(ds_el, sec_tag)
            sec_el.set('user-name', db_user)
            sec_el.set('password', db_pass)
            log("Datasource 'PostgresDS' adicionado ao standalone.xml", "SUCCESS")
        else:
            # Normalizar e atualizar datasource existente
            ds_el = target_ds
            ns_uri = ds_el.tag.split('}', 1)[0][1:] if ds_el.tag.startswith('{') else ''
            cu_tag = f"{{{ns_uri}}}connection-url" if ns_uri else "connection-url"
            sec_tag = f"{{{ns_uri}}}security" if ns_uri else "security"
            drv_tag = f"{{{ns_uri}}}driver" if ns_uri else "driver"

            # connection-url
            cu_el = None
            for child in ds_el:
                if localname(child.tag) == 'connection-url':
                    cu_el = child
                    break
            if cu_el is None:
                cu_el = ET.SubElement(ds_el, cu_tag)
            cu_el.text = f"jdbc:postgresql://{db_host}:{db_port}/{db_name}"

            # driver
            drv_el = None
            for child in ds_el:
                if localname(child.tag) == 'driver':
                    drv_el = child
                    break
            if drv_el is None:
                drv_el = ET.SubElement(ds_el, drv_tag)
            drv_el.text = driver_name

            # security: garantir atributos user-name/password, removendo elementos filhos se existirem
            sec_el = None
            for child in ds_el:
                if localname(child.tag) == 'security':
                    sec_el = child
                    break
            if sec_el is None:
                sec_el = ET.SubElement(ds_el, sec_tag)
            # Ler valores de possíveis elementos filhos existentes
            existing_user = sec_el.get('user-name')
            existing_pass = sec_el.get('password')
            # Coletar valores de elementos aninhados se presentes
            nested_user = None
            nested_pass = None
            to_remove = []
            for child in list(sec_el):
                if localname(child.tag) == 'user-name':
                    nested_user = (child.text or '').strip()
                    to_remove.append(child)
                elif localname(child.tag) == 'password':
                    nested_pass = (child.text or '').strip()
                    to_remove.append(child)
            # Remover nós aninhados inválidos
            for ch in to_remove:
                sec_el.remove(ch)
            # Definir atributos (preferir config atual do projeto)
            sec_el.set('user-name', db_user or existing_user or nested_user or '')
            sec_el.set('password', db_pass or existing_pass or nested_pass or '')
            log("Datasource 'PostgresDS' já existia; parâmetros atualizados e credenciais normalizadas para atributos.", "INFO")

        # Salvar alterações
        tree.write(standalone_xml, encoding='utf-8', xml_declaration=True)
        log("standalone.xml atualizado com datasource PostgreSQL", "SUCCESS")
        return True
    except Exception as e:
        log(f"Erro ao configurar datasource do WildFly: {e}", "ERROR")
        return False

def configure_tomcat_postgres_datasource():
    """
    Configura um datasource PostgreSQL no Tomcat editando o arquivo conf/context.xml
    e garante a presença do driver em TOMCAT_DIR/lib.
    Usa env vars: APP_DB_HOST, APP_DB_PORT, APP_DB_NAME, APP_DB_USER, APP_DB_PASSWORD.

    Returns:
        bool: True se configurado com sucesso, False caso contrário
    """
    try:
        context_xml = os.path.join(TOMCAT_DIR, "conf", "context.xml")
        if not os.path.exists(context_xml):
            log(f"Arquivo context.xml não encontrado: {context_xml}", "ERROR")
            return False

        # Parâmetros: preferir docker-compose.yml com override por env
        db_cfg = load_db_config_from_compose()
        db_host = db_cfg["host"]
        db_port = str(db_cfg["port"]) if isinstance(db_cfg["port"], int) else db_cfg["port"]
        db_name = db_cfg["name"]
        db_user = db_cfg["user"]
        db_pass = db_cfg["password"]

        # Log de confirmação
        log(f"Usando credenciais do banco definidas: db={db_name}, user={db_user}", "INFO")

        # Garantir driver no lib/
        driver_version = "42.7.4"
        jar_name = f"postgresql-{driver_version}.jar"
        lib_dir = os.path.join(TOMCAT_DIR, "lib")
        os.makedirs(lib_dir, exist_ok=True)
        jar_path = os.path.join(lib_dir, jar_name)
        if not os.path.exists(jar_path):
            url = f"https://repo1.maven.org/maven2/org/postgresql/postgresql/{driver_version}/{jar_name}"
            log(f"Baixando driver PostgreSQL para Tomcat: {url}", "INFO")
            if http_download(url, jar_path, timeout=30, max_retries=3, backoff_seconds=2):
                log("Driver PostgreSQL copiado para TOMCAT/lib", "SUCCESS")
            else:
                log("Falha ao baixar driver para Tomcat (verifique sua conexão e tente novamente)", "ERROR")
                return False

        # Backup do context.xml
        backup_path = context_xml + ".bak"
        if not os.path.exists(backup_path):
            shutil.copy2(context_xml, backup_path)
            log(f"Backup criado: {backup_path}", "INFO")

        import xml.etree.ElementTree as ET
        tree = ET.parse(context_xml)
        root = tree.getroot()
        # Garantir que root seja <Context>
        if root.tag.lower() != 'context' and not root.tag.endswith('Context'):
            log("O arquivo context.xml não possui nó raiz <Context>", "ERROR")
            return False

        # Verificar se já existe um Resource com nome 'jdbc/PostgresDS'
        target_name = 'jdbc/PostgresDS'
        existing = None
        for child in root:
            if child.tag.lower().endswith('resource') and child.get('name') == target_name:
                existing = child
                break

        url = f"jdbc:postgresql://{db_host}:{db_port}/{db_name}"
        attrs = {
            'name': target_name,
            'auth': 'Container',
            'type': 'javax.sql.DataSource',
            'factory': 'org.apache.commons.dbcp2.BasicDataSourceFactory',
            'driverClassName': 'org.postgresql.Driver',
            'url': url,
            'username': db_user,
            'password': db_pass,
            'maxTotal': '50',
            'maxIdle': '10',
            'maxWaitMillis': '10000',
            'initialSize': '5',
            'validationQuery': 'SELECT 1',
            'testOnBorrow': 'true'
        }

        if existing is None:
            ET.SubElement(root, 'Resource', attrib=attrs)
            log("Resource JDBC 'jdbc/PostgresDS' adicionado ao context.xml", "SUCCESS")
        else:
            # Atualizar atributos existentes
            for k, v in attrs.items():
                existing.set(k, v)
            log("Resource JDBC 'jdbc/PostgresDS' atualizado no context.xml", "SUCCESS")

        tree.write(context_xml, encoding='utf-8', xml_declaration=True)
        log("context.xml atualizado com datasource PostgreSQL", "SUCCESS")
        return True
    except Exception as e:
        log(f"Erro ao configurar datasource do Tomcat: {e}", "ERROR")
        return False
def check_wildfly_environment():
    """
    Verifica o ambiente do WildFly e fornece informações de diagnóstico.
    
    Returns:
        bool: True se o ambiente estiver configurado corretamente, False caso contrário
    """
    print(f"\n{Colors.YELLOW}=== Verificação do Ambiente WildFly ==={Colors.END}")
    
    # Verificar se o diretório do WildFly existe
    if not os.path.exists(WILDFLY_DIR):
        print(f"{Colors.RED}Erro: Diretório do WildFly não encontrado em: {WILDFLY_DIR}{Colors.END}")
        print(f"{Colors.YELLOW}Sugestão: Verifique se o WildFly está instalado corretamente no caminho especificado.{Colors.END}")
        return False
    
    print(f"{Colors.GREEN}✓ Diretório do WildFly encontrado: {WILDFLY_DIR}{Colors.END}")
    
    # Verificar se as pastas essenciais existem
    required_dirs = ["bin", "standalone", "modules"]
    missing_dirs = [d for d in required_dirs if not os.path.exists(os.path.join(WILDFLY_DIR, d))]
    
    if missing_dirs:
        print(f"{Colors.RED}Erro: Diretórios essenciais não encontrados no WildFly: {', '.join(missing_dirs)}{Colors.END}")
        print(f"{Colors.YELLOW}Sugestão: O diretório do WildFly parece estar incompleto. Verifique a instalação.{Colors.END}")
        return False
    
    print(f"{Colors.GREEN}✓ Estrutura de diretórios do WildFly está completa{Colors.END}")
    
    # Verificar configuração standalone.xml
    standalone_xml = os.path.join(WILDFLY_DIR, "standalone", "configuration", "standalone.xml")
    if not os.path.exists(standalone_xml):
        print(f"{Colors.RED}Erro: Arquivo standalone.xml não encontrado em: {standalone_xml}{Colors.END}")
        print(f"{Colors.YELLOW}Sugestão: O arquivo de configuração principal do WildFly está ausente.{Colors.END}")
        return False
    
    print(f"{Colors.GREEN}✓ Arquivo de configuração standalone.xml encontrado{Colors.END}")
    # Exibir a porta configurada do WildFly em verde e registrar no log
    print(f"{Colors.GREEN}✓ Porta configurada do WildFly: {WILDFLY_PORT}{Colors.END}")
    log(f"Porta do WildFly {WILDFLY_PORT} Ok", "SUCCESS")
    
    # Verificar se os scripts de inicialização existem
    if platform.system() == "Windows":
        startup_script = os.path.join(WILDFLY_DIR, "bin", "standalone.bat")
    else:
        startup_script = os.path.join(WILDFLY_DIR, "bin", "standalone.sh")
    
    if not os.path.exists(startup_script):
        print(f"{Colors.RED}Erro: Script de inicialização não encontrado: {startup_script}{Colors.END}")
        print(f"{Colors.YELLOW}Sugestão: Verifique se o WildFly foi instalado corretamente.{Colors.END}")
        return False
    
    print(f"{Colors.GREEN}✓ Script de inicialização encontrado: {os.path.basename(startup_script)}{Colors.END}")
    
    # Verificar permissões do script no Linux/Mac
    if platform.system() != "Windows" and not os.access(startup_script, os.X_OK):
        print(f"{Colors.RED}Erro: Script de inicialização não tem permissão de execução: {startup_script}{Colors.END}")
        print(f"{Colors.YELLOW}Sugestão: Execute 'chmod +x {startup_script}' para dar permissão de execução.{Colors.END}")
        return False
    
    # Verificar JAVA_HOME
    java_home = os.environ.get("JAVA_HOME", "")
    if not java_home or not os.path.exists(java_home):
        java_home = detect_java_home()
        if java_home:
            print(f"{Colors.GREEN}✓ JAVA_HOME detectado automaticamente: {java_home}{Colors.END}")
        else:
            print(f"{Colors.RED}Erro: JAVA_HOME não está definido ou não é válido{Colors.END}")
            print(f"{Colors.YELLOW}Sugestão: Instale o JDK e defina a variável de ambiente JAVA_HOME.{Colors.END}")
            return False
    else:
        print(f"{Colors.GREEN}✓ JAVA_HOME configurado: {java_home}{Colors.END}")
    
    # Verificar a versão do Java
    java_installed, java_version, _ = check_java_version(java_home)
    if java_installed:
        print(f"{Colors.GREEN}✓ Java verificado: {java_version.get('version', 'Versão desconhecida')}{Colors.END}")
    else:
        print(f"{Colors.RED}Erro: O Java não está acessível ou não está instalado corretamente{Colors.END}")
        print(f"{Colors.YELLOW}Sugestão: Verifique a instalação do Java e a configuração do JAVA_HOME.{Colors.END}")
        return False
    
    # Verificar diretório de deployments
    deployments_dir = os.path.join(WILDFLY_DIR, "standalone", "deployments")
    if not os.path.exists(deployments_dir):
        print(f"{Colors.RED}Erro: Diretório de deployments não encontrado: {deployments_dir}{Colors.END}")
        print(f"{Colors.YELLOW}Sugestão: Verifique se o WildFly foi instalado corretamente.{Colors.END}")
        return False
    
    print(f"{Colors.GREEN}✓ Diretório de deployments encontrado{Colors.END}")
    
    # Verificar se o WildFly já está em execução
    if check_server_running(WILDFLY_PORT):
        print(f"{Colors.GREEN}✓ Servidor WildFly já está em execução na porta {WILDFLY_PORT}{Colors.END}")
        
        # Verificar porta de administração
        admin_port = 9990
        if check_server_running(admin_port):
            print(f"{Colors.GREEN}✓ Interface de administração do WildFly está acessível na porta {admin_port}{Colors.END}")
        else:
            print(f"{Colors.YELLOW}Alerta: Interface de administração do WildFly não está acessível na porta {admin_port}{Colors.END}")
            print(f"{Colors.YELLOW}Sugestão: O servidor pode estar em modo independente ou com configuração diferente.{Colors.END}")
    else:
        print(f"{Colors.YELLOW}Alerta: Servidor WildFly não está em execução{Colors.END}")
        print(f"{Colors.YELLOW}Isso é normal se você ainda não iniciou o servidor.{Colors.END}")
    
    print(f"\n{Colors.GREEN}Ambiente do WildFly verificado e está pronto para uso!{Colors.END}")
    return True

def diagnose_tomcat_issues(tomcat_env):
    """
    Diagnostica problemas com a inicialização do Tomcat e exibe informações úteis.
    
    Args:
        tomcat_env (dict): Variáveis de ambiente configuradas para o Tomcat
    """
    # Adicionar diagnóstico adicional
    print(f"\n{Colors.YELLOW}=== Diagnóstico do Tomcat ==={Colors.END}")
    print(f"{Colors.YELLOW}Verificando logs do Tomcat para encontrar possíveis erros...{Colors.END}")
    
    # Verificar arquivo de log do Tomcat
    catalina_log = os.path.join(TOMCAT_DIR, "logs", "catalina.out")
    if not os.path.exists(catalina_log):
        catalina_log = os.path.join(TOMCAT_DIR, "logs", f"catalina.{time.strftime('%Y-%m-%d')}.log")
    
    if os.path.exists(catalina_log):
        # Ler as últimas 20 linhas do log
        try:
            with open(catalina_log, 'r', encoding='utf-8', errors='ignore') as f:
                log_lines = f.readlines()
                last_lines = log_lines[-20:] if len(log_lines) >= 20 else log_lines
                
                print(f"{Colors.YELLOW}Últimas linhas do log do Tomcat:{Colors.END}")
                for line in last_lines:
                    # Destacar linhas de erro
                    if "ERROR" in line or "SEVERE" in line:
                        print(f"{Colors.RED}{line.strip()}{Colors.END}")
                    else:
                        print(f"{Colors.GRAY}{line.strip()}{Colors.END}")
        except Exception as e:
            print(f"{Colors.RED}Erro ao ler logs do Tomcat: {str(e)}{Colors.END}")
    else:
        print(f"{Colors.YELLOW}Nenhum arquivo de log encontrado em: {catalina_log}{Colors.END}")
    
    # Verificar variáveis de ambiente
    print(f"\n{Colors.YELLOW}Variáveis de ambiente do Tomcat:{Colors.END}")
    print(f"{Colors.YELLOW}CATALINA_HOME: {tomcat_env.get('CATALINA_HOME', 'Não definido')}{Colors.END}")
    print(f"{Colors.YELLOW}CATALINA_BASE: {tomcat_env.get('CATALINA_BASE', 'Não definido')}{Colors.END}")
    print(f"{Colors.YELLOW}CATALINA_OPTS: {tomcat_env.get('CATALINA_OPTS', 'Não definido')}{Colors.END}")
    print(f"{Colors.YELLOW}JAVA_HOME: {tomcat_env.get('JAVA_HOME', 'Não definido')}{Colors.END}")
    
    # Verificar a configuração da porta
    server_xml = os.path.join(TOMCAT_DIR, "conf", "server.xml")
    if os.path.exists(server_xml):
        try:
            with open(server_xml, 'r', encoding='utf-8', errors='ignore') as f:
                content = f.read()
                if f'port="{TOMCAT_PORT}"' in content:
                    print(f"{Colors.GREEN}A porta {TOMCAT_PORT} está configurada no server.xml{Colors.END}")
                else:
                    print(f"{Colors.RED}A porta {TOMCAT_PORT} não foi encontrada no server.xml!{Colors.END}")
        except Exception as e:
            print(f"{Colors.RED}Erro ao ler server.xml: {str(e)}{Colors.END}")
    else:
        print(f"{Colors.RED}Arquivo server.xml não encontrado em: {server_xml}{Colors.END}")
    
    # Sugerir soluções
    print(f"\n{Colors.YELLOW}Possíveis soluções:{Colors.END}")
    print(f"{Colors.YELLOW}1. Verifique se o Tomcat já está em execução em outra porta{Colors.END}")
    print(f"{Colors.YELLOW}2. Tente parar e reiniciar o Tomcat manualmente{Colors.END}")
    print(f"{Colors.YELLOW}3. Verifique as permissões dos arquivos e diretórios do Tomcat{Colors.END}")
    print(f"{Colors.YELLOW}4. Verifique se o JAVA_HOME está configurado corretamente{Colors.END}")
    print(f"{Colors.YELLOW}5. Verifique o arquivo server.xml para confirmar a configuração da porta{Colors.END}")

def check_tomcat_environment():
    """
    Verifica o ambiente do Tomcat e fornece informações de diagnóstico.
    
    Returns:
        bool: True se o ambiente estiver configurado corretamente, False caso contrário
    """
    print(f"\n{Colors.YELLOW}=== Verificação do Ambiente Tomcat ==={Colors.END}")
    
    # Verificar se o diretório do Tomcat existe
    if not os.path.exists(TOMCAT_DIR):
        print(f"{Colors.RED}Erro: Diretório do Tomcat não encontrado em: {TOMCAT_DIR}{Colors.END}")
        print(f"{Colors.YELLOW}Sugestão: Verifique se o Tomcat está instalado corretamente no caminho especificado.{Colors.END}")
        return False
    
    print(f"{Colors.GREEN}✓ Diretório do Tomcat encontrado: {TOMCAT_DIR}{Colors.END}")
    
    # Verificar se as pastas essenciais existem
    required_dirs = ["bin", "lib", "conf", "logs", "temp", "webapps", "work"]
    missing_dirs = [d for d in required_dirs if not os.path.exists(os.path.join(TOMCAT_DIR, d))]
    
    if missing_dirs:
        print(f"{Colors.RED}Erro: Diretórios essenciais não encontrados no Tomcat: {', '.join(missing_dirs)}{Colors.END}")
        print(f"{Colors.YELLOW}Sugestão: O diretório do Tomcat parece estar incompleto. Verifique a instalação.{Colors.END}")
        return False
    
    print(f"{Colors.GREEN}✓ Estrutura de diretórios do Tomcat está completa{Colors.END}")
    
    # Verificar server.xml
    server_xml_path = os.path.join(TOMCAT_DIR, "conf", "server.xml")
    if not os.path.exists(server_xml_path):
        print(f"{Colors.RED}Erro: Arquivo server.xml não encontrado em: {server_xml_path}{Colors.END}")
        print(f"{Colors.YELLOW}Sugestão: O arquivo de configuração principal do Tomcat está ausente.{Colors.END}")
        return False
    
    print(f"{Colors.GREEN}✓ Arquivo de configuração server.xml encontrado{Colors.END}")
    
    # Verificar se a porta está configurada corretamente
    try:
        with open(server_xml_path, 'r', encoding='utf-8', errors='ignore') as f:
            server_xml_content = f.read()
            if f'port="{TOMCAT_PORT}"' in server_xml_content:
                print(f"{Colors.GREEN}✓ Porta {TOMCAT_PORT} configurada no server.xml{Colors.END}")
                # Confirmação no log em verde da porta Ok
                log(f"Porta do Tomcat {TOMCAT_PORT} Ok", "SUCCESS")
            else:
                print(f"{Colors.RED}Alerta: Porta {TOMCAT_PORT} não encontrada no server.xml{Colors.END}")
                print(f"{Colors.YELLOW}Atualizando automaticamente o server.xml para usar a porta {TOMCAT_PORT}...{Colors.END}")
                if configure_tomcat_port(TOMCAT_DIR, TOMCAT_PORT):
                    print(f"{Colors.GREEN}✓ Porta atualizada para {TOMCAT_PORT} em server.xml.{Colors.END}")
                    # Confirmação no log em verde após correção automática
                    log(f"Porta do Tomcat {TOMCAT_PORT} Ok", "SUCCESS")
                    # Oferecer reinício automático do Tomcat Standalone
                    ans = input(f"{Colors.CYAN}Deseja reiniciar o Tomcat Standalone agora para aplicar a mudança? (S/N){Colors.END} ").strip().upper()
                    if ans == 'S':
                        if restart_tomcat_server():
                            print(f"{Colors.GREEN}✓ Tomcat reiniciado. Verifique em http://localhost:{TOMCAT_PORT}/{Colors.END}")
                        else:
                            print(f"{Colors.RED}Falha ao reiniciar o Tomcat automaticamente. Reinicie manualmente.{Colors.END}")
                else:
                    print(f"{Colors.RED}Falha ao atualizar a porta no server.xml.{Colors.END}")
    except Exception as e:
        print(f"{Colors.RED}Erro ao ler server.xml: {str(e)}{Colors.END}")
    
    # Verificar se os scripts de inicialização existem
    if platform.system() == "Windows":
        startup_script = os.path.join(TOMCAT_DIR, "bin", "startup.bat")
    else:
        startup_script = os.path.join(TOMCAT_DIR, "bin", "startup.sh")
    
    if not os.path.exists(startup_script):
        print(f"{Colors.RED}Erro: Script de inicialização não encontrado: {startup_script}{Colors.END}")
        print(f"{Colors.YELLOW}Sugestão: Verifique se o Tomcat foi instalado corretamente.{Colors.END}")
        return False
    
    print(f"{Colors.GREEN}✓ Script de inicialização encontrado: {os.path.basename(startup_script)}{Colors.END}")
    
    # Verificar permissões do script no Linux/Mac
    if platform.system() != "Windows" and not os.access(startup_script, os.X_OK):
        print(f"{Colors.RED}Erro: Script de inicialização não tem permissão de execução: {startup_script}{Colors.END}")
        print(f"{Colors.YELLOW}Sugestão: Execute 'chmod +x {startup_script}' para dar permissão de execução.{Colors.END}")
        return False
    
    # Verificar JAVA_HOME
    java_home = os.environ.get("JAVA_HOME", "")
    if not java_home or not os.path.exists(java_home):
        java_home = detect_java_home()
        if java_home:
            print(f"{Colors.GREEN}✓ JAVA_HOME detectado automaticamente: {java_home}{Colors.END}")
        else:
            print(f"{Colors.RED}Erro: JAVA_HOME não está definido ou não é válido{Colors.END}")
            print(f"{Colors.YELLOW}Sugestão: Instale o JDK e defina a variável de ambiente JAVA_HOME.{Colors.END}")
            return False
    else:
        print(f"{Colors.GREEN}✓ JAVA_HOME configurado: {java_home}{Colors.END}")
    
    # Verificar a versão do Java
    java_installed, java_version, _ = check_java_version(java_home)
    if java_installed:
        print(f"{Colors.GREEN}✓ Java verificado: {java_version.get('version', 'Versão desconhecida')}{Colors.END}")
    else:
        print(f"{Colors.RED}Erro: O Java não está acessível ou não está instalado corretamente{Colors.END}")
        print(f"{Colors.YELLOW}Sugestão: Verifique a instalação do Java e a configuração do JAVA_HOME.{Colors.END}")
        return False
    
    # Verificar se o Tomcat já está em execução
    if check_server_running(TOMCAT_PORT):
        print(f"{Colors.GREEN}✓ Servidor Tomcat já está em execução na porta {TOMCAT_PORT}{Colors.END}")
    else:
        # Verificar se está rodando na porta padrão
        if check_server_running(8080):
            print(f"{Colors.YELLOW}Alerta: Servidor Tomcat está em execução na porta padrão 8080, não na porta {TOMCAT_PORT} configurada{Colors.END}")
            print(f"{Colors.YELLOW}Sugestão: Verifique a configuração da porta no server.xml e reinicie o Tomcat.{Colors.END}")
        else:
            print(f"{Colors.YELLOW}Alerta: Servidor Tomcat não está em execução{Colors.END}")
            print(f"{Colors.YELLOW}Isso é normal se você ainda não iniciou o servidor.{Colors.END}")
    
    print(f"\n{Colors.GREEN}Ambiente do Tomcat verificado e está pronto para uso!{Colors.END}")
    return True

def check_environment():
    global MAVEN_CMD
    
    # Verificar Java
    java_installed, java_version = check_java_installed()
    if not java_installed:
        log("Java não encontrado! Erro: " + java_version, "ERROR")
        log("Por favor, instale o Java (JDK) e adicione-o ao PATH.", "ERROR")
        log("Download do Java: https://www.oracle.com/java/technologies/downloads/", "INFO")
        return False
    else:
        log(f"Java encontrado: versão {java_version}", "SUCCESS")
    
    # Verificar Maven
    maven_installed, maven_version, maven_cmd = check_maven_installed()
    if not maven_installed:
        log("Maven não encontrado! Erro: " + maven_version, "ERROR")
        log("Por favor, instale o Maven e adicione-o ao PATH.", "ERROR")
        log("Download do Maven: https://maven.apache.org/download.cgi", "INFO")
        log("Instruções de instalação: https://maven.apache.org/install.html", "INFO")
        return False
    else:
        log(f"Maven encontrado: versão {maven_version}", "SUCCESS")
        MAVEN_CMD = maven_cmd
    
    # Verificar Docker
    docker_installed, docker_version = check_docker_installed()
    if not docker_installed:
        log("Docker não encontrado! Erro: " + docker_version, "WARNING")
        log("Para funcionalidade completa, instale o Docker Desktop.", "WARNING")
        log("Download do Docker: https://www.docker.com/products/docker-desktop/", "INFO")
    else:
        log(f"Docker encontrado: {docker_version}", "SUCCESS")
        
        # Verificar banco de dados PostgreSQL
        db_connected, db_message = check_database_connection()
        if not db_connected:
            log(f"Banco de dados PostgreSQL não disponível: {db_message}", "WARNING")
            log("Certifique-se de que o contêiner PostgreSQL está em execução.", "WARNING")
            log("Execute 'docker-compose up -d' para iniciar o banco de dados.", "INFO")
        else:
            log(f"Banco de dados PostgreSQL: {db_message}", "SUCCESS")
    
    # Verificar diretório do projeto
    if not os.path.exists(PROJECT_DIR):
        log(f"Diretório do projeto não encontrado: {PROJECT_DIR}", "ERROR")
        log("Verifique se o caminho está correto ou se o projeto foi inicializado.", "ERROR")
        return False
    else:
        log(f"Diretório do projeto encontrado: {PROJECT_DIR}", "SUCCESS")
    
    # Verificar pom.xml
    pom_path = os.path.join(PROJECT_DIR, "pom.xml")
    if not os.path.exists(pom_path):
        log(f"Arquivo pom.xml não encontrado em: {pom_path}", "ERROR")
        log("Verifique se o projeto Maven está configurado corretamente.", "ERROR")
        return False
    else:
        log(f"Arquivo pom.xml encontrado: {pom_path}", "SUCCESS")
        
        # Verificar perfis no pom.xml
        with open(pom_path, 'r', encoding='utf-8') as pom_file:
            pom_content = pom_file.read()
            
        has_tomcat_profile = "<id>tomcat</id>" in pom_content
        has_wildfly_profile = "<id>wildfly</id>" in pom_content
        has_run_profile = "<id>run</id>" in pom_content
        
        if has_tomcat_profile:
            log("Perfil 'tomcat' encontrado no pom.xml", "SUCCESS")
        else:
            log("Perfil 'tomcat' não encontrado no pom.xml", "WARNING")
            
        if has_wildfly_profile:
            log("Perfil 'wildfly' encontrado no pom.xml", "SUCCESS")
        else:
            log("Perfil 'wildfly' não encontrado no pom.xml", "WARNING")
            
        if has_run_profile:
            log("Perfil 'run' encontrado no pom.xml", "SUCCESS")
        else:
            log("Perfil 'run' não encontrado no pom.xml", "WARNING")
    
    # Verificar servidores
    if os.path.exists(TOMCAT_DIR):
        log(f"Tomcat encontrado: {TOMCAT_DIR}", "SUCCESS")
        # Verificar diretório webapps
        tomcat_webapps = os.path.join(TOMCAT_DIR, "webapps")
        if os.path.exists(tomcat_webapps):
            log(f"Diretório webapps do Tomcat encontrado: {tomcat_webapps}", "SUCCESS")
        else:
            os.makedirs(tomcat_webapps)
            log(f"Diretório webapps do Tomcat criado: {tomcat_webapps}", "WARNING")
        
        # Verificar se o Tomcat está em execução
        tomcat_running = check_server_running(TOMCAT_PORT)
        if tomcat_running:
            log(f"Servidor Tomcat está em execução na porta {TOMCAT_PORT}", "SUCCESS")
            log(f"Aplicação disponível em: http://localhost:{TOMCAT_PORT}/meu-projeto-java/", "SUCCESS")
        else:
            log(f"Servidor Tomcat não está em execução na porta {TOMCAT_PORT}", "WARNING")
    else:
        log(f"Tomcat não encontrado em: {TOMCAT_DIR}", "WARNING")
        log("O script tentará baixar o Tomcat automaticamente quando necessário.", "INFO")
    
    if os.path.exists(WILDFLY_DIR):
        log(f"WildFly encontrado: {WILDFLY_DIR}", "SUCCESS")
        # Verificar diretório deployments
        wildfly_deployments = os.path.join(WILDFLY_DIR, "standalone", "deployments")
        if os.path.exists(wildfly_deployments):
            log(f"Diretório deployments do WildFly encontrado: {wildfly_deployments}", "SUCCESS")
        else:
            os.makedirs(wildfly_deployments)
            log(f"Diretório deployments do WildFly criado: {wildfly_deployments}", "WARNING")
        
        # Verificar se o WildFly está em execução
        wildfly_running = check_server_running(WILDFLY_PORT)
        if wildfly_running:
            log(f"Servidor WildFly está em execução na porta {WILDFLY_PORT}", "SUCCESS")
            log(f"Aplicação disponível em: http://localhost:{WILDFLY_PORT}/meu-projeto-java/", "SUCCESS")
        else:
            log(f"Servidor WildFly não está em execução na porta {WILDFLY_PORT}. Precisa esta online para deploy.", "INFO")
            # Não iniciamos o WildFly automaticamente aqui, apenas na operação de deploy
            # Se o usuário precisar, ele pode iniciar manualmente o servidor ou usar a opção de deploy que iniciará automaticamente
    else:
        log(f"WildFly não encontrado em: {WILDFLY_DIR}", "WARNING")
        log("Você precisará instalar o WildFly manualmente para usar essa opção.", "INFO")
    
    return True

def execute_maven_command(command, profile=None, additional_params=None):
    """
    Executa um comando Maven com os perfis e parâmetros especificados.
    
    Args:
        command (str): O comando Maven a ser executado (ex: "clean package")
        profile (str, optional): O perfil Maven a ser utilizado (ex: "tomcat", "wildfly")
        additional_params (str, optional): Parâmetros adicionais (ex: "-DskipTests")
        
    Returns:
        dict: Resultado da execução com success, output e exit_code
    """
    global MAVEN_CMD
    
    try:
        # Verificar se o Maven está instalado
        maven_installed, maven_message, maven_cmd = check_maven_installed()
        if not maven_installed:
            log(f"Maven não está disponível: {maven_message}", "ERROR")
            return {
                "success": False,
                "output": maven_message,
                "exit_code": -1
            }
        
        # Atualizar o comando Maven global
        MAVEN_CMD = maven_cmd
            
        os.chdir(PROJECT_DIR)
        
        cmd = [MAVEN_CMD]
        cmd.extend(command.split())
        
        if profile:
            cmd.append(f"-P{profile}")
            
        if additional_params:
            cmd.extend(additional_params.split())
        
        log(f"Executando comando Maven: {' '.join(cmd)}", "INFO")
        
        # Executar o comando Maven e capturar a saída
        process = subprocess.run(
            cmd, 
            stdout=subprocess.PIPE, 
            stderr=subprocess.STDOUT,
            text=True,
            encoding='utf-8'
        )
        
        output = process.stdout
        exit_code = process.returncode
        
        if exit_code == 0:
            log(f"Comando Maven executado com sucesso: {' '.join(cmd)}", "SUCCESS")
            # Verificar se o WAR foi gerado (para comandos package)
            if "package" in cmd:
                war_files = []
                target_dir = os.path.join(PROJECT_DIR, "target")
                if os.path.exists(target_dir):
                    war_files = [f for f in os.listdir(target_dir) if f.endswith(".war")]
                if war_files:
                    log(f"Arquivo WAR gerado: {os.path.join(target_dir, war_files[0])}", "SUCCESS")
                else:
                    log("Nenhum arquivo WAR encontrado após a compilação", "WARNING")
        else:
            log(f"Falha ao executar comando Maven: {' '.join(cmd)}. Código de saída: {exit_code}", "ERROR")
            # Mostrar os últimos 10 linhas de saída em caso de erro
            if output:
                log("Últimas linhas de saída do Maven:", "ERROR")
                output_lines = output.split('\n')
                for line in output_lines[-10:]:
                    log(f"  {line}", "ERROR")
            
        return {
            "success": exit_code == 0,
            "output": output,
            "exit_code": exit_code
        }
        
    except Exception as e:
        log(f"Erro ao executar comando Maven: {str(e)}", "ERROR")
        return {
            "success": False,
            "output": str(e),
            "exit_code": -1
        }
    finally:
        os.chdir(WORKSPACE_DIR)

def start_tomcat_server():
    """
    Inicia o servidor Tomcat 10 usando o perfil Maven 'tomcat'.
    
    Returns:
        dict: Resultado com success, process e type
    """
    log("Iniciando servidor Tomcat...", "INFO")
    
    try:
        os.chdir(PROJECT_DIR)
        
        # Limpar deployments anteriores
        log("Limpando deployments anteriores...", "INFO")
        if os.path.exists(os.path.join(PROJECT_DIR, "target")):
            log("Removendo diretório target...", "INFO")
            shutil.rmtree(os.path.join(PROJECT_DIR, "target"), ignore_errors=True)
            
        if os.path.exists(os.path.join(PROJECT_DIR, "tomcat.8080")):
            log("Removendo diretório de trabalho do Tomcat...", "INFO")
            shutil.rmtree(os.path.join(PROJECT_DIR, "tomcat.8080"), ignore_errors=True)
        
        # Compilar e empacotar a aplicação como WAR
        log("Compilando e empacotando a aplicação como WAR...", "INFO")
        
        # Tentar primeiramente compilar sem executar testes (mais rápido e menos propenso a erros)
        log("Tentando compilar sem executar testes (mvn clean package -Ptomcat -DskipTests)...", "INFO")
        mvn_result = execute_maven_command("clean package", "tomcat", "-DskipTests")
        
        if not mvn_result["success"]:
            log(f"Falha ao empacotar a aplicação como WAR com perfil tomcat. Código de saída: {mvn_result['exit_code']}", "ERROR")
            
            # Tentativa com opções adicionais
            log("Tentando compilar com opções adicionais de depuração...", "INFO")
            mvn_result = execute_maven_command("clean package", "tomcat", "-DskipTests -X")
            
            if not mvn_result["success"]:
                log("Falha ao empacotar a aplicação com depuração ativada. Abortando.", "ERROR")
                return {
                    "success": False,
                    "process": None,
                    "type": "tomcat"
                }
            
            # Perguntar se deseja continuar tentando com opções alternativas
            try_alternative = input(f"{Colors.WARNING}Deseja tentar compilar com configurações alternativas? (S/N){Colors.END} ").strip().upper()
            
            if try_alternative == "S":
                log("Tentando compilação com configurações alternativas...", "INFO")
                
                # Tentar compilar com perfil específico
                log("Tentando compilação com perfil 'tomcat'...", "INFO")
                
                # Verificar se o perfil 'tomcat' existe no pom.xml
                pom_path = os.path.join(PROJECT_DIR, "pom.xml")
                with open(pom_path, 'r', encoding='utf-8') as pom_file:
                    pom_content = pom_file.read()
                
                if "<id>tomcat</id>" not in pom_content:
                    log("Perfil tomcat não encontrado no pom.xml. Por favor, configure-o manualmente.", "WARNING")
                else:
                    log("Perfil tomcat encontrado no pom.xml. Usando-o para compilação...", "INFO")
                
                # Tentar compilar com o perfil específico
                cmd = [MAVEN_CMD, "clean", "package", "-Ptomcat", "-DskipTests"]
                process = subprocess.run(cmd, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
                
                if process.returncode == 0:
                    log("Compilação bem-sucedida com perfil tomcat!", "SUCCESS")
                else:
                    log("Falha na compilação mesmo com perfil tomcat. Tentando abordagem alternativa...", "WARNING")
                    
                    # Tentar usar o exec plugin com as dependências necessárias
                    cmd = [MAVEN_CMD, "clean", "compile", "-Ptomcat", "-X", "org.codehaus.mojo:exec-maven-plugin:3.1.0:java", 
                           "-Dexec.mainClass=com.exemplo.Main", "-Dexec.classpathScope=compile", "-DskipTests"]
                    process = subprocess.run(cmd, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
                    
                    if process.returncode != 0:
                        log("Todas as tentativas de compilação falharam.", "ERROR")
                        return {
                            "success": False,
                            "process": None,
                            "type": "tomcat"
                        }
            else:
                # Usar um perfil específico ou opções alternativas
                cmd = [MAVEN_CMD, "clean", "package", "-Ptomcat", "-DskipTests"]
                process = subprocess.run(cmd, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
                
                if process.returncode != 0:
                    log(f"Falha ao empacotar a aplicação como WAR com opções alternativas. Código de saída: {process.returncode}", "ERROR")
                    return {
                        "success": False,
                        "process": None,
                        "type": "tomcat"
                    }
        else:
            log("Compilação bem-sucedida sem executar testes.", "SUCCESS")
        
        # Verificar se existe uma classe WebServer.java
        web_server_path = os.path.join(PROJECT_DIR, "src", "main", "java", "com", "exemplo", "server", "tomcat", "WebServer.java")
        if os.path.exists(web_server_path):
            log(f"Detectado arquivo WebServer.java para Tomcat: {web_server_path}", "INFO")
            use_embedded = input(f"{Colors.WARNING}Deseja iniciar usando o Tomcat via WebServer? (S/N){Colors.END} ").strip().upper()
            
            if use_embedded == "S":
                log("Iniciando com Tomcat via WebServer usando perfil 'tomcat'...", "INFO")
                
                # Verificar se o perfil tomcat já existe no pom.xml
                pom_path = os.path.join(PROJECT_DIR, "pom.xml")
                with open(pom_path, 'r', encoding='utf-8') as pom_file:
                    pom_content = pom_file.read()
                
                if "<id>tomcat</id>" not in pom_content:
                    log("Perfil tomcat não encontrado no pom.xml. Por favor, configure-o manualmente.", "WARNING")
                else:
                    log("Perfil tomcat encontrado no pom.xml.", "INFO")
                
                # Determinar o caminho relativo do WebServer.java
                web_server_relative = web_server_path.replace(os.path.join(PROJECT_DIR, "src", "main", "java", ""), "").replace("\\", ".").replace(".java", "")
                log(f"Classe principal do servidor: {web_server_relative}", "INFO")
                
                # Compilar e iniciar com o perfil tomcat
                log("Compilando com o perfil tomcat...", "INFO")
                cmd = [MAVEN_CMD, "clean", "compile", "-Ptomcat", "-DskipTests"]
                process = subprocess.run(cmd, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
                
                if process.returncode == 0:
                    log("Compilação bem-sucedida com perfil tomcat", "SUCCESS")
                    
                    # Iniciar o servidor
                    log("Iniciando Tomcat usando perfil tomcat...", "INFO")
                    cmd = [MAVEN_CMD, "exec:java", "-Ptomcat", f"-Dexec.mainClass={web_server_relative}"]
                    process = subprocess.Popen(cmd, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
                    
                    # Aguardar inicialização
                    time.sleep(10)
                    
                    # Verificar se o processo ainda está em execução
                    if process.poll() is None:
                        log(f"Servidor Tomcat embutido iniciado com sucesso (PID: {process.pid})", "SUCCESS")
                        os.chdir(WORKSPACE_DIR)
                        return {
                            "success": True,
                            "process": process,
                            "type": "tomcat"
                        }
                    else:
                        log(f"Falha ao iniciar o servidor Tomcat embutido. Código de saída: {process.returncode}", "ERROR")
                        log("Tentando abordagem alternativa...", "WARNING")
                else:
                    log("Falha na compilação com perfil embedded-tomcat. Tentando abordagem alternativa...", "WARNING")
        
        # Verificar se o WAR foi gerado
        war_files = [f for f in os.listdir(os.path.join(PROJECT_DIR, "target")) if f.endswith(".war")]
        if not war_files:
            log("Arquivo WAR não foi gerado. Tentando empacotar com opções alternativas...", "WARNING")
            
            # Tentar compilar com o plugin tomcat10 e o perfil tomcat
            log("Tentando iniciar com tomcat10:run sem empacotar como WAR usando perfil 'tomcat'...", "INFO")
            cmd = [MAVEN_CMD, "tomcat10:run", "-Ptomcat", "-Dmaven.tomcat.port=8080", "-DskipTests"]
            process = subprocess.Popen(cmd, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
            
            log(f"Processo Tomcat iniciado com PID: {process.pid} (modo alternativo sem WAR)", "SUCCESS")
            log("Aguardando o servidor inicializar...", "INFO")
            
            # Aguardar tempo para o servidor inicializar completamente
            time.sleep(30)
            
            # Verificar se o processo ainda está em execução
            if process.poll() is None:
                log("Servidor Tomcat inicializado com sucesso (modo alternativo).", "SUCCESS")
                return {
                    "success": True,
                    "process": process,
                    "type": "tomcat"
                }
            else:
                log(f"O processo do Tomcat encerrou prematuramente. Código de saída: {process.returncode}", "ERROR")
                # Limpar novamente em caso de falha
                if os.path.exists(os.path.join(PROJECT_DIR, "target")):
                    shutil.rmtree(os.path.join(PROJECT_DIR, "target"), ignore_errors=True)
                
                # Tentar com Tomcat10 como última alternativa
                log("Tentando método alternativo com Tomcat10 usando perfil 'tomcat'...", "INFO")
                try:
                    cmd = [MAVEN_CMD, "tomcat10:run", "-Ptomcat", "-DskipTests"]
                    process = subprocess.Popen(cmd, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
                    
                    log(f"Processo Tomcat10 alternativo iniciado com PID: {process.pid}", "SUCCESS")
                    log("Aguardando o servidor inicializar...", "INFO")
                    
                    # Aguardar inicialização
                    time.sleep(20)
                    
                    if process.poll() is None:
                        log("Servidor Tomcat10 alternativo inicializado com sucesso.", "SUCCESS")
                        return {
                            "success": True,
                            "process": process,
                            "type": "tomcat"
                        }
                    else:
                        log("O processo do Tomcat10 alternativo encerrou prematuramente.", "ERROR")
                except Exception as e:
                    log(f"Erro no método alternativo com Tomcat10: {str(e)}", "ERROR")
                
                return {
                    "success": False,
                    "process": None,
                    "type": "tomcat"
                }
        
        war_file = os.path.join(PROJECT_DIR, "target", war_files[0])
        log(f"Arquivo WAR gerado: {os.path.basename(war_file)}", "SUCCESS")
        
        # Perguntar ao usuário qual método de inicialização do Tomcat deseja usar
        print(f"\n{Colors.CYAN}Escolha o método de inicialização do Tomcat:{Colors.END}")
        print(f"{Colors.END}1. Usar plugin Maven (tomcat10-maven-plugin){Colors.END}")
        print(f"{Colors.END}2. Baixar e usar Tomcat Standalone{Colors.END}")
        print(f"{Colors.END}3. Usar Tomcat via perfil Maven{Colors.END}")
        tomcat_option = input("> ").strip()
        
        # Opção 1: Usar plugin Maven
        if tomcat_option == "1":
            log("Usando plugin Maven para iniciar o Tomcat...", "INFO")
            cmd = [MAVEN_CMD, "tomcat10:run", "-Ptomcat", "-DskipTests"]
            process = subprocess.Popen(cmd, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
            
            time.sleep(10)
            
            if process.poll() is None:
                log(f"Servidor Tomcat iniciado com sucesso via plugin Maven (PID: {process.pid})", "SUCCESS")
                # Exibir link da aplicação
                print(f"\n{Colors.GREEN}Aplicação disponível em: http://localhost:{TOMCAT_PORT}/meu-projeto-java/{Colors.END}")
                log(f"Aplicação disponível em: http://localhost:{TOMCAT_PORT}/meu-projeto-java/", "SUCCESS")
                return {
                    "success": True,
                    "process": process,
                    "type": "tomcat-plugin"
                }
            else:
                log("Falha ao iniciar o Tomcat via plugin Maven.", "ERROR")
        
        # Opção 2: Usar Tomcat Standalone
        elif tomcat_option == "2":
            log("Selecionado uso do Tomcat Standalone...", "INFO")
            
            # Verificar se o Tomcat já existe
            if not os.path.exists(os.path.join(TOMCAT_DIR, "bin", "startup.bat")) and \
               not os.path.exists(os.path.join(TOMCAT_DIR, "bin", "startup.sh")):
                # Baixar e configurar o Tomcat standalone
                log(f"Tomcat não encontrado em: {TOMCAT_DIR}. Tentando baixar...", "WARNING")
                tomcat_result = download_tomcat_server("10.1.35", TOMCAT_DIR)
                
                if not tomcat_result["success"]:
                    log("Falha ao baixar o Tomcat. Tentando método alternativo...", "ERROR")
                    return {
                        "success": False,
                        "process": None,
                        "type": "tomcat"
                    }
            else:
                log(f"Usando instalação existente do Tomcat em: {TOMCAT_DIR}", "INFO")
            
            # Copiar o WAR para o diretório webapps do Tomcat
            log("Copiando arquivo WAR para o diretório webapps do Tomcat...", "INFO")
                
            # Limpar o diretório webapps se necessário
            tomcat_webapps = os.path.join(TOMCAT_DIR, "webapps")
            if not os.path.exists(tomcat_webapps):
                os.makedirs(tomcat_webapps)
                log(f"Diretório webapps do Tomcat criado: {tomcat_webapps}", "INFO")
                
            if os.path.exists(os.path.join(tomcat_webapps, "ROOT")):
                shutil.rmtree(os.path.join(tomcat_webapps, "ROOT"), ignore_errors=True)
                log("Diretório ROOT existente removido do Tomcat", "INFO")
                
            if os.path.exists(os.path.join(tomcat_webapps, "ROOT.war")):
                os.remove(os.path.join(tomcat_webapps, "ROOT.war"))
                log("Arquivo ROOT.war existente removido do Tomcat", "INFO")
            
            # Copiar o WAR para o diretório webapps com o nome ROOT.war
            war_dest = os.path.join(tomcat_webapps, "ROOT.war")
            shutil.copy2(war_file, war_dest)
            log(f"Arquivo WAR copiado para Tomcat: {war_dest}", "SUCCESS")
            
            # Iniciar Tomcat em foreground (console aqui)
            log("Iniciando o Tomcat em foreground (console neste terminal)...", "INFO")
            bin_dir = os.path.join(TOMCAT_DIR, "bin")
            try:
                if platform.system() == "Windows":
                    cmd = f'"{os.path.join(bin_dir, "catalina.bat")}" run'
                    process = subprocess.Popen(cmd, shell=True, cwd=bin_dir)
                else:
                    cmd = [os.path.join(bin_dir, "catalina.sh"), "run"]
                    process = subprocess.Popen(cmd, cwd=bin_dir)

                print(f"{Colors.CYAN}Tomcat em execução no foreground. Pressione Ctrl+C para parar.{Colors.END}")
                print(f"{Colors.GREEN}Aplicação: http://localhost:{TOMCAT_PORT}/meu-projeto-java/{Colors.END}")
                process.wait()
                log("Tomcat finalizado.", "INFO")
                return {
                    "success": True,
                    "process": process,
                    "type": "tomcat-standalone-foreground"
                }
            except KeyboardInterrupt:
                log("Interrompido pelo usuário. Enviando shutdown ao Tomcat...", "INFO")
                try:
                    if platform.system() == "Windows":
                        subprocess.run([os.path.join(bin_dir, "shutdown.bat")], shell=True, cwd=bin_dir)
                    else:
                        subprocess.run([os.path.join(bin_dir, "shutdown.sh")], cwd=bin_dir)
                except Exception:
                    pass
                return {
                    "success": True,
                    "process": None,
                    "type": "tomcat-standalone-foreground"
                }
        
        # Opção 3: Usar Tomcat via perfil Maven
        elif tomcat_option == "3":
            # Verificar se o perfil 'tomcat' existe no pom.xml
            pom_path = os.path.join(PROJECT_DIR, "pom.xml")
            with open(pom_path, 'r', encoding='utf-8') as pom_file:
                pom_content = pom_file.read()
            
            if "<id>tomcat</id>" not in pom_content:
                log("Perfil 'tomcat' não encontrado no pom.xml. Por favor, configure-o manualmente.", "ERROR")
                return {
                    "success": False,
                    "process": None,
                    "type": "tomcat"
                }
            
            log("Iniciando Tomcat via perfil Maven...", "INFO")
            cmd = [MAVEN_CMD, "tomcat10:run", "-Ptomcat", "-DskipTests"]
            process = subprocess.Popen(cmd, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
            
            time.sleep(10)
            
            if process.poll() is None:
                log(f"Servidor Tomcat iniciado com sucesso via perfil Maven (PID: {process.pid})", "SUCCESS")
                return {
                    "success": True,
                    "process": process,
                    "type": "tomcat-profile"
                }
            else:
                log("Falha ao iniciar o Tomcat via perfil Maven.", "ERROR")
        
        # Opção padrão ou inválida
        log("Opção inválida ou nenhuma opção selecionada. Tentando método padrão...", "WARNING")
        cmd = [MAVEN_CMD, "tomcat10:run", "-Ptomcat", "-DskipTests"]
        process = subprocess.Popen(cmd, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
        
        time.sleep(10)
        
        if process.poll() is None:
            log(f"Servidor Tomcat iniciado com sucesso (método padrão) (PID: {process.pid})", "SUCCESS")
            return {
                "success": True,
                "process": process,
                "type": "tomcat-default"
            }
        else:
            log("Falha ao iniciar o Tomcat com método padrão.", "ERROR")
            return {
                "success": False,
                "process": None,
                "type": "tomcat"
            }
        
    except Exception as e:
        log(f"Erro ao iniciar o servidor Tomcat: {str(e)}", "ERROR")
        return {
            "success": False,
            "process": None,
            "type": "tomcat"
        }
    finally:
        os.chdir(WORKSPACE_DIR)

def start_wildfly_server():
    """
    Inicia o servidor WildFly usando o perfil Maven 'wildfly'.
    
    Returns:
        dict: Resultado com success, process e type
    """
    log("Iniciando servidor WildFly...", "INFO")
    
    try:
        os.chdir(PROJECT_DIR)
        
        # Limpar deployments anteriores
        log("Limpando deployments anteriores...", "INFO")
        if os.path.exists(os.path.join(PROJECT_DIR, "target")):
            log("Removendo diretório target...", "INFO")
            shutil.rmtree(os.path.join(PROJECT_DIR, "target"), ignore_errors=True)
        
        # Compilar e empacotar a aplicação como WAR
        log("Compilando e empacotando a aplicação como WAR...", "INFO")
        
        # Tentar primeiramente compilar sem executar testes (mais rápido e menos propenso a erros)
        log("Tentando compilar sem executar testes (mvn clean package -Pwildfly -DskipTests)...", "INFO")
        mvn_result = execute_maven_command("clean package", "wildfly", "-DskipTests")
        
        if not mvn_result["success"]:
            log(f"Falha ao empacotar a aplicação como WAR com perfil wildfly. Código de saída: {mvn_result['exit_code']}", "ERROR")
            
            # Tentativa com opções adicionais
            log("Tentando compilar com opções adicionais de depuração...", "INFO")
            mvn_result = execute_maven_command("clean package", "wildfly", "-DskipTests -X")
            
            if not mvn_result["success"]:
                log("Falha ao empacotar a aplicação com depuração ativada. Abortando.", "ERROR")
                return {
                    "success": False,
                    "process": None,
                    "type": "wildfly"
                }
            
            # Perguntar se deseja continuar tentando com opções alternativas
            try_wildfly = input(f"{Colors.WARNING}Deseja tentar compilar com configurações alternativas? (S/N){Colors.END} ").strip().upper()
            
            if try_wildfly == "S":
                log("Tentando compilação com configurações alternativas...", "INFO")
                
                # Tentar compilar com perfil específico
                log("Tentando compilação com perfil 'wildfly'...", "INFO")
                
                # Verificar se o perfil 'wildfly' existe no pom.xml
                pom_path = os.path.join(PROJECT_DIR, "pom.xml")
                with open(pom_path, 'r', encoding='utf-8') as pom_file:
                    pom_content = pom_file.read()
                
                if "<id>wildfly</id>" not in pom_content:
                    log("Perfil wildfly não encontrado no pom.xml. Por favor, configure-o manualmente.", "WARNING")
                else:
                    log("Perfil wildfly encontrado no pom.xml. Usando-o para compilação...", "INFO")
                
                # Tentar compilar com o perfil específico
                cmd = [MAVEN_CMD, "clean", "package", "-Pwildfly", "-DskipTests"]
                process = subprocess.run(cmd, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
                
                if process.returncode == 0:
                    log("Compilação bem-sucedida com perfil wildfly!", "SUCCESS")
                else:
                    log("Falha na compilação mesmo com perfil wildfly. Tentando abordagem alternativa...", "WARNING")
                    
                    # Tentar usar o wildfly-maven-plugin
                    cmd = [MAVEN_CMD, "wildfly:deploy", "-Pwildfly", "-DskipTests"]
                    process = subprocess.run(cmd, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
                    
                    if process.returncode != 0:
                        log("Todas as tentativas de compilação falharam.", "ERROR")
                        return {
                            "success": False,
                            "process": None,
                            "type": "wildfly"
                        }
            else:
                log("Operação cancelada pelo usuário após falha na compilação.", "WARNING")
                return {
                    "success": False,
                    "process": None,
                    "type": "wildfly"
                }
        else:
            log("Compilação bem-sucedida sem executar testes.", "SUCCESS")
        
        # Verificar se o WAR foi gerado
        war_files = [f for f in os.listdir(os.path.join(PROJECT_DIR, "target")) if f.endswith(".war")]
        if not war_files:
            log("Arquivo WAR não foi gerado. Tentando empacotar com opções alternativas...", "WARNING")
            return {
                "success": False,
                "process": None,
                "type": "wildfly"
            }
        
        war_file = os.path.join(PROJECT_DIR, "target", war_files[0])
        log(f"Arquivo WAR gerado: {os.path.basename(war_file)}", "SUCCESS")
        
        # Perguntar ao usuário qual método de inicialização do WildFly deseja usar
        print(f"\n{Colors.CYAN}Escolha o método de inicialização do WildFly:{Colors.END}")
        print(f"{Colors.END}1. Usar plugin Maven (wildfly-maven-plugin){Colors.END}")
        print(f"{Colors.END}2. Usar WildFly Standalone{Colors.END}")
        wildfly_option = input("> ").strip()
        
        # Opção 1: Usar plugin Maven
        if wildfly_option == "1":
            log("Usando plugin Maven para iniciar o WildFly...", "INFO")
            cmd = [MAVEN_CMD, "wildfly:run", "-Pwildfly", "-DskipTests"]
            process = subprocess.Popen(cmd, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
            
            time.sleep(20)
            
            if process.poll() is None:
                log(f"Servidor WildFly iniciado com sucesso via plugin Maven (PID: {process.pid})", "SUCCESS")
                # Exibir link da aplicação
                print(f"\n{Colors.GREEN}Aplicação disponível em: http://localhost:{WILDFLY_PORT}/meu-projeto-java/{Colors.END}")
                log(f"Aplicação disponível em: http://localhost:{WILDFLY_PORT}/meu-projeto-java/", "SUCCESS")
                return {
                    "success": True,
                    "process": process,
                    "type": "wildfly-plugin"
                }
            else:
                log("Falha ao iniciar o WildFly via plugin Maven.", "ERROR")
        
        # Opção 2: Usar WildFly Standalone
        elif wildfly_option == "2":
            log("Selecionado uso do WildFly Standalone...", "INFO")
            
            # Verificar se o WildFly já existe
            if not os.path.exists(os.path.join(WILDFLY_DIR, "bin", "standalone.bat")) and \
               not os.path.exists(os.path.join(WILDFLY_DIR, "bin", "standalone.sh")):
                log(f"WildFly não encontrado em: {WILDFLY_DIR}.", "ERROR")
                return {
                    "success": False,
                    "process": None,
                    "type": "wildfly"
                }
            else:
                log(f"Usando instalação existente do WildFly em: {WILDFLY_DIR}", "INFO")
            
            # Copiar o WAR para o diretório deployments do WildFly
            log("Copiando arquivo WAR para o diretório deployments do WildFly...", "INFO")
            
            # Criar o diretório deployments se não existir
            deployments_dir = os.path.join(WILDFLY_DIR, "standalone", "deployments")
            if not os.path.exists(deployments_dir):
                os.makedirs(deployments_dir)
                log(f"Diretório deployments do WildFly criado: {deployments_dir}", "INFO")
            
            # Limpar o diretório deployments se necessário
            for item in ["ROOT.war", "ROOT.war.deployed", "ROOT.war.failed"]:
                item_path = os.path.join(deployments_dir, item)
                if os.path.exists(item_path):
                    os.remove(item_path)
                    log(f"Arquivo {item} existente removido do WildFly", "INFO")
            
            # Copiar o WAR para o diretório deployments com o nome ROOT.war
            war_dest = os.path.join(deployments_dir, "ROOT.war")
            shutil.copy2(war_file, war_dest)
            log(f"Arquivo WAR copiado para WildFly: {war_dest}", "SUCCESS")
            
            # Criar o arquivo de marcador .dodeploy para indicar que deve ser implantado
            dodeploy_marker = os.path.join(deployments_dir, "ROOT.war.dodeploy")
            with open(dodeploy_marker, 'w') as f:
                pass
            log("Arquivo marcador .dodeploy criado para o WildFly", "INFO")
            
            # Iniciar o WildFly
            log("Iniciando o WildFly Standalone...", "INFO")
            if platform.system() == "Windows":
                cmd = [os.path.join(WILDFLY_DIR, "bin", "standalone.bat")]
                process = subprocess.Popen(cmd, shell=True, cwd=os.path.join(WILDFLY_DIR, "bin"))
            else:
                cmd = [os.path.join(WILDFLY_DIR, "bin", "standalone.sh")]
                process = subprocess.Popen(cmd, shell=True, cwd=os.path.join(WILDFLY_DIR, "bin"))
            
            # Aguardar inicialização
            log("Aguardando inicialização do WildFly Standalone...", "INFO")
            time.sleep(30)
            
            # Em sistemas Windows, não podemos obter diretamente o PID do processo Java
            # então vamos assumir que se o script não falhou, o WildFly está rodando
            log("WildFly Standalone inicializado com sucesso.", "SUCCESS")
            # Exibir link da aplicação
            print(f"\n{Colors.GREEN}Aplicação disponível em: http://localhost:{WILDFLY_PORT}/meu-projeto-java/{Colors.END}")
            log(f"Aplicação disponível em: http://localhost:{WILDFLY_PORT}/meu-projeto-java/", "SUCCESS")
            return {
                "success": True,
                "process": process,
                "type": "wildfly-standalone"
            }
        
        # Opção padrão ou inválida
        log("Opção inválida ou nenhuma opção selecionada. Tentando método padrão...", "WARNING")
        cmd = [MAVEN_CMD, "wildfly:run", "-Pwildfly", "-DskipTests"]
        process = subprocess.Popen(cmd, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
        
        time.sleep(20)
        
        if process.poll() is None:
            log(f"Servidor WildFly iniciado com sucesso (método padrão) (PID: {process.pid})", "SUCCESS")
            # Exibir link da aplicação
            print(f"\n{Colors.GREEN}Aplicação disponível em: http://localhost:{WILDFLY_PORT}/meu-projeto-java/{Colors.END}")
            log(f"Aplicação disponível em: http://localhost:{WILDFLY_PORT}/meu-projeto-java/", "SUCCESS")
            return {
                "success": True,
                "process": process,
                "type": "wildfly-default"
            }
        else:
            log("Falha ao iniciar o WildFly com método padrão.", "ERROR")
            return {
                "success": False,
                "process": None,
                "type": "wildfly"
            }
        
    except Exception as e:
        log(f"Erro ao iniciar o servidor WildFly: {str(e)}", "ERROR")
        return {
            "success": False,
            "process": None,
            "type": "wildfly"
        }
    finally:
        os.chdir(WORKSPACE_DIR)

def download_tomcat_server(tomcat_version="10.1.35", destination_path=None):
    """
    Baixa e configura o servidor Apache Tomcat.
    
    Args:
        tomcat_version (str): Versão do Tomcat a ser baixada
        destination_path (str): Caminho para instalar o Tomcat
    
    Returns:
        dict: Resultado com success, path e version
    """
    if destination_path is None:
        # Agora sempre instala dentro do diretório server
        if not os.path.exists(SERVER_DIR):
            os.makedirs(SERVER_DIR, exist_ok=True)
        destination_path = os.path.join(SERVER_DIR, f"apache-tomcat-{tomcat_version}")
    
    log(f"Verificando Tomcat em: {destination_path}", "INFO")
    
    try:
        # Verificar se o Tomcat já foi baixado
        if os.path.exists(destination_path):
            log(f"Diretório do Tomcat já existe em: {destination_path}", "INFO")
            
            # Verificar se é realmente uma instalação do Tomcat
            if (os.path.exists(os.path.join(destination_path, "bin", "startup.bat")) or 
                os.path.exists(os.path.join(destination_path, "bin", "startup.sh"))) and \
               os.path.exists(os.path.join(destination_path, "conf", "server.xml")):
                log("Instalação do Tomcat já existente detectada.", "SUCCESS")
                return {
                    "success": True,
                    "path": destination_path,
                    "version": tomcat_version
                }
            else:
                log("Diretório Tomcat existente parece estar corrompido ou incompleto.", "WARNING")
                print(f"{Colors.YELLOW}O diretório {destination_path} existe, mas não parece ser uma instalação válida do Tomcat.{Colors.END}")
                download_again = input(f"{Colors.CYAN}Deseja tentar baixar novamente? (S/N){Colors.END} ").strip().upper()
                
                if download_again != "S":
                    log("Download do Tomcat cancelado pelo usuário.", "WARNING")
                    return {
                        "success": False,
                        "path": None,
                        "version": None
                    }
                
                # Fazer backup do diretório existente
                backup_dir = f"{destination_path}.bak.{datetime.now().strftime('%Y%m%d%H%M%S')}"
                log(f"Fazendo backup do diretório Tomcat existente para: {backup_dir}", "INFO")
                shutil.move(destination_path, backup_dir)
        
        # Criar diretório para o Tomcat
        os.makedirs(destination_path, exist_ok=True)
        
        # URL para download do Tomcat (versão específica para Windows x64)
        tomcat_url = f"https://archive.apache.org/dist/tomcat/tomcat-10/v{tomcat_version}/bin/apache-tomcat-{tomcat_version}.zip"
        tomcat_zip = os.path.join(tempfile.gettempdir(), f"apache-tomcat-{tomcat_version}.zip")
        
        # Baixar o Tomcat
        log(f"Baixando Tomcat de: {tomcat_url}", "INFO")
        if not http_download(tomcat_url, tomcat_zip, timeout=60, max_retries=3, backoff_seconds=3):
            return {
                "success": False,
                "path": None,
                "version": None
            }
        
        # Extrair o arquivo ZIP
        log(f"Extraindo Tomcat para: {destination_path}", "INFO")
        with zipfile.ZipFile(tomcat_zip, 'r') as zip_ref:
            zip_ref.extractall(tempfile.gettempdir())
        
        # Mover o conteúdo para o destino final
        extracted_dir = os.path.join(tempfile.gettempdir(), f"apache-tomcat-{tomcat_version}")
        
        # Verificar qual pasta foi criada após a extração
        if not os.path.exists(extracted_dir):
            # Tentar variações do nome da pasta
            possible_dirs = [
                os.path.join(tempfile.gettempdir(), f"tomcat-{tomcat_version}"),
                os.path.join(tempfile.gettempdir(), f"apache-tomcat-{tomcat_version}"),
                os.path.join(tempfile.gettempdir(), f"apache-tomcat-{tomcat_version}-windows-x64")
            ]
            
            for dir_path in possible_dirs:
                if os.path.exists(dir_path):
                    extracted_dir = dir_path
                    break
            
            # Se ainda não encontrou, procurar qualquer pasta com "tomcat" no nome
            if not os.path.exists(extracted_dir):
                tomcat_folders = [d for d in os.listdir(tempfile.gettempdir()) 
                                  if os.path.isdir(os.path.join(tempfile.gettempdir(), d)) and "tomcat" in d.lower()]
                if tomcat_folders:
                    extracted_dir = os.path.join(tempfile.gettempdir(), tomcat_folders[0])
        
        if os.path.exists(extracted_dir):
            # Copiar o conteúdo para o destino final
            for item in os.listdir(extracted_dir):
                s = os.path.join(extracted_dir, item)
                d = os.path.join(destination_path, item)
                if os.path.isdir(s):
                    shutil.copytree(s, d, dirs_exist_ok=True)
                else:
                    shutil.copy2(s, d)
        else:
            log("Não foi possível encontrar a pasta extraída do Tomcat no diretório temporário", "ERROR")
            return {
                "success": False,
                "path": None,
                "version": None
            }
        
        # Limpar arquivos temporários
        os.remove(tomcat_zip)
        shutil.rmtree(extracted_dir, ignore_errors=True)
        
        # Verificar se o download e a extração foram bem-sucedidos
        if (os.path.exists(os.path.join(destination_path, "bin", "startup.bat")) or 
            os.path.exists(os.path.join(destination_path, "bin", "startup.sh"))) and \
           os.path.exists(os.path.join(destination_path, "conf", "server.xml")):
            log(f"Apache Tomcat {tomcat_version} baixado e configurado com sucesso em: {destination_path}", "SUCCESS")
            
            # Tornar os scripts executáveis
            if platform.system() != "Windows" and os.path.exists(os.path.join(destination_path, "bin")):
                log("Definindo permissões de execução para scripts .sh...", "INFO")
                for script in os.listdir(os.path.join(destination_path, "bin")):
                    if script.endswith(".sh"):
                        script_path = os.path.join(destination_path, "bin", script)
                        os.chmod(script_path, 0o755)
            
            return {
                "success": True,
                "path": destination_path,
                "version": tomcat_version
            }
        else:
            log(f"Falha ao verificar a instalação do Tomcat em: {destination_path}", "ERROR")
            return {
                "success": False,
                "path": None,
                "version": None
            }
    except Exception as e:
        log(f"Erro ao baixar ou configurar o Tomcat: {str(e)}", "ERROR")
        return {
            "success": False,
            "path": None,
            "version": None
        }

def clean_all_deployments():
    """Limpa todos os deployments e arquivos temporários."""
    log("Limpando todos os deployments e arquivos temporários...", "INFO")
    
    try:
        # Limpar diretório target do projeto
        if os.path.exists(os.path.join(PROJECT_DIR, "target")):
            log("Removendo diretório target do projeto...", "INFO")
            shutil.rmtree(os.path.join(PROJECT_DIR, "target"), ignore_errors=True)
        
        # Limpar deployments do Tomcat
        if os.path.exists(TOMCAT_DIR):
            tomcat_webapps = os.path.join(TOMCAT_DIR, "webapps")
            if os.path.exists(tomcat_webapps):
                log("Limpando deployments do Tomcat...", "INFO")
                # Remover ROOT.war e diretório ROOT
                for item in ["ROOT", "ROOT.war"]:
                    item_path = os.path.join(tomcat_webapps, item)
                    if os.path.exists(item_path):
                        if os.path.isdir(item_path):
                            shutil.rmtree(item_path, ignore_errors=True)
                            log(f"Diretório {item} removido do Tomcat", "INFO")
                        else:
                            os.remove(item_path)
                            log(f"Arquivo {item} removido do Tomcat", "INFO")
        
        # Limpar deployments do WildFly
        if os.path.exists(WILDFLY_DIR):
            wildfly_deployments = os.path.join(WILDFLY_DIR, "standalone", "deployments")
            if os.path.exists(wildfly_deployments):
                log("Limpando deployments do WildFly...", "INFO")
                # Remover arquivos relacionados a deployments
                for item in ["ROOT.war", "ROOT.war.deployed", "ROOT.war.failed", "ROOT.war.dodeploy"]:
                    item_path = os.path.join(wildfly_deployments, item)
                    if os.path.exists(item_path):
                        os.remove(item_path)
                        log(f"Arquivo {item} removido do WildFly", "INFO")
        
        # Limpar diretórios temporários de trabalho
        for temp_dir in ["tomcat.8080", "tmp"]:
            if os.path.exists(os.path.join(PROJECT_DIR, temp_dir)):
                log(f"Removendo diretório temporário: {temp_dir}...", "INFO")
                shutil.rmtree(os.path.join(PROJECT_DIR, temp_dir), ignore_errors=True)
        
        log("Todos os deployments e arquivos temporários foram limpos com sucesso.", "SUCCESS")
        return True
    except Exception as e:
        log(f"Erro ao limpar deployments: {str(e)}", "ERROR")
        return False

def run_maven_tests():
    """
    Executa os testes do Maven com JaCoCo.
    
    Returns:
        bool: True se os testes foram executados com sucesso, False caso contrário
    """
    log("Executando testes Maven com JaCoCo...", "INFO")
    
    try:
        os.chdir(PROJECT_DIR)
        
        # Executar testes com JaCoCo
        mvn_result = execute_maven_command("clean test verify")
        
        if mvn_result["success"]:
            log("Testes Maven executados com sucesso.", "SUCCESS")
            
            # Verificar se o relatório do JaCoCo foi gerado
            jacoco_report = os.path.join(PROJECT_DIR, "target", "site", "jacoco", "index.html")
            if os.path.exists(jacoco_report):
                log(f"Relatório do JaCoCo gerado em: {jacoco_report}", "SUCCESS")
                
                # Abrir o relatório no navegador
                if platform.system() == "Windows":
                    os.startfile(jacoco_report)
                elif platform.system() == "Darwin":  # macOS
                    subprocess.run(["open", jacoco_report])
                else:  # Linux
                    subprocess.run(["xdg-open", jacoco_report])
            else:
                log("Relatório do JaCoCo não foi gerado.", "WARNING")
            
            return True
        else:
            log("Falha ao executar testes Maven.", "ERROR")
            return False
    except Exception as e:
        log(f"Erro ao executar testes Maven: {str(e)}", "ERROR")
        return False
    finally:
        os.chdir(WORKSPACE_DIR)

def run_maven_tests_module():
    """
    Executa os testes do Maven com JaCoCo no módulo específico.
    
    Returns:
        bool: True se os testes foram executados com sucesso, False caso contrário
    """
    log("Executando testes Maven com JaCoCo no módulo...", "INFO")
    
    try:
        os.chdir(WORKSPACE_DIR)
        
        # Executar testes com JaCoCo no módulo específico
        cmd = [MAVEN_CMD, "-q", "-f", f"{PROJECT_DIR}/pom.xml", "clean", "test", "verify"]
        process = subprocess.run(cmd, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
        
        if process.returncode == 0:
            log("Testes Maven executados com sucesso no módulo.", "SUCCESS")
            
            # Verificar se o relatório do JaCoCo foi gerado
            jacoco_report = os.path.join(PROJECT_DIR, "target", "site", "jacoco", "index.html")
            if os.path.exists(jacoco_report):
                log(f"Relatório do JaCoCo gerado em: {jacoco_report}", "SUCCESS")
                
                # Abrir o relatório no navegador
                if platform.system() == "Windows":
                    os.startfile(jacoco_report)
                elif platform.system() == "Darwin":  # macOS
                    subprocess.run(["open", jacoco_report])
                else:  # Linux
                    subprocess.run(["xdg-open", jacoco_report])
            else:
                log("Relatório do JaCoCo não foi gerado.", "WARNING")
            
            return True
        else:
            log(f"Falha ao executar testes Maven no módulo. Código de saída: {process.returncode}", "ERROR")
            return False
    except Exception as e:
        log(f"Erro ao executar testes Maven no módulo: {str(e)}", "ERROR")
        return False

def run_embedded_tomcat():
    """
    Executa o Tomcat incorporado usando o perfil Maven.
    
    Returns:
        bool: True se o servidor foi iniciado com sucesso, False caso contrário
    """
    log("Iniciando Tomcat incorporado usando perfil Maven...", "INFO")
    
    try:
        os.chdir(WORKSPACE_DIR)
        
        # Iniciar Tomcat incorporado usando o perfil 'run'
        cmd = [MAVEN_CMD, "-q", "-f", f"{PROJECT_DIR}/pom.xml", "-Prun"]
        process = subprocess.Popen(cmd, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
        
        time.sleep(10)
        
        if process.poll() is None:
            log(f"Tomcat incorporado iniciado com sucesso (PID: {process.pid}).", "SUCCESS")
            return True
        else:
            log("Falha ao iniciar Tomcat incorporado.", "ERROR")
            return False
    except Exception as e:
        log(f"Erro ao iniciar Tomcat incorporado: {str(e)}", "ERROR")
        return False

def stop_tomcat_server():
    """
    Para o servidor Tomcat se estiver em execução.
    
    Returns:
        bool: True se o servidor foi parado com sucesso, False caso contrário
    """
    log("Parando servidor Tomcat...", "INFO")
    
    try:
        if platform.system() == "Windows":
            cmd = [os.path.join(TOMCAT_DIR, "bin", "shutdown.bat")]
            subprocess.run(cmd, shell=True, check=False)
        else:
            cmd = [os.path.join(TOMCAT_DIR, "bin", "shutdown.sh")]
            subprocess.run(cmd, shell=True, check=False)
        
        # Aguardar o servidor parar
        time.sleep(5)
        log("Servidor Tomcat parado com sucesso", "SUCCESS")
        return True
    except Exception as e:
        log(f"Erro ao parar o servidor Tomcat: {str(e)}", "ERROR")
        return False

def restart_tomcat_server():
    """
    Reinicia o Tomcat Standalone usando os scripts do diretório `TOMCAT_DIR`.
    Retorna True se reiniciado com sucesso.
    """
    try:
        stopped = False
        # Tentar parar com script
        if platform.system() == "Windows":
            shutdown = os.path.join(TOMCAT_DIR, "bin", "shutdown.bat")
            if os.path.exists(shutdown):
                subprocess.run([shutdown], shell=True, cwd=os.path.join(TOMCAT_DIR, "bin"))
                stopped = True
        else:
            shutdown = os.path.join(TOMCAT_DIR, "bin", "shutdown.sh")
            if os.path.exists(shutdown):
                subprocess.run([shutdown], cwd=os.path.join(TOMCAT_DIR, "bin"))
                stopped = True
        time.sleep(3)

        # Iniciar novamente
        if platform.system() == "Windows":
            startup = os.path.join(TOMCAT_DIR, "bin", "startup.bat")
            if not os.path.exists(startup):
                log("startup.bat não encontrado no Tomcat.", "ERROR")
                return False
            subprocess.run([startup], shell=True, cwd=os.path.join(TOMCAT_DIR, "bin"))
        else:
            startup = os.path.join(TOMCAT_DIR, "bin", "startup.sh")
            if not os.path.exists(startup):
                log("startup.sh não encontrado no Tomcat.", "ERROR")
                return False
            subprocess.run([startup], cwd=os.path.join(TOMCAT_DIR, "bin"))

        log("Tomcat reiniciado.", "SUCCESS")
        return True
    except Exception as e:
        log(f"Falha ao reiniciar Tomcat: {e}", "ERROR")
        return False

def stop_wildfly_server():
    """
    Para o servidor WildFly se estiver em execução.
    
    Returns:
        bool: True se o servidor foi parado com sucesso, False caso contrário
    """
    log("Parando servidor WildFly...", "INFO")
    
    try:
        if platform.system() == "Windows":
            # Usar o JBoss CLI para parar o servidor
            cmd = [os.path.join(WILDFLY_DIR, "bin", "jboss-cli.bat"), "--connect", "--command=:shutdown"]
            subprocess.run(cmd, shell=True, check=False)
        else:
            cmd = [os.path.join(WILDFLY_DIR, "bin", "jboss-cli.sh"), "--connect", "--command=:shutdown"]
            subprocess.run(cmd, shell=True, check=False)
        
        # Aguardar o servidor parar
        time.sleep(5)
        log("Servidor WildFly parado com sucesso", "SUCCESS")
        return True
    except Exception as e:
        log(f"Erro ao parar o servidor WildFly: {str(e)}", "ERROR")
        return False

def stop_all_servers():
    """
    Para todos os servidores em execução.
    
    Returns:
        bool: True se todos os servidores foram parados com sucesso, False caso contrário
    """
    log("Parando todos os servidores...", "INFO")
    
    tomcat_stopped = stop_tomcat_server()
    wildfly_stopped = stop_wildfly_server()
    
    if tomcat_stopped and wildfly_stopped:
        log("Todos os servidores foram parados com sucesso", "SUCCESS")
    else:
        log("Alguns servidores podem não ter sido parados corretamente", "WARNING")
    
    return tomcat_stopped and wildfly_stopped

def main():
    """Função principal que exibe o menu simplificado e processa as opções."""
    global CURRENT_SERVER
    # Parser de argumentos para overrides
    parser = build_arg_parser()
    # Ignorar argv[0]
    args, unknown = parser.parse_known_args()
    if unknown:
        log(f"Argumentos desconhecidos ignorados: {' '.join(unknown)}", "WARNING")

    # Aplicar overrides de diretórios
    update_server_dirs(args.tomcat_dir, args.wildfly_dir)
    log(f"Tomcat efetivo: {TOMCAT_DIR}", "INFO")
    log(f"WildFly efetivo: {WILDFLY_DIR}", "INFO")

    # Se for apenas checar ambiente e sair
    if getattr(args, 'only_check', False):
        log("--only-check detectado: verificando ambiente e saindo...", "INFO")
        check_environment()
        return
    
    # Variáveis para controlar os servidores em execução
    tomcat_process = None
    wildfly_process = None
    
    # Banner de inicialização
    print(f"{Colors.HEADER}{'=' * 80}{Colors.END}")
    print(f"{Colors.HEADER}{'Gerenciador de Deploy Java':^80}{Colors.END}")
    print(f"{Colors.HEADER}{'=' * 80}{Colors.END}")
    
    exit_menu = False
    
    while not exit_menu:
        print(f"\n{Colors.CYAN}Menu Principal:{Colors.END}")
        print(f"{Colors.BLUE}1. Verificar ambiente (Java, Maven, Docker, BD){Colors.END}")
        print(f"{Colors.BLUE}2. Deploy em Tomcat {Colors.CYAN}[Porta: {TOMCAT_PORT}]{Colors.END}")
        print(f"{Colors.BLUE}3. Iniciar Tomcat {Colors.CYAN}[Sem deploy]{Colors.END}")
        print(f"{Colors.BLUE}4. Deploy em WildFly {Colors.CYAN}[Porta: {WILDFLY_PORT}]{Colors.END}")
        print(f"{Colors.BLUE}5. Iniciar WildFly {Colors.CYAN}[Sem deploy]{Colors.END}")
        print(f"{Colors.BLUE}6. Undeploy {Colors.CYAN}(Limpar deployments, Tomcat e WildFly){Colors.END}")
        print(f"{Colors.BLUE}7. Diagnóstico do Tomcat {Colors.CYAN}(Verificar configuração e ambiente){Colors.END}")
        print(f"{Colors.BLUE}8. Diagnóstico do WildFly {Colors.CYAN}(Verificar configuração e ambiente){Colors.END}")
        print(f"{Colors.BLUE}9. Aplicar porta 9090 no Tomcat e reiniciar{Colors.END}")
        print(f"{Colors.BLUE}10. Configurar datasource PostgreSQL no WildFly{Colors.END}")
        print(f"{Colors.BLUE}11. Configurar datasource PostgreSQL no Tomcat{Colors.END}")
        print(f"{Colors.BLUE}0. Sair{Colors.END}")
        
        option = input(f"\n{Colors.CYAN}Escolha uma opção: {Colors.END}").strip()
        
        if option == "1":
            log("Verificando ambiente...", "INFO")
            check_environment()
            input(f"\n{Colors.WARNING}Pressione Enter para continuar...{Colors.END}")
        
        elif option == "2":
            log("Iniciando processo de deploy no Tomcat...", "INFO")
            
            # Verificar ambiente básico antes de prosseguir
            log("Verificando requisitos mínimos...", "INFO")
            java_installed, _ = check_java_installed()
            maven_installed, _, maven_cmd = check_maven_installed()
            
            if not java_installed or not maven_installed:
                log("Requisitos mínimos não atendidos. Verifique o ambiente primeiro (opção 1).", "ERROR")
                input(f"\n{Colors.WARNING}Pressione Enter para continuar...{Colors.END}")
                continue
            
            MAVEN_CMD = maven_cmd
            
            # Verificar se o Tomcat está em execução
            tomcat_running = check_server_running(TOMCAT_PORT)
            if tomcat_running:
                log(f"Servidor Tomcat está em execução na porta {TOMCAT_PORT}", "WARNING")
                log("O Tomcat não permite deploy a quente. O servidor será parado, feito undeploy e reiniciado.", "WARNING")
                # Proceder automaticamente com o deploy sem solicitar confirmação
                should_continue = "S"
                log("Continuando automaticamente com o deploy...", "INFO")
            else:
                log(f"Servidor Tomcat não está em execução na porta {TOMCAT_PORT}", "INFO")
                log("O servidor será iniciado após o deploy.", "INFO")
            
            # Compilar e empacotar o projeto
            log("Compilando e empacotando o projeto para Tomcat...", "INFO")
            mvn_result = execute_maven_command("clean package", "tomcat", "-DskipTests")
            
            if not mvn_result["success"]:
                log("Falha na compilação do projeto. Verifique os erros acima.", "ERROR")
                input(f"\n{Colors.WARNING}Pressione Enter para continuar...{Colors.END}")
                continue
            
            # Verificar se o WAR foi gerado
            war_files = []
            target_dir = os.path.join(PROJECT_DIR, "target")
            if os.path.exists(target_dir):
                war_files = [f for f in os.listdir(target_dir) if f.endswith(".war")]
            
            if not war_files:
                log("Nenhum arquivo WAR encontrado em target/. Procurando em outros diretórios...", "WARNING")
                
                # Verificar em diretórios alternativos (por exemplo, diretório atual ou diretório raiz do projeto)
                if os.path.exists(PROJECT_DIR):
                    alt_war_files = [f for f in os.listdir(PROJECT_DIR) if f.endswith(".war")]
                    if alt_war_files:
                        war_files = alt_war_files
                        target_dir = PROJECT_DIR
                        log(f"Arquivo WAR encontrado no diretório do projeto: {os.path.join(target_dir, war_files[0])}", "INFO")
                
                if not war_files:
                    log("Nenhum arquivo WAR encontrado. Falha no processo de deploy para Tomcat.", "ERROR")
                    input(f"\n{Colors.WARNING}Pressione Enter para continuar...{Colors.END}")
                    continue
                    
            war_file = os.path.join(target_dir, war_files[0])
            log(f"Arquivo WAR encontrado: {war_file}", "SUCCESS")
            
            # Deploy no Tomcat
            if os.path.exists(TOMCAT_DIR):
                # Configurar a porta do Tomcat antes de iniciar
                log(f"Configurando Tomcat para usar a porta {TOMCAT_PORT}...", "INFO")
                configure_tomcat_port(TOMCAT_DIR, TOMCAT_PORT)

                # Garantir datasource PostgreSQL no Tomcat
                log("Verificando/Configurando datasource PostgreSQL no Tomcat...", "INFO")
                if configure_tomcat_postgres_datasource():
                    log("Datasource PostgreSQL pronto no Tomcat.", "SUCCESS")
                else:
                    log("Não foi possível garantir o datasource PostgreSQL no Tomcat.", "WARNING")
                
                tomcat_webapps = os.path.join(TOMCAT_DIR, "webapps")
                if not os.path.exists(tomcat_webapps):
                    os.makedirs(tomcat_webapps)
                
                # Verificar se o Tomcat está em execução para fazer undeploy
                tomcat_running = check_server_running(TOMCAT_PORT)
                if tomcat_running:
                    log("Tomcat está em execução. Realizando undeploy...", "INFO")
                    
                    # Parar o Tomcat para undeploy
                    print(f"\n{Colors.YELLOW}Parando o servidor Tomcat para undeploy, aguarde...{Colors.END}")
                    if platform.system() == "Windows":
                        try:
                            subprocess.run([os.path.join(TOMCAT_DIR, "bin", "shutdown.bat")], shell=True)
                            log("Comando de parada do Tomcat executado com sucesso", "SUCCESS")
                            print(f"{Colors.YELLOW}Aguardando o servidor parar (10 segundos)...{Colors.END}")
                            time.sleep(10)  # Aguardar o Tomcat parar
                        except Exception as e:
                            log(f"Erro ao parar o Tomcat: {str(e)}", "ERROR")
                    else:
                        try:
                            subprocess.run([os.path.join(TOMCAT_DIR, "bin", "shutdown.sh")], shell=True)
                            log("Comando de parada do Tomcat executado com sucesso", "SUCCESS")
                            print(f"{Colors.YELLOW}Aguardando o servidor parar (10 segundos)...{Colors.END}")
                            time.sleep(10)  # Aguardar o Tomcat parar
                        except Exception as e:
                            log(f"Erro ao parar o Tomcat: {str(e)}", "ERROR")
                else:
                    log("Tomcat não está em execução. Prosseguindo com a limpeza do diretório webapps...", "INFO")
                
                # Limpar deployments anteriores (mesmo se o servidor não estiver rodando)
                log("Limpando deployments anteriores...", "INFO")
                for item in ["meu-projeto-java", "meu-projeto-java.war", "ROOT", "ROOT.war"]:
                    item_path = os.path.join(tomcat_webapps, item)
                    if os.path.exists(item_path):
                        if os.path.isdir(item_path):
                            shutil.rmtree(item_path, ignore_errors=True)
                            log(f"Diretório {item} removido do Tomcat", "INFO")
                        else:
                            os.remove(item_path)
                            log(f"Arquivo {item} removido do Tomcat", "INFO")
                            # Iniciar novamente em foreground (console neste terminal)
                            bin_dir = os.path.join(TOMCAT_DIR, "bin")
                            try:
                                if platform.system() == "Windows":
                                    catalina = os.path.join(bin_dir, "catalina.bat")
                                    if not os.path.exists(catalina):
                                        log("catalina.bat não encontrado no Tomcat.", "ERROR")
                                        return False
                                    cmd = f'"{catalina}" run'
                                    process = subprocess.Popen(cmd, shell=True, cwd=bin_dir)
                                else:
                                    catalina = os.path.join(bin_dir, "catalina.sh")
                                    if not os.path.exists(catalina):
                                        log("catalina.sh não encontrado no Tomcat.", "ERROR")
                                        return False
                                    process = subprocess.Popen([catalina, "run"], cwd=bin_dir)
                                print(f"{Colors.CYAN}Tomcat em execução no foreground. Pressione Ctrl+C para parar.{Colors.END}")
                                print(f"{Colors.GREEN}Aplicação: http://localhost:{TOMCAT_PORT}/meu-projeto-java/{Colors.END}")
                                process.wait()
                                log("Tomcat finalizado.", "INFO")
                            except KeyboardInterrupt:
                                log("Interrompido pelo usuário. Enviando shutdown ao Tomcat...", "INFO")
                                try:
                                    if platform.system() == "Windows":
                                        subprocess.run([os.path.join(bin_dir, "shutdown.bat")], shell=True, cwd=bin_dir)
                                    else:
                                        subprocess.run([os.path.join(bin_dir, "shutdown.sh")], cwd=bin_dir)
                                except Exception:
                                    pass
                
                # Copiar o WAR para o Tomcat
                war_dest = os.path.join(tomcat_webapps, "meu-projeto-java.war")
                shutil.copy2(war_file, war_dest)
                log(f"Arquivo WAR copiado para Tomcat: {war_dest}", "SUCCESS")
                
                # Iniciar o Tomcat no foreground (console neste terminal)
                log("Iniciando o Tomcat em foreground (console neste terminal)...", "INFO")
                log(f"Configurado para usar a porta {TOMCAT_PORT}.", "INFO")
                bin_dir = os.path.join(TOMCAT_DIR, "bin")
                try:
                    if platform.system() == "Windows":
                        cmd = f'"{os.path.join(bin_dir, "catalina.bat")}" run'
                        tomcat_process = subprocess.Popen(cmd, shell=True, cwd=bin_dir)
                    else:
                        cmd = [os.path.join(bin_dir, "catalina.sh"), "run"]
                        tomcat_process = subprocess.Popen(cmd, cwd=bin_dir)
                    print(f"{Colors.CYAN}Tomcat em execução no foreground. Pressione Ctrl+C para parar.{Colors.END}")
                    print(f"{Colors.GREEN}Aplicação: http://localhost:{TOMCAT_PORT}/meu-projeto-java/{Colors.END}")
                    tomcat_process.wait()
                    log("Tomcat finalizado.", "INFO")
                except KeyboardInterrupt:
                    log("Interrompido pelo usuário. Enviando shutdown ao Tomcat...", "INFO")
                    try:
                        if platform.system() == "Windows":
                            subprocess.run([os.path.join(bin_dir, "shutdown.bat")], shell=True, cwd=bin_dir)
                        else:
                            subprocess.run([os.path.join(bin_dir, "shutdown.sh")], cwd=bin_dir)
                    except Exception:
                        pass
                else:
                    try:
                        tomcat_process = subprocess.Popen([os.path.join(TOMCAT_DIR, "bin", "startup.sh")], shell=True, env=tomcat_env)
                        log("Comando de inicialização do Tomcat executado com sucesso", "SUCCESS")
                        print(f"{Colors.YELLOW}Aguardando o servidor inicializar (20 segundos)...{Colors.END}")
                        time.sleep(20)  # Aumentando o tempo de espera para o Tomcat iniciar completamente
                    except Exception as e:
                        log(f"Erro ao iniciar o Tomcat: {str(e)}", "ERROR")
                
                # Verificar novamente se o servidor está respondendo
                    try:
                        s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
                        s.settimeout(1)
                        result = s.connect_ex(('localhost', TOMCAT_PORT))
                        s.close()
                        if result == 0:
                            print(f"{Colors.GREEN}Servidor Tomcat iniciado com sucesso na porta {TOMCAT_PORT}!{Colors.END}")
                            log("Verificação confirmou que o Tomcat está em execução na porta configurada", "SUCCESS")
                        else:
                            print(f"{Colors.WARNING}O servidor Tomcat pode não ter iniciado na porta {TOMCAT_PORT}. Verificando porta padrão 8080...{Colors.END}")
                            log("Verificação não conseguiu confirmar se o Tomcat está respondendo na porta configurada", "WARNING")
                            
                            # Verificar se o Tomcat está rodando na porta padrão 8080
                            s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
                            s.settimeout(1)
                            result = s.connect_ex(('localhost', 8080))
                            s.close()
                            if result == 0:
                                print(f"{Colors.WARNING}Tomcat está rodando na porta padrão 8080 em vez de {TOMCAT_PORT}!{Colors.END}")
                                log("Tomcat detectado na porta 8080. A configuração de porta não funcionou.", "WARNING")
                                
                                # Exibir informações de diagnóstico
                                print(f"\n{Colors.YELLOW}=== Informações de Diagnóstico do Tomcat ==={Colors.END}")
                                print(f"{Colors.YELLOW}CATALINA_HOME: {tomcat_env.get('CATALINA_HOME', 'Não definido')}{Colors.END}")
                                print(f"{Colors.YELLOW}CATALINA_BASE: {tomcat_env.get('CATALINA_BASE', 'Não definido')}{Colors.END}")
                                print(f"{Colors.YELLOW}CATALINA_OPTS: {tomcat_env.get('CATALINA_OPTS', 'Não definido')}{Colors.END}")
                                print(f"{Colors.YELLOW}JAVA_HOME: {tomcat_env.get('JAVA_HOME', 'Não definido')}{Colors.END}")
                                
                                # Sugerir soluções
                                print(f"\n{Colors.YELLOW}Sugestões para corrigir o problema:{Colors.END}")
                                print(f"{Colors.YELLOW}1. Verifique se o servidor na porta 8080 é realmente o Tomcat{Colors.END}")
                                print(f"{Colors.YELLOW}2. Tente parar qualquer serviço rodando na porta 8080{Colors.END}")
                                print(f"{Colors.YELLOW}3. Verifique se o arquivo server.xml tem a porta definida como {TOMCAT_PORT}{Colors.END}")
                                
                                # Exibir informações de diagnóstico
                                print(f"\n{Colors.YELLOW}=== Informações de Diagnóstico do Tomcat ==={Colors.END}")
                                print(f"{Colors.YELLOW}CATALINA_HOME: {tomcat_env.get('CATALINA_HOME', 'Não definido')}{Colors.END}")
                                print(f"{Colors.YELLOW}CATALINA_BASE: {tomcat_env.get('CATALINA_BASE', 'Não definido')}{Colors.END}")
                                print(f"{Colors.YELLOW}CATALINA_OPTS: {tomcat_env.get('CATALINA_OPTS', 'Não definido')}{Colors.END}")
                                print(f"{Colors.YELLOW}JAVA_HOME: {tomcat_env.get('JAVA_HOME', 'Não definido')}{Colors.END}")
                                
                                # Sugerir soluções
                                print(f"\n{Colors.YELLOW}Sugestões para corrigir o problema:{Colors.END}")
                                print(f"{Colors.YELLOW}1. Verifique se o servidor na porta 8080 é realmente o Tomcat{Colors.END}")
                                print(f"{Colors.YELLOW}2. Tente parar qualquer serviço rodando na porta 8080{Colors.END}")
                                print(f"{Colors.YELLOW}3. Verifique se o arquivo server.xml tem a porta definida como {TOMCAT_PORT}{Colors.END}")
                    except:
                        log("Não foi possível verificar o status final do Tomcat.", "WARNING")
                
                # Exibir link da aplicação
                print(f"\n{Colors.GREEN}Aplicação disponível em: http://localhost:{TOMCAT_PORT}/meu-projeto-java/{Colors.END}")
                log(f"Aplicação disponível em: http://localhost:{TOMCAT_PORT}/meu-projeto-java/", "SUCCESS")
            else:
                log(f"Tomcat não encontrado em: {TOMCAT_DIR}", "ERROR")
                log("Instale o Tomcat ou corrija o caminho no script.", "ERROR")
            
            # Exibir mensagem de sucesso e URL da aplicação
            print(f"\n{Colors.GREEN}{'=' * 60}{Colors.END}")
            print(f"{Colors.GREEN}DEPLOY NO TOMCAT CONCLUÍDO COM SUCESSO!{Colors.END}")
            print(f"{Colors.GREEN}Processo realizado: Undeploy -> Deploy -> Reinicialização{Colors.END}")
            print(f"{Colors.GREEN}Acesse a aplicação em: http://localhost:{TOMCAT_PORT}/meu-projeto-java/{Colors.END}")
            print(f"{Colors.GREEN}{'=' * 60}{Colors.END}")
            
            input(f"\n{Colors.WARNING}Pressione Enter para continuar...{Colors.END}")
        
        elif option == "3":
            log("Iniciando servidor Tomcat (sem deploy)...", "INFO")
            
            # Verificar se o Tomcat está em execução
            tomcat_running = check_server_running(TOMCAT_PORT)
            if tomcat_running:
                log(f"Servidor Tomcat já está em execução na porta {TOMCAT_PORT}", "SUCCESS")
                log(f"Aplicação disponível em: http://localhost:{TOMCAT_PORT}/meu-projeto-java/", "SUCCESS")
                input(f"\n{Colors.WARNING}Pressione Enter para continuar...{Colors.END}")
                continue
            
            # Verificar se o Tomcat existe
            if os.path.exists(TOMCAT_DIR):
                # Configurar a porta do Tomcat antes de iniciar
                log(f"Configurando Tomcat para usar a porta {TOMCAT_PORT}...", "INFO")
                configure_tomcat_port(TOMCAT_DIR, TOMCAT_PORT)
                
                # Configurar variáveis de ambiente para o Tomcat
                tomcat_env = setup_tomcat_environment(TOMCAT_DIR)
                tomcat_env["CATALINA_OPTS"] = f"-Dport.http.nonssl={TOMCAT_PORT}"
                
                tomcat_bin = os.path.join(TOMCAT_DIR, "bin")
                if platform.system() == "Windows":
                    try:
                        startup_bat = os.path.join(tomcat_bin, "startup.bat")
                        subprocess.Popen([startup_bat], shell=True, env=tomcat_env)
                        log("Comando de inicialização do Tomcat executado com sucesso", "SUCCESS")
                        print(f"{Colors.YELLOW}Aguardando o servidor inicializar (10 segundos)...{Colors.END}")
                        time.sleep(10)  # Aguardar o Tomcat iniciar completamente
                        
                        # Verificar se o Tomcat iniciou com sucesso
                        tomcat_started = check_server_running(TOMCAT_PORT)
                        if tomcat_started:
                            log("Tomcat iniciado com sucesso", "SUCCESS")
                            log(f"Aplicação disponível em: http://localhost:{TOMCAT_PORT}/meu-projeto-java/ (se existir)", "SUCCESS")
                        else:
                            log("Tomcat pode não ter iniciado corretamente", "WARNING")
                            diagnose_tomcat_issues(tomcat_env)
                    except Exception as e:
                        log(f"Erro ao iniciar o Tomcat: {str(e)}", "ERROR")
                else:
                    try:
                        startup_sh = os.path.join(tomcat_bin, "startup.sh")
                        os.chmod(startup_sh, 0o755)  # Garantir que o script tenha permissão de execução
                        subprocess.Popen([startup_sh], shell=True, env=tomcat_env)
                        log("Comando de inicialização do Tomcat executado com sucesso", "SUCCESS")
                        print(f"{Colors.YELLOW}Aguardando o servidor inicializar (10 segundos)...{Colors.END}")
                        time.sleep(10)  # Aguardar o Tomcat iniciar completamente
                        
                        # Verificar se o Tomcat iniciou com sucesso
                        tomcat_started = check_server_running(TOMCAT_PORT)
                        if tomcat_started:
                            log("Tomcat iniciado com sucesso", "SUCCESS")
                            log(f"Aplicação disponível em: http://localhost:{TOMCAT_PORT}/meu-projeto-java/ (se existir)", "SUCCESS")
                        else:
                            log("Tomcat pode não ter iniciado corretamente", "WARNING")
                            diagnose_tomcat_issues(tomcat_env)
                    except Exception as e:
                        log(f"Erro ao iniciar o Tomcat: {str(e)}", "ERROR")
            else:
                log(f"Tomcat não encontrado em: {TOMCAT_DIR}", "ERROR")
                log("Instale o Tomcat ou corrija o caminho no script.", "ERROR")
            
            # Exibir mensagem de sucesso
            print(f"\n{Colors.GREEN}{'=' * 60}{Colors.END}")
            print(f"{Colors.GREEN}SERVIDOR TOMCAT INICIADO!{Colors.END}")
            print(f"{Colors.GREEN}Porta: {TOMCAT_PORT}{Colors.END}")
            print(f"{Colors.GREEN}{'=' * 60}{Colors.END}")
            
            input(f"\n{Colors.WARNING}Pressione Enter para continuar...{Colors.END}")
        
        elif option == "4":
            log("Iniciando processo de deploy no WildFly...", "INFO")
            
            # Verificar ambiente básico antes de prosseguir
            log("Verificando requisitos mínimos...", "INFO")
            java_installed, _ = check_java_installed()
            maven_installed, _, maven_cmd = check_maven_installed()
            
            if not java_installed or not maven_installed:
                log("Requisitos mínimos não atendidos. Verifique o ambiente primeiro (opção 1).", "ERROR")
                input(f"\n{Colors.WARNING}Pressione Enter para continuar...{Colors.END}")
                continue
            
            MAVEN_CMD = maven_cmd
            
            # Verificar se o WildFly está em execução
            wildfly_running = False
            try:
                # Tentar conectar ao WildFly Management (porta 9990, sem offset já que estamos usando a porta padrão)
                import socket
                # Porta de administração padrão do WildFly
                wildfly_management_port = 9990
                s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
                s.settimeout(1)
                result = s.connect_ex(('localhost', wildfly_management_port))
                s.close()
                wildfly_running = (result == 0)
                if wildfly_running:
                    log(f"WildFly está em execução na porta de administração {wildfly_management_port}.", "SUCCESS")
                    log("O WildFly suporta deploy a quente. O servidor não precisa ser reiniciado.", "SUCCESS")
                else:
                    log(f"WildFly não está em execução na porta de administração {wildfly_management_port}. Iniciando automaticamente...", "INFO")
                    log("Iniciando o WildFly para o deploy...", "INFO")
                    
                    # Iniciar o WildFly
                    if os.path.exists(WILDFLY_DIR):
                        # Configurar variáveis de ambiente para o WildFly
                        wildfly_env = setup_wildfly_environment()
                        
                        wildfly_bin = os.path.join(WILDFLY_DIR, "bin")
                        if platform.system() == "Windows":
                            try:
                                standalone_bat = os.path.join(wildfly_bin, "standalone.bat")
                                wildfly_process = subprocess.Popen([standalone_bat], shell=True, env=wildfly_env)
                                log("Comando de inicialização do WildFly executado com sucesso", "SUCCESS")
                                print(f"{Colors.YELLOW}Aguardando o servidor inicializar (30 segundos)...{Colors.END}")
                                time.sleep(30)  # Aguardar o WildFly iniciar completamente
                                
                                # Verificar se o WildFly iniciou com sucesso
                                s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
                                s.settimeout(1)
                                result = s.connect_ex(('localhost', wildfly_management_port))
                                s.close()
                                if result == 0:
                                    wildfly_running = True
                                    log("WildFly iniciado com sucesso", "SUCCESS")
                                else:
                                    log("WildFly pode não ter iniciado corretamente", "WARNING")
                                    diagnose_wildfly_issues(wildfly_env)
                            except Exception as e:
                                log(f"Erro ao iniciar o WildFly: {str(e)}", "ERROR")
                        else:
                            try:
                                standalone_sh = os.path.join(wildfly_bin, "standalone.sh")
                                os.chmod(standalone_sh, 0o755)  # Garantir que o script tenha permissão de execução
                                wildfly_process = subprocess.Popen([standalone_sh], shell=True, env=wildfly_env)
                                log("Comando de inicialização do WildFly executado com sucesso", "SUCCESS")
                                print(f"{Colors.YELLOW}Aguardando o servidor inicializar (30 segundos)...{Colors.END}")
                                time.sleep(30)  # Aguardar o WildFly iniciar completamente
                                
                                # Verificar se o WildFly iniciou com sucesso
                                s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
                                s.settimeout(1)
                                result = s.connect_ex(('localhost', wildfly_management_port))
                                s.close()
                                if result == 0:
                                    wildfly_running = True
                                    log("WildFly iniciado com sucesso", "SUCCESS")
                                else:
                                    log("WildFly pode não ter iniciado corretamente", "WARNING")
                                    diagnose_wildfly_issues(wildfly_env)
                            except Exception as e:
                                log(f"Erro ao iniciar o WildFly: {str(e)}", "ERROR")
                    else:
                        log(f"WildFly não encontrado em: {WILDFLY_DIR}", "ERROR")
                        log("Instale o WildFly ou corrija o caminho no script.", "ERROR")
                        input(f"\n{Colors.WARNING}Pressione Enter para continuar...{Colors.END}")
                        continue
            except:
                log("Não foi possível verificar o status do WildFly. Assumindo que não está em execução.", "WARNING")
            
            # Compilar e empacotar o projeto para WildFly
            # Se o WildFly foi iniciado ou já estava em execução, usamos o perfil completo
            if wildfly_running:
                log("Usando perfil WildFly para compilação e deploy...", "INFO")
                mvn_result = execute_maven_command("clean package", "wildfly", "-DskipTests")
            else:
                log("WildFly não está em execução. Usando compilação sem deploy...", "WARNING")
                # Usar propriedade para pular a fase de undeploy/deploy
                mvn_result = execute_maven_command("clean package", "", "-DskipTests")
            
            if not mvn_result["success"]:
                log("Falha na compilação do projeto. Tentando abordagem alternativa...", "WARNING")
                # Tentativa alternativa: compilar apenas o WAR sem usar perfil específico
                mvn_result = execute_maven_command("clean package", "", "-DskipTests")
                if not mvn_result["success"]:
                    log("Todas as tentativas de compilação falharam. Verifique os erros acima.", "ERROR")
                    input(f"\n{Colors.WARNING}Pressione Enter para continuar...{Colors.END}")
                    continue
                else:
                    log("Compilação bem-sucedida com abordagem alternativa.", "SUCCESS")
            
            # Verificar se o WAR foi gerado
            war_files = []
            target_dir = os.path.join(PROJECT_DIR, "target")
            if os.path.exists(target_dir):
                war_files = [f for f in os.listdir(target_dir) if f.endswith(".war")]
            
            if not war_files:
                log("Nenhum arquivo WAR encontrado em target/. Procurando em outros diretórios...", "WARNING")
                
                # Verificar em diretórios alternativos (por exemplo, diretório atual ou diretório raiz do projeto)
                if os.path.exists(PROJECT_DIR):
                    alt_war_files = [f for f in os.listdir(PROJECT_DIR) if f.endswith(".war")]
                    if alt_war_files:
                        war_files = alt_war_files
                        target_dir = PROJECT_DIR
                        log(f"Arquivo WAR encontrado no diretório do projeto: {os.path.join(target_dir, war_files[0])}", "INFO")
                
                if not war_files:
                    log("Nenhum arquivo WAR encontrado. Falha no processo de deploy para WildFly.", "ERROR")
                    input(f"\n{Colors.WARNING}Pressione Enter para continuar...{Colors.END}")
                    continue
                    
            war_file = os.path.join(target_dir, war_files[0])
            log(f"Arquivo WAR encontrado: {war_file}", "SUCCESS")
            
            # Deploy no WildFly
            if os.path.exists(WILDFLY_DIR):
                # Garantir datasource PostgreSQL configurado
                log("Verificando/Configurando datasource PostgreSQL no WildFly...", "INFO")
                if configure_wildfly_postgres_datasource():
                    log("Datasource PostgreSQL pronto no WildFly.", "SUCCESS")
                else:
                    log("Não foi possível garantir o datasource PostgreSQL. O app pode falhar ao conectar.", "WARNING")
                deployments_dir = os.path.join(WILDFLY_DIR, "standalone", "deployments")
                if not os.path.exists(deployments_dir):
                    os.makedirs(deployments_dir)
                
                # Limpar deployments anteriores
                for pattern in ["ROOT.war*", "meu-projeto-java.war*"]:
                    for item_path in glob.glob(os.path.join(deployments_dir, pattern)):
                        if os.path.exists(item_path):
                            os.remove(item_path)
                            log(f"Arquivo {os.path.basename(item_path)} removido do WildFly", "INFO")
                
                # Copiar o WAR para o WildFly
                war_dest = os.path.join(deployments_dir, "meu-projeto-java.war")
                shutil.copy2(war_file, war_dest)
                log(f"Arquivo WAR copiado para WildFly: {war_dest}", "SUCCESS")
                
                # Criar arquivo .dodeploy
                with open(os.path.join(deployments_dir, "meu-projeto-java.war.dodeploy"), 'w') as f:
                    pass
                log("Arquivo marcador .dodeploy criado para o WildFly", "INFO")
                
                # Verificar se o WildFly está em execução
                import socket
                wildfly_running = False
                try:
                    s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
                    s.settimeout(1)
                    result = s.connect_ex(('localhost', WILDFLY_PORT))
                    s.close()
                    wildfly_running = (result == 0)
                    if wildfly_running:
                        log("WildFly está em execução. Aplicação será implantada com deploy a quente.", "SUCCESS")
                except:
                    log("Não foi possível verificar o status do WildFly.", "WARNING")
                
                # Se o WildFly não estiver em execução, iniciar automaticamente
                if not wildfly_running:
                    log("WildFly não está em execução. Iniciando o servidor automaticamente...", "INFO")
                    print(f"\n{Colors.YELLOW}Iniciando o servidor WildFly, aguarde...{Colors.END}")
                    
                    # Configurar variáveis de ambiente para o WildFly
                    wildfly_env = setup_wildfly_environment()
                    
                    if platform.system() == "Windows":
                        try:
                            wildfly_process = subprocess.Popen([os.path.join(WILDFLY_DIR, "bin", "standalone.bat")], shell=True, env=wildfly_env)
                            log("Comando de inicialização do WildFly executado com sucesso", "SUCCESS")
                            print(f"{Colors.YELLOW}Aguardando o servidor inicializar (30 segundos)...{Colors.END}")
                            time.sleep(30)  # Aumentando o tempo de espera para o WildFly iniciar completamente
                        except Exception as e:
                            log(f"Erro ao iniciar o WildFly: {str(e)}", "ERROR")
                    else:
                        try:
                            wildfly_process = subprocess.Popen([os.path.join(WILDFLY_DIR, "bin", "standalone.sh")], shell=True, env=wildfly_env)
                            log("Comando de inicialização do WildFly executado com sucesso", "SUCCESS")
                            print(f"{Colors.YELLOW}Aguardando o servidor inicializar (30 segundos)...{Colors.END}")
                            time.sleep(30)  # Aumentando o tempo de espera para o WildFly iniciar completamente
                        except Exception as e:
                            log(f"Erro ao iniciar o WildFly: {str(e)}", "ERROR")
                    
                    # Verificar novamente se o servidor está respondendo
                    try:
                        s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
                        s.settimeout(1)
                        result = s.connect_ex(('localhost', WILDFLY_PORT))
                        s.close()
                        if result == 0:
                            print(f"{Colors.GREEN}Servidor WildFly iniciado com sucesso!{Colors.END}")
                            log("Verificação confirmou que o WildFly está em execução", "SUCCESS")
                            wildfly_running = True
                        else:
                            print(f"{Colors.WARNING}O servidor WildFly pode estar iniciando. Aguarde alguns segundos para acessar a aplicação.{Colors.END}")
                            log("Verificação não conseguiu confirmar se o WildFly está respondendo, mas o processo foi iniciado", "WARNING")
                            diagnose_wildfly_issues(wildfly_env)
                    except:
                        log("Não foi possível verificar o status final do WildFly.", "WARNING")
                
                # Exibir link da aplicação
                print(f"\n{Colors.GREEN}Aplicação disponível em: http://localhost:{WILDFLY_PORT}/meu-projeto-java/{Colors.END}")
                log(f"Aplicação disponível em: http://localhost:{WILDFLY_PORT}/meu-projeto-java/", "SUCCESS")
            else:
                log(f"WildFly não encontrado em: {WILDFLY_DIR}", "ERROR")
                log("Instale o WildFly ou corrija o caminho no script.", "ERROR")
            
            # Exibir mensagem de sucesso e URL da aplicação
            print(f"\n{Colors.GREEN}{'=' * 60}{Colors.END}")
            print(f"{Colors.GREEN}DEPLOY NO WILDFLY CONCLUÍDO COM SUCESSO!{Colors.END}")
            if wildfly_running:
                print(f"{Colors.GREEN}Processo realizado: Deploy a quente sem reinicialização{Colors.END}")
            else:
                print(f"{Colors.GREEN}Processo realizado: Inicialização do servidor + Deploy{Colors.END}")
            print(f"{Colors.GREEN}Acesse a aplicação em: http://localhost:{WILDFLY_PORT}/meu-projeto-java/{Colors.END}")
            print(f"{Colors.GREEN}{'=' * 60}{Colors.END}")
            
            input(f"\n{Colors.WARNING}Pressione Enter para continuar...{Colors.END}")
        
        elif option == "5":
            log("Iniciando servidor WildFly (sem deploy)...", "INFO")
            
            # Verificar ambiente básico antes de prosseguir
            log("Verificando requisitos mínimos...", "INFO")
            java_installed, _ = check_java_installed()
            
            if not java_installed:
                log("Requisitos mínimos não atendidos. Verifique o ambiente primeiro (opção 1).", "ERROR")
                input(f"\n{Colors.WARNING}Pressione Enter para continuar...{Colors.END}")
                continue
            
            # Verificar se o WildFly está em execução
            wildfly_running = check_server_running(WILDFLY_PORT)
            if wildfly_running:
                log(f"Servidor WildFly já está em execução na porta {WILDFLY_PORT}", "SUCCESS")
                log(f"Aplicação disponível em: http://localhost:{WILDFLY_PORT}/meu-projeto-java/", "SUCCESS")
                input(f"\n{Colors.WARNING}Pressione Enter para continuar...{Colors.END}")
                continue
            
            # Verificar se o WildFly existe
            if os.path.exists(WILDFLY_DIR):
                # Configurar variáveis de ambiente para o WildFly
                wildfly_env = setup_wildfly_environment()
                
                log(f"Inicializando o WildFly. Isso pode levar alguns instantes...", "INFO")
                log(f"Configurado para usar a porta {WILDFLY_PORT}", "INFO")
                
                wildfly_bin = os.path.join(WILDFLY_DIR, "bin")
                if platform.system() == "Windows":
                    try:
                        standalone_bat = os.path.join(wildfly_bin, "standalone.bat")
                        wildfly_process = subprocess.Popen([standalone_bat], shell=True, env=wildfly_env)
                        log("Comando de inicialização do WildFly executado com sucesso", "SUCCESS")
                        print(f"{Colors.YELLOW}Aguardando o servidor inicializar (30 segundos)...{Colors.END}")
                        time.sleep(30)  # Aguardar o WildFly iniciar completamente
                        
                        # Verificar se o WildFly iniciou com sucesso
                        wildfly_started = check_server_running(WILDFLY_PORT)
                        if wildfly_started:
                            log("WildFly iniciado com sucesso", "SUCCESS")
                            log(f"Aplicação disponível em: http://localhost:{WILDFLY_PORT}/meu-projeto-java/ (se existir)", "SUCCESS")
                        else:
                            log("WildFly pode não ter iniciado completamente. Verificando problemas...", "WARNING")
                            diagnose_wildfly_issues(wildfly_env)
                    except Exception as e:
                        log(f"Erro ao iniciar o WildFly: {str(e)}", "ERROR")
                        diagnose_wildfly_issues(wildfly_env)
                else:
                    try:
                        standalone_sh = os.path.join(wildfly_bin, "standalone.sh")
                        os.chmod(standalone_sh, 0o755)  # Garantir que o script tenha permissão de execução
                        wildfly_process = subprocess.Popen([standalone_sh], shell=True, env=wildfly_env)
                        log("Comando de inicialização do WildFly executado com sucesso", "SUCCESS")
                        print(f"{Colors.YELLOW}Aguardando o servidor inicializar (30 segundos)...{Colors.END}")
                        time.sleep(30)  # Aguardar o WildFly iniciar completamente
                        
                        # Verificar se o WildFly iniciou com sucesso
                        wildfly_started = check_server_running(WILDFLY_PORT)
                        if wildfly_started:
                            log("WildFly iniciado com sucesso", "SUCCESS")
                            log(f"Aplicação disponível em: http://localhost:{WILDFLY_PORT}/meu-projeto-java/ (se existir)", "SUCCESS")
                        else:
                            log("WildFly pode não ter iniciado completamente. Verificando problemas...", "WARNING")
                            diagnose_wildfly_issues(wildfly_env)
                    except Exception as e:
                        log(f"Erro ao iniciar o WildFly: {str(e)}", "ERROR")
                        diagnose_wildfly_issues(wildfly_env)
            else:
                log(f"WildFly não encontrado em: {WILDFLY_DIR}", "ERROR")
                log("Instale o WildFly ou corrija o caminho no script.", "ERROR")
            
            # Exibir mensagem de sucesso
            print(f"\n{Colors.GREEN}{'=' * 60}{Colors.END}")
            print(f"{Colors.GREEN}SERVIDOR WILDFLY INICIADO!{Colors.END}")
            print(f"{Colors.GREEN}Porta: {WILDFLY_PORT}{Colors.END}")
            print(f"{Colors.GREEN}Console Admin: http://localhost:{WILDFLY_MANAGEMENT_PORT}/console{Colors.END}")
            print(f"{Colors.GREEN}{'=' * 60}{Colors.END}")
            
            input(f"\n{Colors.WARNING}Pressione Enter para continuar...{Colors.END}")
        
        elif option == "6":
            log("Iniciando processo de undeploy (limpeza de deployments)...", "INFO")
            
            # Limpar deployments do Tomcat
            if os.path.exists(TOMCAT_DIR):
                tomcat_webapps = os.path.join(TOMCAT_DIR, "webapps")
                if os.path.exists(tomcat_webapps):
                    log("Limpando deployments do Tomcat...", "INFO")
                    # Remover ROOT.war e diretório ROOT
                    for item in ["ROOT", "ROOT.war"]:
                        item_path = os.path.join(tomcat_webapps, item)
                        if os.path.exists(item_path):
                            if os.path.isdir(item_path):
                                shutil.rmtree(item_path, ignore_errors=True)
                                log(f"Diretório {item} removido do Tomcat", "SUCCESS")
                            else:
                                os.remove(item_path)
                                log(f"Arquivo {item} removido do Tomcat", "SUCCESS")
            
            # Limpar deployments do WildFly
            if os.path.exists(WILDFLY_DIR):
                wildfly_deployments = os.path.join(WILDFLY_DIR, "standalone", "deployments")
                if os.path.exists(wildfly_deployments):
                    log("Limpando deployments do WildFly...", "INFO")
                    # Remover arquivos relacionados a deployments
                    for item in ["ROOT.war", "ROOT.war.deployed", "ROOT.war.failed", "ROOT.war.dodeploy"]:
                        item_path = os.path.join(wildfly_deployments, item)
                        if os.path.exists(item_path):
                            os.remove(item_path)
                            log(f"Arquivo {item} removido do WildFly", "SUCCESS")
            
            # Limpar diretório target
            if os.path.exists(os.path.join(PROJECT_DIR, "target")):
                log("Limpando diretório target do projeto...", "INFO")
                shutil.rmtree(os.path.join(PROJECT_DIR, "target"), ignore_errors=True)
                log("Diretório target do projeto limpo com sucesso", "SUCCESS")
            
            log("Processo de undeploy concluído com sucesso", "SUCCESS")
            
            input(f"\n{Colors.WARNING}Pressione Enter para continuar...{Colors.END}")
        
        elif option == "7":
            log("Iniciando diagnóstico do Tomcat...", "INFO")
            check_tomcat_environment()
            input(f"\n{Colors.WARNING}Pressione Enter para continuar...{Colors.END}")
            
        elif option == "8":
            log("Iniciando diagnóstico do WildFly...", "INFO")
            check_wildfly_environment()
            input(f"\n{Colors.WARNING}Pressione Enter para continuar...{Colors.END}")
        
        elif option == "9":
            log(f"Aplicando porta {TOMCAT_PORT} no Tomcat e reiniciando...", "INFO")
            # Garantir que diretório existe
            if not os.path.exists(TOMCAT_DIR):
                log(f"Tomcat não encontrado em: {TOMCAT_DIR}", "ERROR")
                input(f"\n{Colors.WARNING}Pressione Enter para continuar...{Colors.END}")
                continue
            # Atualizar server.xml
            if configure_tomcat_port(TOMCAT_DIR, TOMCAT_PORT):
                log(f"Porta do Tomcat ajustada para {TOMCAT_PORT}.", "SUCCESS")
                # Reiniciar
                if restart_tomcat_server():
                    log(f"Tomcat reiniciado. Acesse http://localhost:{TOMCAT_PORT}/", "SUCCESS")
                else:
                    log("Falha ao reiniciar Tomcat automaticamente. Reinicie manualmente.", "ERROR")
            else:
                log("Falha ao atualizar server.xml.", "ERROR")
            input(f"\n{Colors.WARNING}Pressione Enter para continuar...{Colors.END}")

        elif option == "10":
            log("Configurando datasource PostgreSQL no WildFly...", "INFO")
            if not os.path.exists(WILDFLY_DIR):
                log(f"WildFly não encontrado em: {WILDFLY_DIR}", "ERROR")
                input(f"\n{Colors.WARNING}Pressione Enter para continuar...{Colors.END}")
                continue
            if configure_wildfly_postgres_datasource():
                log("Datasource PostgreSQL configurado com sucesso no WildFly.", "SUCCESS")
                # Reinício automático para aplicar alterações
                log("Reiniciando WildFly para aplicar alterações do datasource...", "INFO")
                stop_wildfly_server()
                time.sleep(2)
                if platform.system() == "Windows":
                    subprocess.Popen([os.path.join(WILDFLY_DIR, "bin", "standalone.bat")], shell=True)
                else:
                    subprocess.Popen([os.path.join(WILDFLY_DIR, "bin", "standalone.sh")], shell=True)
                log("WildFly reiniciado (inicialização em background).", "INFO")
            else:
                log("Falha ao configurar o datasource do WildFly.", "ERROR")
            input(f"\n{Colors.WARNING}Pressione Enter para continuar...{Colors.END}")

        elif option == "11":
            log("Configurando datasource PostgreSQL no Tomcat...", "INFO")
            if not os.path.exists(TOMCAT_DIR):
                log(f"Tomcat não encontrado em: {TOMCAT_DIR}", "ERROR")
                input(f"\n{Colors.WARNING}Pressione Enter para continuar...{Colors.END}")
                continue
            if configure_tomcat_postgres_datasource():
                log("Datasource PostgreSQL configurado com sucesso no Tomcat.", "SUCCESS")
                # Reinício obrigatório: o Tomcat não aplica context.xml a quente
                log("Reiniciando Tomcat para aplicar alterações do datasource...", "INFO")
                if restart_tomcat_server():
                    log("Tomcat reiniciado para aplicar datasource.", "SUCCESS")
                else:
                    log("Falha ao reiniciar o Tomcat.", "ERROR")
            else:
                log("Falha ao configurar o datasource do Tomcat.", "ERROR")
            input(f"\n{Colors.WARNING}Pressione Enter para continuar...{Colors.END}")
            
        elif option == "0":
            exit_menu = True
            log("Parando todos os servidores em execução...", "INFO")
            stop_all_servers()
            log("Encerrando script...", "INFO")
        
        else:
            log(f"Opção inválida: {option}. Escolha uma opção de 0 a 8.", "WARNING")
            input(f"\n{Colors.WARNING}Pressione Enter para continuar...{Colors.END}")

if __name__ == "__main__":
    main()