#!/usr/bin/env python
"""Wrapper interativo para executar o main.py com acoes focadas em Tomcat.

Uso:
    python main_tom.py           # abre menu interativo
    python main_tom.py 2         # executa a opcao 2 (deploy Tomcat)
"""

import contextlib
import json
import logging
import os
import platform
import shlex
import shutil
import socket
import ssl
import subprocess
import sys
import time
from dataclasses import dataclass
from datetime import datetime
from pathlib import Path
from typing import Callable, List, Optional, Sequence
from urllib.parse import urljoin
from urllib.request import Request, urlopen
from html.parser import HTMLParser

LOG_DIR_NAME = "logs"
LOGGER_NAME = "main_tom"

try:
    import requests  # type: ignore
except Exception:
    requests = None  # Fallback para urllib

HTTP_HEADERS = {
    'User-Agent': 'main_tom/1.0',
    'Accept': 'application/json, text/plain, */*'
}

_HTTP_SSL_CONTEXT = ssl._create_unverified_context() if hasattr(ssl, '_create_unverified_context') else None

CUSTOM_CHOICES = {"c", "custom", "personalizado"}
EXIT_CHOICES = {"0", "sair", "exit", "quit", "q"}

@dataclass(frozen=True)
class Action:
    key: str
    description: str
    args: Optional[List[str]]
    handler: Optional[Callable[[logging.Logger, str, str], int]] = None

def configure_logging(workspace: str) -> tuple[logging.Logger, str]:
    log_dir = os.path.join(workspace, LOG_DIR_NAME)
    os.makedirs(log_dir, exist_ok=True)

    timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
    log_path = os.path.join(log_dir, f"main_tom_{timestamp}.log")

    logger = logging.getLogger(LOGGER_NAME)
    logger.handlers.clear()
    logger.setLevel(logging.INFO)
    formatter = logging.Formatter("%(asctime)s [%(levelname)s] %(message)s")

    file_handler = logging.FileHandler(log_path, encoding="utf-8")
    file_handler.setFormatter(formatter)
    logger.addHandler(file_handler)

    stream_handler = logging.StreamHandler()
    stream_handler.setFormatter(formatter)
    logger.addHandler(stream_handler)

    logger.propagate = False
    logger.info("Logger inicializado (arquivo: %s)", log_path)
    return logger, log_path

def format_cmd(parts: Sequence[str]) -> str:
    if not parts:
        return ""
    try:
        quoted = [shlex.quote(part) for part in parts]
    except Exception:
        quoted = list(parts)
    return " ".join(quoted)

def prompt_custom_args(logger: logging.Logger) -> Optional[List[str]]:
    prompt = "Informe os argumentos para main.py (por exemplo, '2 --tomcat-dir C:/tomcat'): "
    while True:
        try:
            raw_value = input(prompt)
        except EOFError:
            logger.error("Entrada interativa indisponivel.")
            return None

        raw_value = raw_value.strip()
        if not raw_value:
            logger.info("Nenhum argumento informado; cancelando comando personalizado.")
            return None

        lowered = raw_value.lower()
        if lowered in EXIT_CHOICES:
            logger.info("Cancelando comando personalizado a pedido do usuario.")
            return None

        try:
            args = shlex.split(raw_value, posix=False)
        except ValueError as exc:
            logger.error("Nao foi possivel interpretar a entrada: %s", exc)
            continue

        logger.info("Argumentos personalizados informados: %s", raw_value)
        return args

def run_main_script(logger: logging.Logger, main_script: str, args: List[str]) -> int:
    cmd = [sys.executable, main_script, *args]
    logger.info("Executando main.py com argumentos: %s", format_cmd(args))

    try:
        result = subprocess.run(cmd, check=False)
    except OSError as error:
        logger.exception("Falha ao executar main.py: %s", error)
        return 1

    if result.returncode == 0:
        logger.info("Execucao concluida com sucesso.")
    else:
        logger.error("Execucao finalizada com codigo de saida %s.", result.returncode)
    return result.returncode

TOMCAT_PORT = 9090
PROJECT_RELATIVE_PATH = Path("caracore-hub")
TOMCAT_RELATIVE_PATH = Path("server") / "apache-tomcat-10.1.35"
WAR_NAME = "caracore-hub.war"
CLEAN_TARGETS = ("caracore-hub", "caracore-hub.war", "ROOT", "ROOT.war")

def find_maven_executable() -> Optional[str]:
    candidates = ["mvn.cmd", "mvn.bat", "mvn"] if platform.system().lower().startswith("win") else ["mvn"]
    for candidate in candidates:
        located = shutil.which(candidate)
        if located:
            return located
    return None

