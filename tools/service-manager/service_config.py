"""
Configuration, port scanning, and process tree termination for UMIS services.
"""

import os
import socket
import subprocess
from dataclasses import dataclass
from typing import List, Optional


@dataclass
class ServiceInfo:
    service_id: str
    display_name: str
    port: int
    working_dir: str
    command: List[str]
    browser_url: str
    log_color: str


def get_project_root() -> str:
    current_dir = os.path.dirname(os.path.abspath(__file__))
    return os.path.abspath(os.path.join(current_dir, "..", ".."))


def get_service_definitions() -> List[ServiceInfo]:
    root_dir = get_project_root()
    return [
        ServiceInfo(
            service_id="umis_backend",
            display_name="UMIS Backend",
            port=8080,
            working_dir=os.path.join(root_dir, "backend"),
            command=["powershell.exe", "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", ".\\run-backend.ps1"],
            browser_url="http://localhost:8080/actuator/health",
            log_color="#38bdf8",
        ),
        ServiceInfo(
            service_id="po_processor",
            display_name="PO Processor",
            port=8085,
            working_dir=os.path.join(root_dir, "PO-Processor"),
            command=["powershell.exe", "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", ".\\run.ps1"],
            browser_url="http://localhost:8085/actuator/health",
            log_color="#a855f7",
        ),
        ServiceInfo(
            service_id="react_frontend",
            display_name="React Frontend",
            port=3000,
            working_dir=os.path.join(root_dir, "frontend"),
            command=["cmd.exe", "/c", "npm run dev"],
            browser_url="http://localhost:3000/po",
            log_color="#4ade80",
        ),
        ServiceInfo(
            service_id="email_generator",
            display_name="Email Test Portal",
            port=3050,
            working_dir=os.path.join(root_dir, "tools", "po-email-generator"),
            command=["node.exe", "server.js"],
            browser_url="http://localhost:3050",
            log_color="#f59e0b",
        ),
        ServiceInfo(
            service_id="rabbitmq",
            display_name="RabbitMQ Broker",
            port=5672,
            working_dir=root_dir,
            command=[],  # System service or external container
            browser_url="http://localhost:15672",
            log_color="#fb923c",
        ),
    ]


def check_port_listening(port_number: int) -> bool:
    try:
        with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as sock:
            sock.settimeout(0.25)
            result = sock.connect_ex(("127.0.0.1", port_number))
            return result == 0
    except Exception:
        return False


def terminate_process_tree(process_id: Optional[int]) -> None:
    if not process_id:
        return
    try:
        # Forcefully terminate process and all child processes on Windows
        subprocess.run(
            ["taskkill", "/F", "/T", "/PID", str(process_id)],
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL,
            timeout=5,
        )
    except Exception:
        pass
