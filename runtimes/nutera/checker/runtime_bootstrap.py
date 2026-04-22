import ctypes
import os
import pathlib
import platform
import re
import struct
import subprocess
import sys
from dataclasses import dataclass
from typing import List, Optional, Tuple


ROOT = pathlib.Path(__file__).resolve().parent.parent
VC_RUNTIME_DLLS = ("vcruntime140.dll", "vcruntime140_1.dll", "msvcp140.dll")

WINDOWS_SO_ONLY_ERROR = (
    "\u5f53\u524d\u4e3a Windows \u73af\u5883\uff0c\u4f46 Z3 \u672c\u5730\u5e93\u76ee\u5f55\u4e2d"
    "\u53ea\u6709 Linux .so \u6587\u4ef6\uff0c\u7f3a\u5c11 Windows DLL"
)
VC_RUNTIME_HINT = (
    "\u68c0\u6d4b\u5230 libz3java.dll \u5b58\u5728\uff0c\u4f46\u5176\u4f9d\u8d56\u7684 VC++ runtime "
    "\u53ef\u80fd\u7f3a\u5931\uff0c\u8bf7\u5b89\u88c5 Microsoft Visual C++ Redistributable (x64)\u3002"
)

_BOOTSTRAP_CACHE = None
_DLL_DIRECTORY_HANDLES = []
_PRELOADED_DLLS = []


@dataclass
class RuntimeBootstrap:
    root: pathlib.Path
    platform_name: str
    is_windows: bool
    javachecker_jar: str
    z3_jar: Optional[str]
    classpath_entries: List[str]
    native_dir: pathlib.Path
    java_library_path: str
    java_executable: str
    java_version_output: str
    java_settings_output: str
    python_bits: int
    jvm_bits: Optional[int]
    libz3_path: pathlib.Path
    libz3java_path: pathlib.Path
    libz3_bits: Optional[int]
    libz3java_bits: Optional[int]
    missing_vc_runtime: List[str]


def _first_existing(candidates: List[str]) -> Optional[str]:
    for p in candidates:
        if p and os.path.exists(p):
            return os.path.abspath(p)
    return None


def _resolve_javachecker_jar(root: pathlib.Path) -> str:
    jar = _first_existing(
        [
            os.environ.get("JAVACHECKER_JAR"),
            str(root / "deps" / "javachecker" / "build" / "libs" / "javachecker-uber.jar"),
            str(root / "libs" / "javachecker-uber.jar"),
            "/workspace/deps/javachecker/build/libs/javachecker-uber.jar",
        ]
    )
    if not jar:
        raise FileNotFoundError(
            "Could not locate javachecker-uber.jar. "
            "Set JAVACHECKER_JAR explicitly or build deps/javachecker first."
        )
    return jar


def _resolve_z3_jar(root: pathlib.Path) -> Optional[str]:
    return _first_existing(
        [
            os.environ.get("NUTERA_Z3_JAR"),
            str(root / "libs" / "share" / "java" / "com.microsoft.z3.jar"),
            "/workspace/libs/share/java/com.microsoft.z3.jar",
        ]
    )


def _build_native_dir_candidates(root: pathlib.Path, is_windows: bool) -> List[pathlib.Path]:
    platform_dir = "windows-x64" if is_windows else "linux-x64"
    candidates: List[pathlib.Path] = []

    env_dir = (os.environ.get("NUTERA_Z3_NATIVE_DIR") or "").strip().strip("\"'")
    if env_dir:
        candidates.append(pathlib.Path(env_dir))

    candidates.extend(
        [
            root / "libs" / platform_dir,
            root / platform_dir,
            root / "libs",
            root / "lib",
            root / "bin",
            root,
            root / "libs" / "z3-z3-4.11.0" / "build",
            root / "libs" / "z3-z3-4.11.0" / "build" / "bin",
            root / "libs" / "z3-z3-4.11.0" / "build" / "lib",
        ]
    )

    workspace_root = pathlib.Path("/workspace")
    candidates.extend(
        [
            workspace_root / "libs" / platform_dir,
            workspace_root / "libs",
        ]
    )

    unique: List[pathlib.Path] = []
    seen = set()
    for raw in candidates:
        if raw is None:
            continue
        try:
            normalized = raw.expanduser().resolve()
        except Exception:
            normalized = raw.expanduser()
        key = str(normalized).lower() if os.name == "nt" else str(normalized)
        if key in seen:
            continue
        seen.add(key)
        unique.append(normalized)
    return unique