def build_project(logger: logging.Logger, project_dir: Path) -> Optional[Path]:
    maven_executable = find_maven_executable()
    if not maven_executable:
        logger.error("Maven nao encontrado no PATH.")
        return None

    cmd = [maven_executable, "-f", str(project_dir / "pom.xml"), "clean", "package", "-DskipTests"]
    logger.info("Executando build Maven: %s", format_cmd(cmd))
    if platform.system().lower().startswith("win"):
        exec_cmd = ["cmd.exe", "/c", *cmd]
        result = subprocess.run(exec_cmd, cwd=str(project_dir), check=False)
    else:
        result = subprocess.run(cmd, cwd=str(project_dir), check=False)
    if result.returncode != 0:
        logger.error("Build Maven retornou codigo %s.", result.returncode)
        return None

    target_dir = project_dir / "target"
    if not target_dir.exists():
        logger.error("Diretorio target nao encontrado em %s.", target_dir)
        return None

    war_candidates = sorted(target_dir.glob('*.war'))
    if not war_candidates:
        logger.error("Nenhum arquivo WAR encontrado em %s.", target_dir)
        return None

    war_path = None
    for candidate in war_candidates:
        if candidate.name == WAR_NAME:
            war_path = candidate
            break
    if war_path is None:
        war_path = war_candidates[0]

    logger.info("WAR gerado em: %s", war_path)
    return war_path

def stop_tomcat(logger: logging.Logger, tomcat_dir: Path) -> None:
    bin_dir = tomcat_dir / "bin"
    if not bin_dir.exists():
        logger.warning("Diretorio bin do Tomcat nao encontrado em %s.", bin_dir)
        return

    script = "shutdown.bat" if platform.system().lower().startswith("win") else "shutdown.sh"
    script_path = bin_dir / script
    if not script_path.exists():
        logger.info("Script de parada (%s) nao encontrado. Tomcat pode nao estar instalado.", script_path)
        return

    logger.info("Solicitando parada do Tomcat (%s).", script_path)
    try:
        if platform.system().lower().startswith("win"):
            exec_cmd = ["cmd.exe", "/c", str(script_path)]
            subprocess.run(exec_cmd, cwd=str(bin_dir), check=False)
        else:
            subprocess.run([str(script_path)], cwd=str(bin_dir), check=False)
        time.sleep(5)
    except OSError as exc:
        logger.warning("Falha ao executar script de parada do Tomcat: %s", exc)

def clean_previous_deployments(logger: logging.Logger, webapps_dir: Path) -> None:
    webapps_dir.mkdir(parents=True, exist_ok=True)
    for target in CLEAN_TARGETS:
        target_path = webapps_dir / target
        if target_path.exists():
            try:
                if target_path.is_dir():
                    shutil.rmtree(target_path)
                else:
                    target_path.unlink()
                logger.info("Removido deployment antigo: %s", target_path)
            except OSError as exc:
                logger.warning("Nao foi possivel remover %s: %s", target_path, exc)

def copy_war_to_tomcat(logger: logging.Logger, war_path: Path, webapps_dir: Path) -> Path:
    destination = webapps_dir / WAR_NAME
    shutil.copy2(war_path, destination)
    logger.info("WAR copiado para: %s", destination)
    return destination

def start_tomcat(logger: logging.Logger, tomcat_dir: Path) -> bool:
    bin_dir = tomcat_dir / "bin"
    if not bin_dir.exists():
        logger.error("Diretorio bin do Tomcat nao encontrado em %s.", bin_dir)
        return False

    script = "startup.bat" if platform.system().lower().startswith("win") else "startup.sh"
    script_path = bin_dir / script
    if not script_path.exists():
        logger.error("Script de inicio (%s) nao encontrado.", script_path)
        return False

    logger.info("Iniciando Tomcat via %s.", script_path)
    try:
        if platform.system().lower().startswith("win"):
            exec_cmd = ["cmd.exe", "/c", str(script_path)]
            result = subprocess.run(exec_cmd, cwd=str(bin_dir), check=False)
        else:
            result = subprocess.run([str(script_path)], cwd=str(bin_dir), check=False)
    except OSError as exc:
        logger.error("Falha ao iniciar Tomcat: %s", exc)
        return False

    if result.returncode != 0:
        logger.error("Script de inicio retornou codigo %s.", result.returncode)
        return False

    return True

