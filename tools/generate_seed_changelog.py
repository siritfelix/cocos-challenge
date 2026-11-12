"""Generador del changelog de seed (0003) a partir del database.sql de referencia.

Las cotizaciones del archivo de referencia tienen dos filas con NULL en
open/high/low; el esquema autoritativo exige NOT NULL y valores positivos, asi
que esos campos se completan con el valor de close (research R-019).
"""
import re
import sys
from datetime import datetime

SRC = "database.sql"
OUT = "src/main/resources/db/changelog/data/0003-seed-reference-data.yaml"


def yaml_quote(value: str) -> str:
    """Escapa un valor para YAML con comillas dobles."""
    return '"' + value.replace("\\", "\\\\").replace('"', '\\"') + '"'


def sql_literal(value: str) -> str:
    """Escapa un valor como literal de texto de PostgreSQL."""
    return "'" + value.replace("'", "''") + "'"


def main() -> int:
    raw = open(SRC, encoding="utf-8").read()

    users = re.findall(r"\('([^']+)','(\d+)'\)", raw)
    instruments = re.findall(r"\('([A-Z0-9]+)','([^']*)','(ACCIONES|MONEDA)'\)", raw)
    quotes = re.findall(
        r"\((\d+),'(\d{4}-\d{2}-\d{2})',"
        r"(NULL|[\d.]+),(NULL|[\d.]+),(NULL|[\d.]+),(NULL|[\d.]+),(NULL|[\d.]+)\)",
        raw,
    )

    if not users or not instruments or not quotes:
        print("ERROR: no se pudo extraer datos del fuente")
        return 1

    # id -> ticker, para resolver cada cotizacion por ticker en vez de por id
    id_to_ticker = {i: ticker for i, (ticker, _, _) in enumerate(instruments, start=1)}
    if len(id_to_ticker) != len(instruments):
        print("ERROR: ids de instrumentos duplicados")
        return 1

    out = []
    out.append("databaseChangeLog:")
    out.append("")

    # ---------------------------------------------------------------- usuarios
    out.append("  # ---------------------------------------------------------------------")
    out.append("  # users - cuentas de referencia. El numero de cuenta (account_number)")
    out.append("  # es el identificador que viaja en la cabecera X-AccountNumber.")
    out.append("  # ---------------------------------------------------------------------")
    out.append("  - changeSet:")
    out.append("      id: 0003-001-seed-users")
    out.append("      author: portfolio")
    out.append("      preConditions:")
    out.append("        onFail: MARK_RAN")
    out.append("        onFailMessage: usuarios ya cargados")
    out.append("        # Se aplica solo si la tabla aun no tiene filas (count = 0).")
    out.append("        sqlCheck:")
    out.append("          expectedResult: 0")
    out.append("          sql: SELECT COUNT(*) FROM users")
    out.append("      changes:")
    for email, account in users:
        out.append("        - insert:")
        out.append("            tableName: users")
        out.append("            columns:")
        out.append("              - column:")
        out.append("                  name: email")
        out.append(f"                  value: {yaml_quote(email)}")
        out.append("              - column:")
        out.append("                  name: account_number")
        out.append(f"                  value: {yaml_quote(account)}")
    out.append("")

    # ------------------------------------------------------------ instrumentos
    out.append("  # ---------------------------------------------------------------------")
    out.append("  # instruments - catalogo de referencia. ARS es el instrumento de")
    out.append("  # efectivo (tipo MONEDA) y no es operable como compra ni venta.")
    out.append("  # ---------------------------------------------------------------------")
    out.append("  - changeSet:")
    out.append("      id: 0003-002-seed-instruments")
    out.append("      author: portfolio")
    out.append("      preConditions:")
    out.append("        onFail: MARK_RAN")
    out.append("        onFailMessage: instrumentos ya cargados")
    out.append("        # Se aplica solo si la tabla aun no tiene filas (count = 0).")
    out.append("        sqlCheck:")
    out.append("          expectedResult: 0")
    out.append("          sql: SELECT COUNT(*) FROM instruments")
    out.append("      changes:")
    for ticker, name, kind in instruments:
        # El nombre del efectivo sigue la spec (ARS / Peso argentino), no el
        # 'PESOS' del archivo de referencia (research R-019).
        display_name = "Peso argentino" if ticker == "ARS" else name
        out.append("        - insert:")
        out.append("            tableName: instruments")
        out.append("            columns:")
        out.append("              - column:")
        out.append("                  name: ticker")
        out.append(f"                  value: {yaml_quote(ticker)}")
        out.append("              - column:")
        out.append("                  name: name")
        out.append(f"                  value: {yaml_quote(display_name)}")
        out.append("              - column:")
        out.append("                  name: type")
        out.append(f"                  value: {yaml_quote(kind)}")
    out.append("")

    # ------------------------------------------------------------- marketdata
    out.append("  # ---------------------------------------------------------------------")
    out.append("  # marketdata - cotizaciones de referencia. Se resuelven por ticker")
    out.append("  # para no depender del id generado. previous_close difiere de close")
    out.append("  # para poder verificar el rendimiento diario.")
    out.append("  # ---------------------------------------------------------------------")
    out.append("  - changeSet:")
    out.append("      id: 0003-003-seed-marketdata")
    out.append("      author: portfolio")
    out.append("      preConditions:")
    out.append("        onFail: MARK_RAN")
    out.append("        onFailMessage: cotizaciones ya cargadas")
    out.append("        # Se aplica solo si la tabla aun no tiene filas (count = 0).")
    out.append("        sqlCheck:")
    out.append("          expectedResult: 0")
    out.append("          sql: SELECT COUNT(*) FROM marketdata")
    out.append("      changes:")

    null_filled = 0
    for inst_id, date, o, h, low, close, prev in quotes:
        ticker = id_to_ticker[int(inst_id)]
        # El esquema exige valores positivos y NOT NULL; donde la referencia
        # trae NULL se usa el cierre como aproximacion del valor de la sesion.
        def pick(value: str, fallback: str) -> str:
            if value == "NULL":
                return fallback
            return value

        close_value = pick(close, "0")
        open_value = pick(o, close_value)
        high_value = pick(h, close_value)
        low_value = pick(low, close_value)
        prev_value = pick(prev, close_value)
        if "NULL" in (o, h, low, prev):
            null_filled += 1

        def dec(value: str) -> str:
            # Redondea a 2 decimales, que es la escala del esquema.
            return f"{float(value):.2f}"

        # La columna datetime es TIMESTAMP sin zona horaria (convencion UTC).
        timestamp = datetime.strptime(date, "%Y-%m-%d").strftime("%Y-%m-%d %H:%M:%S")

        # INSERT ... SELECT: resuelve el id del instrumento por ticker, de modo
        # que el seed no dependa del id generado por la secuencia.
        out.append("        - sql:")
        out.append(
            "            sql: INSERT INTO marketdata (instrument_id, high, low, open, close,"
            " previous_close, datetime) "
            f"SELECT id, {dec(high_value)}, {dec(low_value)}, {dec(open_value)},"
            f" {dec(close_value)}, {dec(prev_value)}, TIMESTAMP '{timestamp}' "
            f"FROM instruments WHERE ticker = {sql_literal(ticker)}"
        )

    out.append("")
    with open(OUT, "w", encoding="utf-8", newline="\n") as handle:
        handle.write("\n".join(out))

    print(f"OK: {len(users)} usuarios, {len(instruments)} instrumentos, {len(quotes)} cotizaciones")
    print(f"    filas con NULL completadas desde close: {null_filled}")
    print(f"    generado en {OUT}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