def _resolve_native_dir(root: pathlib.Path, is_windows: bool) -> pathlib.Path:
    platform_dir = "windows-x64" if is_windows else "linux-x64"
    expected = (root / "libs" / platform_dir).resolve()
    candidates = _build_native_dir_candidates(root, is_windows)
    existing_dirs = [p for p in candidates if p.is_dir()]

    for candidate in existing_dirs:
        libz3, libz3java = _resolve_native_libs(candidate, is_windows)
        if libz3.exists() and libz3java.exists():
            return candidate.resolve()

    if expected.is_dir():
        return expected

    if existing_dirs:
        return existing_dirs[0].resolve()

    return expected


def _build_classpath(root: pathlib.Path, z3_jar: Optional[str], javachecker_jar: str) -> List[str]:
    entries: List[str] = []
    seen = set()
    for p in (z3_jar, javachecker_jar):
        if p and p not in seen:
            entries.append(p)
            seen.add(p)

    libs_dir = root / "libs"
    if libs_dir.is_dir():
        for e in libs_dir.iterdir():
            if e.is_file() and e.suffix == ".jar":
                ep = str(e.resolve())
                if ep not in seen:
                    entries.append(ep)
                    seen.add(ep)
    return entries


def _prepend_env_path(var_name: str, entry: str, separator: str) -> None:
    existing = os.environ.get(var_name, "")
    parts = [p for p in existing.split(separator) if p]
    normalized = [p.lower() if os.name == "nt" else p for p in parts]
    key = entry.lower() if os.name == "nt" else entry
    if key in normalized:
        return
    os.environ[var_name] = entry + (separator + existing if existing else "")


def _merge_java_tool_options(existing: str, java_library_path: str) -> str:
    updated = re.sub(r"(?<!\S)-Djava\.library\.path=\S+", "", existing or "").strip()
    prefix = f"-Djava.library.path={java_library_path}"
    return (prefix + " " + updated).strip() if updated else prefix


def _resolve_java_executable() -> str:
    for key in ("NUTERA_JAVA_HOME", "JAVA_HOME"):
        home = (os.environ.get(key) or "").strip()
        if home:
            exe = pathlib.Path(home) / "bin" / ("java.exe" if os.name == "nt" else "java")
            if exe.exists():
                return str(exe)
    return "java"


def _run_command(command: List[str], timeout_seconds: int = 12) -> str:
    try:
        proc = subprocess.run(
            command,
            capture_output=True,
            text=True,
            timeout=timeout_seconds,
            check=False,
        )
        text = (proc.stdout or "") + (proc.stderr or "")
        return text.strip()
    except Exception as ex:
        return f"(failed to run {' '.join(command)}: {ex})"


def _extract_java_library_path(java_settings_output: str) -> str:
    for line in java_settings_output.splitlines():
        if "java.library.path =" in line:
            return line.split("=", 1)[1].strip()
    return ""


def _parse_jvm_bits(java_settings_output: str, java_version_output: str) -> Optional[int]:
    merged = "\n".join([java_settings_output or "", java_version_output or ""])
    m = re.search(r"sun\.arch\.data\.model\s*=\s*(\d+)", merged)
    if m:
        try:
            return int(m.group(1))
        except Exception:
            return None
    m = re.search(r"os\.arch\s*=\s*(\S+)", merged)
    if not m:
        return None
    arch = m.group(1).lower()
    if arch in ("amd64", "x86_64", "aarch64", "arm64"):
        return 64
    if arch in ("x86", "i386", "i686"):
        return 32
    return None


