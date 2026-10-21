#!/usr/bin/env python3
"""Genera el «developer token» de ShazamKit para Android: un JWT firmado con ES256.

El SDK de Android no lo genera: hay que dárselo hecho (`DeveloperTokenProvider`). Se firma
con la clave privada (.p8) de un Media ID con ShazamKit, en el portal de Apple:
Certificates, IDs & Profiles › Identifiers › Media IDs (con ShazamKit) y Keys › una clave
con «Media Services (MusicKit, ShazamKit)». Ver CLAUDE.md.

Sólo usa la biblioteca estándar y `openssl`, que viene en el Mac y en Debian.

Uso:
    Tools/shazam-token.py                  → imprime el token
    Tools/shazam-token.py --dias 90        → con otra caducidad (por omisión 90)
    Tools/shazam-token.py --comprobar TOKEN → decodifica uno y dice cuándo caduca

La configuración, **fuera del repositorio**, en ~/.config/shazamkit/token.env (permisos 600):
    SHAZAM_KEY_ID=ABC123DEFG
    SHAZAM_TEAM_ID=XXXXXXXXXX
    SHAZAM_KEY_FILE=~/.config/shazamkit/AuthKey_ABC123DEFG.p8
"""

from __future__ import annotations

import argparse
import base64
import json
import os
import subprocess
import sys
import tempfile
import time
from pathlib import Path

CONFIG = Path.home() / ".config/shazamkit/token.env"
# Apple admite hasta 6 meses para los tokens de MusicKit; un plugin de Flutter habla de 3
# para ShazamKit. Por omisión, 90 días, que vale en los dos casos.
DEFAULT_DAYS = 90
MAX_SECONDS = 15_777_000          # 6 meses, el tope de Apple


def b64url(data: bytes) -> str:
    return base64.urlsafe_b64encode(data).rstrip(b"=").decode()


def b64url_decode(text: str) -> bytes:
    return base64.urlsafe_b64decode(text + "=" * (-len(text) % 4))


def der_to_raw(der: bytes) -> bytes:
    """openssl da la firma ECDSA en DER (SEQUENCE { r, s }); JWT la quiere como r‖s, 32+32."""
    if der[0] != 0x30:
        raise ValueError("firma DER inesperada")
    index = 2 if der[1] < 0x80 else 2 + (der[1] & 0x7F)
    parts = []
    for _ in range(2):
        if der[index] != 0x02:
            raise ValueError("firma DER inesperada")
        length = der[index + 1]
        value = der[index + 2:index + 2 + length]
        parts.append(value.lstrip(b"\x00").rjust(32, b"\x00"))
        index += 2 + length
    return parts[0] + parts[1]


def sign_es256(message: bytes, key_file: Path) -> bytes:
    with tempfile.NamedTemporaryFile() as data:
        data.write(message)
        data.flush()
        der = subprocess.run(["openssl", "dgst", "-sha256", "-sign", str(key_file), data.name],
                             capture_output=True, check=True).stdout
    return der_to_raw(der)


def make_token(key_id: str, team_id: str, key_file: Path, days: int,
               now: int | None = None) -> str:
    issued = int(now if now is not None else time.time())
    expires = issued + min(days * 86400, MAX_SECONDS)
    header = b64url(json.dumps({"alg": "ES256", "kid": key_id}, separators=(",", ":")).encode())
    payload = b64url(json.dumps({"iss": team_id, "iat": issued, "exp": expires},
                                separators=(",", ":")).encode())
    signing_input = f"{header}.{payload}".encode()
    return f"{header}.{payload}.{b64url(sign_es256(signing_input, key_file))}"


def read_config() -> dict[str, str]:
    if not CONFIG.exists():
        sys.exit(f"✗ Falta {CONFIG} (ver la cabecera de este script).")
    values = {}
    for line in CONFIG.read_text().splitlines():
        line = line.strip()
        if line and not line.startswith("#") and "=" in line:
            key, _, value = line.partition("=")
            values[key.strip()] = os.path.expanduser(value.strip().strip('"'))
    missing = [k for k in ("SHAZAM_KEY_ID", "SHAZAM_TEAM_ID", "SHAZAM_KEY_FILE") if not values.get(k)]
    if missing:
        sys.exit(f"✗ Faltan en {CONFIG}: {', '.join(missing)}")
    return values


def describe(token: str) -> str:
    header, payload, _ = token.split(".")
    h = json.loads(b64url_decode(header))
    p = json.loads(b64url_decode(payload))
    left = (p["exp"] - time.time()) / 86400
    when = time.strftime("%d-%m-%Y", time.localtime(p["exp"]))
    state = f"caduca el {when} (quedan {left:.0f} días)" if left > 0 else f"CADUCADO el {when}"
    return f"clave {h.get('kid')}, equipo {p.get('iss')}: {state}"


def main() -> int:
    parser = argparse.ArgumentParser(description="Token de ShazamKit para Android")
    parser.add_argument("--dias", type=int, default=DEFAULT_DAYS)
    parser.add_argument("--comprobar", metavar="TOKEN")
    args = parser.parse_args()
    if args.comprobar:
        print(describe(args.comprobar))
        return 0
    config = read_config()
    print(make_token(config["SHAZAM_KEY_ID"], config["SHAZAM_TEAM_ID"],
                     Path(config["SHAZAM_KEY_FILE"]), args.dias))
    return 0


if __name__ == "__main__":
    sys.exit(main())