def wait_for_port(port: int, timeout: int = 60) -> bool:
    deadline = time.time() + timeout
    while time.time() < deadline:
        try:
            with socket.create_connection(("localhost", port), timeout=2):
                return True
        except OSError:
            time.sleep(1)
    return False

def deploy_and_start_tomcat(logger: logging.Logger, main_script: str, workspace: str) -> int:
    workspace_path = Path(workspace)
    project_dir = workspace_path / PROJECT_RELATIVE_PATH
    tomcat_dir = workspace_path / TOMCAT_RELATIVE_PATH

    if not project_dir.exists():
        logger.error("Diretorio do projeto nao encontrado: %s", project_dir)
        return 1
    if not tomcat_dir.exists():
        logger.error("Diretorio do Tomcat nao encontrado: %s", tomcat_dir)
        return 1

    war_path = build_project(logger, project_dir)
    if war_path is None:
        return 1

    logger.info("Configurando datasource/JNDI via main.py (opcao 11).")
    ds_result = run_main_script(logger, main_script, ["11"])
    if ds_result != 0:
        logger.warning("Configuracao JNDI retornou codigo %s. Prosseguindo mesmo assim.", ds_result)

    stop_tomcat(logger, tomcat_dir)
    webapps_dir = tomcat_dir / "webapps"
    clean_previous_deployments(logger, webapps_dir)
    copy_war_to_tomcat(logger, war_path, webapps_dir)

    if not start_tomcat(logger, tomcat_dir):
        return 1

    if wait_for_port(TOMCAT_PORT, timeout=60):
        logger.info("Tomcat disponivel em http://localhost:%s/caracore-hub/", TOMCAT_PORT)
        return 0

    logger.warning("Nao foi possivel confirmar Tomcat escutando na porta %s apos o deploy.", TOMCAT_PORT)
    return 1


def _tail_file(path: Path, max_bytes: int = 64_000) -> str:
    try:
        with path.open('rb') as fh:
            fh.seek(0, os.SEEK_END)
            size = fh.tell()
            fh.seek(max(size - max_bytes, 0))
            data = fh.read()
        return data.decode('utf-8', errors='ignore')
    except OSError:
        return ""

def is_server_up(host: str, port: int, timeout: int = 2) -> bool:
    try:
        with socket.create_connection((host, port), timeout=timeout):
            return True
    except OSError:
        return False

def _http_fetch(url: str, timeout: int) -> tuple[int, str, str]:
    if requests is not None:
        resp = requests.get(url, timeout=timeout, headers=HTTP_HEADERS)
        return resp.status_code, resp.text or '', resp.headers.get('Content-Type', '')
    req = Request(url, headers=HTTP_HEADERS)
    kwargs = {}
    if _HTTP_SSL_CONTEXT is not None and url.lower().startswith('https://'):
        kwargs['context'] = _HTTP_SSL_CONTEXT
    with contextlib.closing(urlopen(req, timeout=timeout, **kwargs)) as resp:
        charset = resp.headers.get_content_charset() if hasattr(resp.headers, 'get_content_charset') else None
        if not charset:
            charset = 'utf-8'
        text_resp = resp.read().decode(charset, errors='ignore')
        return resp.getcode(), text_resp, resp.headers.get('Content-Type', '')