def _parse_pe_bits(path: pathlib.Path) -> Optional[int]:
    try:
        with path.open("rb") as f:
            header = f.read(512)
            if len(header) < 64 or header[:2] != b"MZ":
                return None
            pe_offset = struct.unpack_from("<I", header, 0x3C)[0]
            f.seek(pe_offset)
            if f.read(4) != b"PE\x00\x00":
                return None
            f.seek(20, os.SEEK_CUR)
            magic = struct.unpack("<H", f.read(2))[0]
            if magic == 0x20B:
                return 64
            if magic == 0x10B:
                return 32
            return None
    except Exception:
        return None


def _resolve_native_libs(native_dir: pathlib.Path, is_windows: bool) -> Tuple[pathlib.Path, pathlib.Path]:
    if is_windows:
        libz3 = native_dir / "libz3.dll"
        libz3java = native_dir / "libz3java.dll"
    else:
        libz3_candidates = [
            native_dir / "libz3.so",
            native_dir / "libz3.so.4.11",
            native_dir / "libz3.so.4.11.0.0",
        ]
        libz3java_candidates = [native_dir / "libz3java.so"]
        libz3 = next((p for p in libz3_candidates if p.exists()), libz3_candidates[0])
        libz3java = next((p for p in libz3java_candidates if p.exists()), libz3java_candidates[0])
    return libz3, libz3java


def _collect_native_scan_report(candidates: List[pathlib.Path], is_windows: bool) -> str:
    rows = []
    for candidate in candidates:
        if not candidate.is_dir():
            continue
        libz3, libz3java = _resolve_native_libs(candidate, is_windows)
        rows.append(
            f"{candidate} (libz3={'yes' if libz3.exists() else 'no'}, "
            f"libz3java={'yes' if libz3java.exists() else 'no'})"
        )
    return " | ".join(rows) if rows else "(no existing candidate directories)"


def _check_windows_layout(native_dir: pathlib.Path) -> None:
    files = [p.name.lower() for p in native_dir.iterdir() if p.is_file()]
    has_dll = any(name.endswith(".dll") for name in files)
    has_so = any(".so" in name for name in files)
    if not has_dll and has_so:
        raise RuntimeError(WINDOWS_SO_ONLY_ERROR)


def _check_windows_vc_runtime() -> List[str]:
    missing = []
    for dll in VC_RUNTIME_DLLS:
        try:
            ctypes.WinDLL(dll)
        except OSError:
            missing.append(dll)
    return missing


def _raise_windows_load_error(lib_name: str, error: OSError, missing_vc_runtime: List[str]) -> None:
    message = str(error)
    lower = message.lower()
    hints = []

    if "winerror 193" in lower or "%1 is not a valid win32 application" in lower:
        hints.append("Possible architecture mismatch: ensure Python/JVM/DLL are all 64-bit.")
    if "module could not be found" in lower or "winerror 126" in lower:
        if lib_name.lower() == "libz3java.dll":
            hints.append("libz3java.dll is present, but one of its dependencies is missing.")
            hints.append("Check that libz3.dll is in the same directory and PATH includes that directory.")
        else:
            hints.append(f"Failed to load {lib_name}; verify this DLL exists and is readable.")
    if missing_vc_runtime:
        hints.append(VC_RUNTIME_HINT)
        hints.append("Missing VC runtime DLLs: " + ", ".join(missing_vc_runtime))

    hint_text = "\n".join(hints) if hints else "Check DLL dependencies and PATH settings."
    raise RuntimeError(
        f"Windows preload failed: {lib_name}\n"
        f"OSError: {message}\n"
        f"{hint_text}"
    ) from error


def _preload_windows_dlls(libz3_path: pathlib.Path, libz3java_path: pathlib.Path, missing_vc_runtime: List[str]) -> None:
    for path in (libz3_path, libz3java_path):
        try:
            handle = ctypes.WinDLL(str(path))
            _PRELOADED_DLLS.append(handle)
        except OSError as ex:
            _raise_windows_load_error(path.name, ex, missing_vc_runtime)


