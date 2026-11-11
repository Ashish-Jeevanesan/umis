"""
Tkinter Desktop Service Manager for UMIS, PO Processor, Frontend, and Email Generator.
Provides live port monitoring, individual service controls, and embedded streaming terminals.
"""

import os
import queue
import subprocess
import threading
import tkinter as tk
import webbrowser
from tkinter import messagebox, ttk
from typing import Dict, Optional

from service_config import (
    ServiceInfo,
    check_port_listening,
    get_service_definitions,
    terminate_process_tree,
)
from terminal_widget import TerminalTab


class ServiceManagerApp:
    def __init__(self, root: tk.Tk):
        self.root = root
        self.root.title("UMIS + PO Processor — Service Control Center")
        self.root.geometry("1100x780")
        self.root.minsize(900, 600)

        self.services = get_service_definitions()
        self.processes: Dict[str, subprocess.Popen] = {}
        self.status_labels: Dict[str, ttk.Label] = {}
        self.terminal_tabs: Dict[str, TerminalTab] = {}
        self.log_queue = queue.Queue()

        self._configure_styles()
        self._build_ui()
        self._start_background_tasks()

        self.root.protocol("WM_DELETE_WINDOW", self._on_window_close)

    def _configure_styles(self):
        style = ttk.Style()
        style.theme_use("clam")
        style.configure("TFrame", background="#f8fafc")
        style.configure("Header.TLabel", font=("Segoe UI", 14, "bold"), foreground="#0f172a", background="#f8fafc")
        style.configure("Card.TFrame", background="#ffffff", relief="ridge", borderwidth=1)
        style.configure("ServiceName.TLabel", font=("Segoe UI", 11, "bold"), foreground="#1e293b", background="#ffffff")
        style.configure("Online.TLabel", font=("Segoe UI", 9, "bold"), foreground="#16a34a", background="#ffffff")
        style.configure("Offline.TLabel", font=("Segoe UI", 9, "bold"), foreground="#dc2626", background="#ffffff")

    def _build_ui(self):
        main_container = ttk.Frame(self.root, padding=10)
        main_container.pack(fill=tk.BOTH, expand=True)

        # Header with Title and Global Action Buttons
        header_frame = ttk.Frame(main_container)
        header_frame.pack(fill=tk.X, pady=(0, 10))

        title_label = ttk.Label(header_frame, text="Microservice Control Center & Live Console", style="Header.TLabel")
        title_label.pack(side=tk.LEFT)

        start_all_button = ttk.Button(header_frame, text="▶ Start All", command=self.start_all_services)
        start_all_button.pack(side=tk.RIGHT, padx=4)

        stop_all_button = ttk.Button(header_frame, text="⏹ Stop All", command=self.stop_all_services)
        stop_all_button.pack(side=tk.RIGHT, padx=4)

        refresh_button = ttk.Button(header_frame, text="🔄 Check Ports", command=self.refresh_port_statuses)
        refresh_button.pack(side=tk.RIGHT, padx=4)

        # Service Control Cards Frame
        cards_frame = ttk.Frame(main_container)
        cards_frame.pack(fill=tk.X, pady=(0, 10))

        for service in self.services:
            self._create_service_card(cards_frame, service)

        # Bottom Notebook with Terminal Tabs
        notebook_frame = ttk.Frame(main_container)
        notebook_frame.pack(fill=tk.BOTH, expand=True)

        self.notebook = ttk.Notebook(notebook_frame)
        self.notebook.pack(fill=tk.BOTH, expand=True)

        # Tab 1: Combined logs
        combined_tab = TerminalTab(self.notebook, "All Services")
        self.notebook.add(combined_tab, text=" All Services ")
        self.terminal_tabs["all"] = combined_tab

        # Tabs per individual service
        for service in self.services:
            if service.command:  # Only for managed services
                tab = TerminalTab(self.notebook, service.display_name)
                self.notebook.add(tab, text=f" {service.display_name} (:{service.port}) ")
                self.terminal_tabs[service.service_id] = tab

    def _create_service_card(self, parent_widget, service: ServiceInfo):
        card = ttk.Frame(parent_widget, style="Card.TFrame", padding=8)
        card.pack(fill=tk.X, pady=3)

        name_label = ttk.Label(card, text=f"{service.display_name} (Port {service.port})", style="ServiceName.TLabel")
        name_label.pack(side=tk.LEFT, padx=6)

        status_label = ttk.Label(card, text="● CHECKING...", style="Offline.TLabel")
        status_label.pack(side=tk.LEFT, padx=12)
        self.status_labels[service.service_id] = status_label

        if service.browser_url:
            open_browser_btn = ttk.Button(card, text="🌐 Open", width=8, command=lambda url=service.browser_url: webbrowser.open(url))
            open_browser_btn.pack(side=tk.RIGHT, padx=4)

        if service.command:
            stop_btn = ttk.Button(card, text="⏹ Stop", width=8, command=lambda s_id=service.service_id: self.stop_service(s_id))
            stop_btn.pack(side=tk.RIGHT, padx=4)

            start_btn = ttk.Button(card, text="▶ Start", width=8, command=lambda s_id=service.service_id: self.start_service(s_id))
            start_btn.pack(side=tk.RIGHT, padx=4)

    def start_service(self, service_id: str):
        service = next((item for item in self.services if item.service_id == service_id), None)
        if not service or not service.command:
            return

        if check_port_listening(service.port):
            messagebox.showinfo("Already Running", f"{service.display_name} is already active on port {service.port}.")
            return

        try:
            process = subprocess.Popen(
                service.command,
                cwd=service.working_dir,
                stdout=subprocess.PIPE,
                stderr=subprocess.STDOUT,
                text=True,
                bufsize=1,
                creationflags=subprocess.CREATE_NO_WINDOW if os.name == "nt" else 0,
            )
            self.processes[service_id] = process

            reader_thread = threading.Thread(
                target=self._stream_process_output, args=(service_id, process), daemon=True
            )
            reader_thread.start()

            self._log_system_message(service_id, f"=== Started {service.display_name} (PID: {process.pid}) ===\n")
            self.root.after(1000, self.refresh_port_statuses)
        except Exception as error:
            messagebox.showerror("Launch Error", f"Failed to start {service.display_name}:\n{str(error)}")

    def stop_service(self, service_id: str):
        service = next((item for item in self.services if item.service_id == service_id), None)
        if not service:
            return

        process = self.processes.get(service_id)
        if process:
            terminate_process_tree(process.pid)
            self.processes.pop(service_id, None)
            self._log_system_message(service_id, f"=== Stopped {service.display_name} ===\n")
        elif check_port_listening(service.port):
            # External or orphaned process listening on the port
            self._terminate_port_owner(service.port)
            self._log_system_message(service_id, f"=== Terminated external process on port {service.port} ===\n")

        self.root.after(1000, self.refresh_port_statuses)

    def _terminate_port_owner(self, port_number: int):
        try:
            # Find owning PID on Windows
            output = subprocess.check_output(f"netstat -ano | findstr :{port_number}", shell=True, text=True)
            for line in output.splitlines():
                parts = line.strip().split()
                if len(parts) >= 5 and "LISTENING" in parts:
                    pid = parts[-1]
                    terminate_process_tree(int(pid))
        except Exception:
            pass

    def start_all_services(self):
        for service in self.services:
            if service.command:
                self.start_service(service.service_id)

    def stop_all_services(self):
        for service in self.services:
            if service.command:
                self.stop_service(service.service_id)

    def _stream_process_output(self, service_id: str, process: subprocess.Popen):
        try:
            for line in iter(process.stdout.readline, ""):
                if not line:
                    break
                self.log_queue.put((service_id, line))
        except Exception:
            pass
        finally:
            process.stdout.close()

    def _log_system_message(self, service_id: str, message: str):
        self.log_queue.put((service_id, message))

    def _start_background_tasks(self):
        self.refresh_port_statuses()
        self._schedule_port_polling()
        self._schedule_log_queue_drain()

    def _schedule_port_polling(self):
        self.refresh_port_statuses()
        self.root.after(2000, self._schedule_port_polling)

    def refresh_port_statuses(self):
        for service in self.services:
            is_active = check_port_listening(service.port)
            label = self.status_labels.get(service.service_id)
            if label:
                if is_active:
                    label.config(text=f"● ONLINE (Port {service.port})", style="Online.TLabel")
                else:
                    label.config(text=f"○ OFFLINE (Port {service.port})", style="Offline.TLabel")

    def _schedule_log_queue_drain(self):
        try:
            while not self.log_queue.empty():
                service_id, line = self.log_queue.get_nowait()
                # Append to dedicated service tab
                tab = self.terminal_tabs.get(service_id)
                if tab:
                    tab.append_line(line)
                # Append to combined tab with service prefix
                combined = self.terminal_tabs.get("all")
                if combined:
                    combined.append_line(f"[{service_id}] {line}")
        except queue.Empty:
            pass
        finally:
            self.root.after(40, self._schedule_log_queue_drain)

    def _on_window_close(self):
        active_count = len(self.processes)
        if active_count > 0:
            if messagebox.askyesno("Exit Service Manager", f"There are {active_count} active services running.\nStop all services before exiting?"):
                self.stop_all_services()
        self.root.destroy()


def main():
    root = tk.Tk()
    app = ServiceManagerApp(root)
    root.mainloop()


if __name__ == "__main__":
    main()