def validate_tomcat_jndi_local(tomcat_dir: Path, runtime_check: bool = True) -> tuple[bool, str]:
    context_xml = tomcat_dir / 'conf' / 'context.xml'
    if not context_xml.exists():
        return False, f"context.xml nao encontrado em {context_xml}"
    try:
        import xml.etree.ElementTree as ET
        tree = ET.parse(context_xml)
        root = tree.getroot()
    except Exception as exc:
        return False, f"Falha ao ler context.xml: {exc}"
    resource = None
    for el in root.iter():
        if el.tag.endswith('Resource') and el.get('name') in ('jdbc/PostgresDS', 'java:comp/env/jdbc/PostgresDS'):
            resource = el
            break
    if resource is None:
        return False, 'Resource jdbc/PostgresDS nao encontrado em conf/context.xml'
    typ = (resource.get('type') or '').lower()
    url = resource.get('url') or ''
    username = resource.get('username') or ''
    factory = resource.get('factory') or ''
    cfg_ok = ('javax.sql.datasource' in typ) and ('jdbc:postgresql' in url.lower()) and bool(username) and bool(factory)
    lib_dir = tomcat_dir / 'lib'
    has_pg_driver = False
    try:
        if lib_dir.is_dir():
            has_pg_driver = any(name.lower().startswith('postgresql-') and name.lower().endswith('.jar') for name in os.listdir(lib_dir))
    except OSError:
        has_pg_driver = False
    messages: list[str] = []
    messages.append('Resource em context.xml OK' if cfg_ok else 'Resource em context.xml incompleto')
    messages.append('Driver postgresql presente em lib/' if has_pg_driver else 'Driver postgresql NAO encontrado em lib/')
    ok = cfg_ok and has_pg_driver
    if runtime_check:
        if is_server_up('localhost', TOMCAT_PORT):
            logs_dir = tomcat_dir / 'logs'
            if logs_dir.is_dir():
                candidates = list(logs_dir.glob('catalina*.log'))
                latest = max(candidates, key=lambda p: p.stat().st_mtime, default=None)
                if latest:
                    tail_low = _tail_file(latest).lower()
                    err_tokens = [
                        'cannot create jdbc driver',
                        'cannot create poolableconnectionfactory',
                        'resource is not available',
                        'failed to register in jndi',
                        'nontransientconnectionexception',
                        'org.postgresql.util',
                        'name [jdbc/postgresds] is not bound',
                        'name [java:comp/env/jdbc/postgresds] is not bound',
                        'no suitable driver',
                        'org.postgresql.driver not found',
                        'fetal: password authentication failed',
                        'connection refused',
                    ]
                    ok_tokens = [
                        'registering jndi resource',
                        'registered jndi resource',
                        'initialized datasource',
                        'pgjdbc',
                        'bound to naming context',
                        'name [java:comp/env/jdbc/postgresds] is bound',
                        'name [jdbc/postgresds] is bound',
                        'org.apache.commons.dbcp2',
                    ]
                    if any(token in tail_low for token in err_tokens):
                        ok = False
                        messages.append('Erros detectados em catalina.log referentes ao datasource/JNDI')
                    elif any(token in tail_low for token in ok_tokens):
                        messages.append('Logs indicam datasource inicializado/bound')
        else:
            messages.append('Tomcat nao esta em execucao; validacao de runtime nao realizada')
            ok = False
    return ok, '; '.join(messages)

def validate_datasource_http(base_url: str, timeout: int = 8) -> tuple[bool, str]:
    try:
        candidates: list[str] = []
        env_url = os.environ.get('APP_JNDI_STATUS_URL', '').strip()
        if env_url:
            if env_url.lower().startswith(('http://', 'https://')):
                candidates.append(env_url)
            else:
                candidates.append(urljoin(base_url, env_url))
        candidates.extend([
            urljoin(base_url, 'status/jndi'),
            urljoin(base_url, 'health/jndi'),
            urljoin(base_url, 'actuator/health/db'),
            urljoin(base_url, 'health'),
            urljoin(base_url, 'status'),
        ])
        seen: set[str] = set()
        for url in candidates:
            if url in seen:
                continue
            seen.add(url)
            try:
                status, body, _ = _http_fetch(url, timeout)
            except Exception:
                continue
            if status >= 400:
                continue
            body = (body or '').strip()
            parsed = None
            try:
                parsed = json.loads(body)
            except Exception:
                parsed = None
            if isinstance(parsed, dict):
                dumped = json.dumps(parsed).lower()
                status_value = str(parsed.get('status', '')).upper()
                if status_value in ('UP', 'OK') or ('db' in dumped and ('up' in dumped or 'ok' in dumped)):
                    return True, f"HTTP OK em {url} (JSON)"
            low = body.lower()
            if ('ok' in low or 'up' in low) and any(token in low for token in ('postgres', 'postgresds', 'jndi', 'datasource')):
                return True, f"HTTP OK em {url} (texto)"
        return False, 'Nenhuma URL de status confirmou JNDI'
    except Exception as exc:
        return False, f"Falha na validacao HTTP de JNDI: {exc}"

