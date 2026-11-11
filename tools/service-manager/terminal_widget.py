"""
Custom scrolled terminal widget for streaming service console logs with auto-scroll and buffer limits.
"""

import re
import tkinter as tk
from tkinter import scrolledtext, ttk

ANSI_ESCAPE_REGEX = re.compile(r"\x1B(?:[@-Z\\-_]|\[[0-?]*[ -/]*[@-~])")


class TerminalTab(ttk.Frame):
    """
    Terminal view widget styled like a dark-mode console with auto-scroll and buffer controls.
    """

    MAX_BUFFER_LINES = 2500

    def __init__(self, parent_widget, title: str):
        super().__init__(parent_widget)
        self.title = title
        self.auto_scroll_var = tk.BooleanVar(value=True)
        self._line_count = 0

        self._build_toolbar()
        self._build_text_area()

    def _build_toolbar(self):
        toolbar = ttk.Frame(self)
        toolbar.pack(fill=tk.X, padx=6, pady=(4, 2))

        title_label = ttk.Label(toolbar, text=f"Console Output: {self.title}", font=("Segoe UI", 9, "bold"))
        title_label.pack(side=tk.LEFT, padx=(2, 10))

        clear_button = ttk.Button(toolbar, text="Clear Console", command=self.clear_logs)
        clear_button.pack(side=tk.RIGHT, padx=4)

        auto_scroll_checkbox = ttk.Checkbutton(
            toolbar, text="Auto-Scroll", variable=self.auto_scroll_var
        )
        auto_scroll_checkbox.pack(side=tk.RIGHT, padx=6)

    def _build_text_area(self):
        container = ttk.Frame(self)
        container.pack(fill=tk.BOTH, expand=True, padx=6, pady=(0, 6))

        self.text_widget = scrolledtext.ScrolledText(
            container,
            wrap=tk.WORD,
            bg="#0f172a",
            fg="#f8fafc",
            insertbackground="#38bdf8",
            selectbackground="#334155",
            selectforeground="#ffffff",
            font=("Consolas", 10),
            state=tk.DISABLED,
            borderwidth=0,
            highlightthickness=1,
            highlightbackground="#1e293b",
        )
        self.text_widget.pack(fill=tk.BOTH, expand=True)

        # Define color tags for combined logs
        self.text_widget.tag_configure("error", foreground="#f87171")
        self.text_widget.tag_configure("warn", foreground="#fbbf24")
        self.text_widget.tag_configure("info", foreground="#60a5fa")
        self.text_widget.tag_configure("success", foreground="#4ade80")

    def append_line(self, raw_text: str, tag_name: str = None):
        clean_text = ANSI_ESCAPE_REGEX.sub("", raw_text)
        if not clean_text:
            return

        self.text_widget.config(state=tk.NORMAL)

        # Prune buffer if exceeding maximum line threshold
        self._line_count += 1
        if self._line_count > self.MAX_BUFFER_LINES:
            self.text_widget.delete("1.0", "500.0")
            self._line_count -= 500

        # Automatic level detection for coloring if tag not specified
        if not tag_name:
            upper_line = clean_text.upper()
            if "ERROR" in upper_line or "EXCEPTION" in upper_line or "FAILED" in upper_line:
                tag_name = "error"
            elif "WARN" in upper_line:
                tag_name = "warn"
            elif "STARTED" in upper_line or "SUCCESS" in upper_line or "READY" in upper_line:
                tag_name = "success"

        if tag_name:
            self.text_widget.insert(tk.END, clean_text, tag_name)
        else:
            self.text_widget.insert(tk.END, clean_text)

        if self.auto_scroll_var.get():
            self.text_widget.see(tk.END)

        self.text_widget.config(state=tk.DISABLED)

    def clear_logs(self):
        self.text_widget.config(state=tk.NORMAL)
        self.text_widget.delete("1.0", tk.END)
        self._line_count = 0
        self.text_widget.config(state=tk.DISABLED)