def _assert_architecture(runtime: RuntimeBootstrap) -> None:
    if runtime.python_bits != 64:
        raise RuntimeError(
            f"Python architecture mismatch: expected 64-bit, got {runtime.python_bits}-bit."
        )
    if runtime.jvm_bits is not None and runtime.jvm_bits != runtime.python_bits:
        raise RuntimeError(
            f"JVM architecture mismatch: Python is {runtime.python_bits}-bit, "
            f"but JVM is {runtime.jvm_bits}-bit."
        )
    if runtime.is_windows:
        if runtime.libz3_bits is not None and runtime.libz3_bits != runtime.python_bits:
            raise RuntimeError(
                f"libz3.dll architecture mismatch: Python is {runtime.python_bits}-bit, "
                f"but libz3.dll is {runtime.libz3_bits}-bit."
            )
        if runtime.libz3java_bits is not None and runtime.libz3java_bits != runtime.python_bits:
            raise RuntimeError(
                f"libz3java.dll architecture mismatch: Python is {runtime.python_bits}-bit, "
                f"but libz3java.dll is {runtime.libz3java_bits}-bit."
            )


def _print_diagnostics(runtime: RuntimeBootstrap) -> None:
    prefix = "[rf_check][diag]"
    native_files = []
    if runtime.native_dir.is_dir():
        native_files = sorted([p.name for p in runtime.native_dir.iterdir() if p.is_file()])

    related_path_entries = []
    for entry in os.environ.get("PATH", "").split(os.pathsep):
        lower = entry.lower()
        if "z3" in lower or "nutera\\libs" in lower or "nutera/libs" in lower:
            related_path_entries.append(entry)

    print(f"{prefix} os = {runtime.platform_name}")
    print(f"{prefix} python_version = {sys.version.splitlines()[0]}")
    print(f"{prefix} python_arch = {runtime.python_bits}-bit")
    print(f"{prefix} java_executable = {runtime.java_executable}")
    print(f"{prefix} java_version = {runtime.java_version_output or '(empty)'}")

    java_lib_path = _extract_java_library_path(runtime.java_settings_output) or runtime.java_library_path
    print(f"{prefix} java.library.path = {java_lib_path}")
    print(f"{prefix} JAVA_TOOL_OPTIONS = {os.environ.get('JAVA_TOOL_OPTIONS', '(empty)')}")
    print(
        f"{prefix} PATH(z3-related) = "
        + (" | ".join(related_path_entries) if related_path_entries else "(none)")
    )
    print(f"{prefix} native_dir = {runtime.native_dir}")
    print(f"{prefix} native_dir_files = {native_files}")
    print(f"{prefix} z3_jar = {runtime.z3_jar or '(missing)'}")
    print(f"{prefix} libz3_exists = {runtime.libz3_path.exists()} ({runtime.libz3_path})")
    print(f"{prefix} libz3java_exists = {runtime.libz3java_path.exists()} ({runtime.libz3java_path})")
    print(f"{prefix} jvm_arch = {runtime.jvm_bits if runtime.jvm_bits is not None else 'unknown'}-bit")

    if runtime.is_windows:
        print(
            f"{prefix} dll_arch = libz3:{runtime.libz3_bits if runtime.libz3_bits is not None else 'unknown'}-bit, "
            f"libz3java:{runtime.libz3java_bits if runtime.libz3java_bits is not None else 'unknown'}-bit"
        )
        if runtime.missing_vc_runtime:
            print(f"{prefix} missing_vc_runtime = {', '.join(runtime.missing_vc_runtime)}")
            print(f"{prefix} {VC_RUNTIME_HINT}")