def validate_tomcat_datasource(logger: logging.Logger, main_script: str, workspace: str) -> int:
    tomcat_dir = Path(workspace) / TOMCAT_RELATIVE_PATH
    if not tomcat_dir.exists():
        logger.error("Diretorio do Tomcat nao encontrado: %s", tomcat_dir)
        print("Diretorio do Tomcat nao encontrado.")
        return 1
    ok_static, msg_static = validate_tomcat_jndi_local(tomcat_dir, runtime_check=False)
    logger.info("Validacao JNDI (configuracao): %s", msg_static)
    print(f"Configuracao: {msg_static}")
    if not ok_static:
        logger.warning("Problemas encontrados na configuracao JNDI do Tomcat.")
    if not is_server_up('localhost', TOMCAT_PORT):
        warning = f"Tomcat nao esta respondendo na porta {TOMCAT_PORT}. Inicie o servidor antes de validar o datasource."
        logger.warning(warning)
        print(warning)
        return 1
    ok_runtime, msg_runtime = validate_tomcat_jndi_local(tomcat_dir, runtime_check=True)
    logger.info("Validacao JNDI (runtime/logs): %s", msg_runtime)
    print(f"Runtime/logs: {msg_runtime}")
    base_url = f"http://localhost:{TOMCAT_PORT}/caracore-hub/"
    ok_http, msg_http = validate_datasource_http(base_url)
    logger.info("Validacao HTTP do datasource: %s", msg_http)
    print(f"HTTP: {msg_http}")
    final_ok = ok_static and ok_runtime and ok_http
    if final_ok:
        logger.info("Datasource JNDI validado com sucesso.")
        print("Datasource validado com sucesso.")
        return 0
    logger.warning("Validacao do datasource encontrou problemas. Consulte o log para detalhes.")
    print("Validacao encontrou problemas. Consulte o log.")
    return 1

ACTIONS: List[Action] = [
    Action("1", "[1] Verificar ambiente (Java/Maven/Docker/BD)", ["1"]),
    Action("2", "[2] Deploy completo no Tomcat", None, handler=deploy_and_start_tomcat),
    Action("3", "[3] Iniciar Tomcat (sem deploy)", ["3"]),
    Action("4", "[4] Parar/Undeploy do Tomcat", ["6"]),
    Action("5", "[5] Diagnostico do Tomcat", ["7"]),
    Action("6", "[6] Aplicar porta 9090 no Tomcat e reiniciar", ["9"]),
    Action("7", "[7] Configurar datasource PostgreSQL no Tomcat", ["11"]),
    Action("8", "[8] Validar JNDI/Datasource no Tomcat", None, handler=validate_tomcat_datasource),
    Action("C", "[C] Executar comando personalizado do main.py", None),
    Action("0", "[0] Sair", None),
]

ACTION_MAP = {action.key.lower(): action for action in ACTIONS}

MENU_HEADER = "\n========= Gestao Tomcat ========="
MENU_FOOTER = "================================"

def display_menu() -> None:
    print(MENU_HEADER)
    for action in ACTIONS:
        print(action.description)
    print(MENU_FOOTER)

def interactive_menu(logger: logging.Logger, main_script: str, workspace: str) -> None:
    last_choice: Optional[str] = None

    while True:
        display_menu()
        try:
            choice_raw = input("Selecione uma opcao (Enter repete a ultima): ")
        except EOFError:
            logger.warning("Entrada interativa indisponivel; encerrando.")
            return

        choice = choice_raw.strip().lower()
        if not choice:
            if last_choice is None:
                print("Nenhuma opcao selecionada.")
                continue
            choice = last_choice
            print(f"Repetindo opcao '{choice.upper()}'.")

        if choice in EXIT_CHOICES:
            logger.info("Encerrando menu a pedido do usuario.")
            return

        action = ACTION_MAP.get(choice)
        if action is None:
            print("Opcao invalida. Escolha novamente.")
            continue

        last_choice = choice

        if action.handler:
            return_code = action.handler(logger, main_script, workspace)
        else:
            if action.args is None:
                if action.key.lower() in CUSTOM_CHOICES:
                    args = prompt_custom_args(logger)
                    if not args:
                        continue
                else:
                    logger.info("Encerrando menu.")
                    return
            else:
                args = action.args

            return_code = run_main_script(logger, main_script, args)

        if return_code != 0:
            print(f"main.py retornou codigo {return_code}. Consulte o log para detalhes.")
        else:
            print("Acao concluida com sucesso.")

def main() -> None:
    workspace = os.path.dirname(os.path.abspath(__file__))
    main_script = os.path.join(workspace, "main.py")

    logger, _ = configure_logging(workspace)

    if not os.path.exists(main_script):
        message = "main.py nao encontrado no diretorio corrente."
        logger.error(message)
        print(message, file=sys.stderr)
        sys.exit(1)

    args = sys.argv[1:]

    if args:
        logger.info("Argumentos recebidos via CLI: %s", format_cmd(args))
        action = ACTION_MAP.get(args[0].lower()) if args else None
        if action and action.handler:
            code = action.handler(logger, main_script, workspace)
        else:
            code = run_main_script(logger, main_script, args)
        sys.exit(code)

    logger.info("Iniciando menu interativo de gestao Tomcat.")
    interactive_menu(logger, main_script, workspace)

if __name__ == "__main__":
    main()
