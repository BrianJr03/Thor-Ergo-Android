"""Prepare native self-tests like Armada 0.1.7's real supervisor.

The release carries gtk-layer-shell in runtime/usr/lib64. It is not a new
system dependency. Both the dynamic loader and GI must see that private runtime
BEFORE Python imports GTK. Merely assigning LD_LIBRARY_PATH inside an already
running process is insufficient on Linux. No global environment is changed.
"""
from __future__ import annotations
import os
from pathlib import Path
import sys
from typing import Mapping, Sequence

STARTUP_KEYS = ("LD_LIBRARY_PATH", "GI_TYPELIB_PATH", "PYTHONPATH", "LD_PRELOAD")


def native_test_environment(app: Path, inherited: Mapping[str, str] | None = None) -> dict[str, str]:
    """Return a private child environment; leave the caller's mapping untouched.

    LD_LIBRARY_PATH and GI_TYPELIB_PATH deliberately match portable_environment
    in the original supervisor, including replacement rather than concatenation.
    System-installed GTK remains usable when no portable library is present.
    """
    app = Path(app).resolve(strict=True)
    if not (app / "src").is_dir():
        raise ValueError("A copia da aplicacao nao contem a pasta src: " + str(app))
    env = dict(os.environ if inherited is None else inherited)
    lib = app / "runtime/usr/lib64"
    shared = lib / "libgtk-layer-shell.so.0"
    if shared.is_symlink() and not shared.exists():
        raise ValueError("A biblioteca GtkLayerShell incluida tem um link quebrado: " + str(shared))
    if shared.exists():
        if not shared.is_file():
            raise ValueError("A biblioteca GtkLayerShell incluida nao e um ficheiro: " + str(shared))
        typelib = lib / "girepository-1.0/GtkLayerShell-0.1.typelib"
        if not typelib.is_file():
            raise ValueError("Falta o typelib GtkLayerShell incluido na copia: " + str(typelib))
        env["LD_LIBRARY_PATH"] = str(lib)
        env["GI_TYPELIB_PATH"] = str(typelib.parent)
    env["PYTHONPATH"] = str(app / "src")
    env["PYTHONDONTWRITEBYTECODE"] = "1"
    env["PYTHONUNBUFFERED"] = "1"
    env["PYTHONUTF8"] = "1"
    env["PYTHONIOENCODING"] = "utf-8"
    # The real GUI supervisor also excludes inherited preload injection.
    env.pop("LD_PRELOAD", None)
    return env


def ensure_native_startup(app: Path, argv: Sequence[str] | None = None) -> dict[str, str]:
    """Re-exec direct selftest --native invocations once, before importing GI.

    The installer normally supplies the correct environment on subprocess.run,
    so this is also a safety net for manual SSH invocation. Equality of the
    startup variables makes the operation idempotent, without a trusted marker.
    """
    env = native_test_environment(app)
    if any(os.environ.get(key) != env.get(key) for key in STARTUP_KEYS):
        arguments = list(sys.argv if argv is None else argv)
        if not arguments:
            raise ValueError("Falta o comando do teste nativo.")
        print("[Thor] A preparar as bibliotecas privadas da app para o teste nativo.", flush=True)
        os.execve(sys.executable, [sys.executable, "-u", "-B", *arguments], env)
    return env