def ensure_runtime_prepared(print_diagnostics: bool = True) -> RuntimeBootstrap:
    global _BOOTSTRAP_CACHE

    if _BOOTSTRAP_CACHE is not None:
        if print_diagnostics and os.environ.get("NUTERA_Z3_DIAGNOSTICS_PRINTED") != "1":
            _print_diagnostics(_BOOTSTRAP_CACHE)
            os.environ["NUTERA_Z3_DIAGNOSTICS_PRINTED"] = "1"
        return _BOOTSTRAP_CACHE

    platform_name = platform.platform()
    is_windows = os.name == "nt"
    python_bits = struct.calcsize("P") * 8

    javachecker_jar = _resolve_javachecker_jar(ROOT)
    z3_jar = _resolve_z3_jar(ROOT)
    classpath_entries = _build_classpath(ROOT, z3_jar, javachecker_jar)
    native_candidates = _build_native_dir_candidates(ROOT, is_windows)
    native_dir = _resolve_native_dir(ROOT, is_windows)

    if not native_dir.is_dir():
        expected = "libs/windows-x64" if is_windows else "libs/linux-x64"
        raise FileNotFoundError(
            f"Z3 native directory not found: {native_dir}. Expected {expected}. "
            f"Scanned candidates: {_collect_native_scan_report(native_candidates, is_windows)}"
        )
    if is_windows:
        _check_windows_layout(native_dir)

    libz3_path, libz3java_path = _resolve_native_libs(native_dir, is_windows)
    if not libz3_path.exists():
        raise FileNotFoundError(
            f"Missing native Z3 core library: {libz3_path}. "
            f"Scanned candidates: {_collect_native_scan_report(native_candidates, is_windows)}"
        )
    if not libz3java_path.exists():
        raise FileNotFoundError(
            f"Missing native Z3 Java library: {libz3java_path}. "
            f"Scanned candidates: {_collect_native_scan_report(native_candidates, is_windows)}"
        )

    os.environ["JAVACHECKER_JAR"] = javachecker_jar

    if classpath_entries:
        current = os.environ.get("CLASSPATH", "")
        cp = os.pathsep.join(classpath_entries)
        os.environ["CLASSPATH"] = current + (os.pathsep + cp if current else cp)

    if is_windows:
        _prepend_env_path("PATH", str(native_dir), os.pathsep)
        if hasattr(os, "add_dll_directory"):
            handle = os.add_dll_directory(str(native_dir))
            _DLL_DIRECTORY_HANDLES.append(handle)
    else:
        _prepend_env_path("LD_LIBRARY_PATH", str(native_dir), ":")

    java_library_path = str(native_dir)
    os.environ["JAVA_TOOL_OPTIONS"] = _merge_java_tool_options(
        os.environ.get("JAVA_TOOL_OPTIONS", ""),
        java_library_path,
    )

    java_executable = _resolve_java_executable()
    java_version_output = _run_command([java_executable, "-version"])
    java_settings_output = _run_command([java_executable, "-XshowSettings:properties", "-version"])
    jvm_bits = _parse_jvm_bits(java_settings_output, java_version_output)

    libz3_bits = _parse_pe_bits(libz3_path) if is_windows else None
    libz3java_bits = _parse_pe_bits(libz3java_path) if is_windows else None
    missing_vc_runtime = _check_windows_vc_runtime() if is_windows else []

    runtime = RuntimeBootstrap(
        root=ROOT,
        platform_name=platform_name,
        is_windows=is_windows,
        javachecker_jar=javachecker_jar,
        z3_jar=z3_jar,
        classpath_entries=classpath_entries,
        native_dir=native_dir,
        java_library_path=java_library_path,
        java_executable=java_executable,
        java_version_output=java_version_output,
        java_settings_output=java_settings_output,
        python_bits=python_bits,
        jvm_bits=jvm_bits,
        libz3_path=libz3_path,
        libz3java_path=libz3java_path,
        libz3_bits=libz3_bits,
        libz3java_bits=libz3java_bits,
        missing_vc_runtime=missing_vc_runtime,
    )

    _assert_architecture(runtime)

    if is_windows:
        _preload_windows_dlls(libz3_path, libz3java_path, missing_vc_runtime)

    _BOOTSTRAP_CACHE = runtime
    if print_diagnostics and os.environ.get("NUTERA_Z3_DIAGNOSTICS_PRINTED") != "1":
        _print_diagnostics(runtime)
        os.environ["NUTERA_Z3_DIAGNOSTICS_PRINTED"] = "1"

    return runtime
