"""Valida el formato de las tareas de tasks.md segun las reglas de Spec Kit."""
import re
import sys

PATH = "specs/001-investment-portfolio-api/tasks.md"
PATTERN = re.compile(r"^- \[([ xX])\] (T\d{3})( \[P\])?( \[US\d\])? (.+)$")
STORIES = {f"US{i}" for i in range(1, 7)}


def main() -> int:
    with open(PATH, encoding="utf-8") as handle:
        lines = handle.read().split("\n")

    tasks = [l for l in lines if l.startswith("- [ ] ") or l.startswith("- [X] ")]
    ids = []
    invalid = []
    for line in tasks:
        match = PATTERN.match(line)
        if match:
            ids.append(match.group(2))
            if match.group(4) and match.group(4).strip() not in {f"[{s}]" for s in STORIES}:
                invalid.append(f"etiqueta de historia invalida: {line[:80]}")
        else:
            invalid.append(f"formato invalido: {line[:80]}")

    expected = [f"T{i:03d}" for i in range(1, 138)]
    sequential = ids == expected

    done = [l for l in tasks if l.startswith("- [X] ")]
    pending = [l for l in tasks if l.startswith("- [ ] ")]

    print(f"total de tareas      : {len(tasks)}")
    print(f"formato invalido     : {len(invalid)}")
    for issue in invalid[:5]:
        print(f"   - {issue}")
    print(f"secuencia T001..T137 : {sequential}")
    print(f"marcadas [X]         : {len(done)}")
    print(f"pendientes [ ]       : {len(pending)}")

    # La numeracion de la fase 2 debe seguir intacta.
    fase2 = [l for l in tasks if re.match(r"^- \[[ xX]\] T0(1[3-9]|[2-4][0-9]|50) ", l)]
    fase2_done = [l for l in fase2 if l.startswith("- [X] ")]
    print(f"fase 2 (T013-T050)   : {len(fase2_done)}/{len(fase2)} completadas")

    return 0 if not invalid and sequential else 1


if __name__ == "__main__":
    sys.exit(main())
