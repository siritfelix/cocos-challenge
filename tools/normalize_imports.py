"""Normaliza los imports de los archivos Java segun la convencion del proyecto.

Convencion (config/checkstyle/checkstyle.xml, modulo CustomImportOrder):
  1. imports estaticos
  2. java.*
  3. el resto (jakarta.*, org.*, com.*, io.*, ar.*)

Cada grupo va separado por una linea en blanco y, dentro del grupo, los
imports van ordenados alfabeticamente. Los imports estaticos se ordenan por
el nombre completo del miembro importado.

Uso: python tools/normalize_imports.py
"""
import os
import re
import sys

IMPORT_RE = re.compile(r"^import\s+(static\s+)?([\w.]+(\.\*)?)\s*;")


def group_of(is_static: bool, name: str) -> int:
    """Devuelve el indice del grupo al que pertenece un import."""
    if is_static:
        return 0
    if name.startswith("java."):
        return 1
    return 2


def normalize(path: str) -> bool:
    """Reordena el bloque de imports del archivo. Devuelve True si cambio."""
    with open(path, encoding="utf-8") as handle:
        lines = handle.read().split("\n")

    indexes = [i for i, line in enumerate(lines) if IMPORT_RE.match(line)]
    if not indexes:
        return False

    start, end = indexes[0], indexes[-1]
    imports = []
    for index in indexes:
        match = IMPORT_RE.match(lines[index])
        is_static = match.group(1) is not None
        name = match.group(2)
        imports.append((group_of(is_static, name), name, is_static))

    # Ordena por grupo y, dentro del grupo, alfabeticamente. Los imports
    # estaticos se ordenan por el nombre del miembro.
    imports.sort(key=lambda item: (item[0], item[1]))

    rendered = []
    previous_group = None
    for group, name, is_static in imports:
        if previous_group is not None and group != previous_group:
            rendered.append("")
        keyword = "import static " if is_static else "import "
        rendered.append(f"{keyword}{name};")
        previous_group = group

    new_lines = lines[:start] + rendered + lines[end + 1:]
    new_content = "\n".join(new_lines)

    with open(path, encoding="utf-8") as handle:
        original = handle.read()
    if new_content == original:
        return False

    with open(path, "w", encoding="utf-8", newline="") as handle:
        handle.write(new_content)
    return True


def main() -> int:
    changed = []
    for root, dirs, files in os.walk("src"):
        dirs[:] = [d for d in dirs if d != "target"]
        for name in files:
            if name.endswith(".java"):
                path = os.path.join(root, name)
                if normalize(path):
                    changed.append(path)

    for path in changed:
        print(f"normalizado: {path}")
    print(f"\narchivos normalizados: {len(changed)}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
