"""Console access to the isolated, loopback-only development audit server.

Reads its generated RCON secret without printing it. Never discovers or controls another server.
Usage: python tools/verification/native_server_console.py "essence debug balance validate"
"""
from pathlib import Path
import json
import socket
import struct
import sys
import time

ROOT = Path(__file__).resolve().parents[2]
SERVER = ROOT / "fabric/build/progression-audit-server"

def read_exact(stream, count):
    value = b""
    while len(value) < count:
        part = stream.recv(count - len(value))
        if not part:
            raise ConnectionError("Isolated server closed the console connection")
        value += part
    return value

def receive(stream):
    size = struct.unpack("<i", read_exact(stream, 4))[0]
    if size < 10 or size > 8_000_000:
        raise ValueError("Invalid console packet size")
    packet = read_exact(stream, size)
    identifier, kind = struct.unpack("<ii", packet[:8])
    return identifier, kind, packet[8:-2].decode("utf-8", errors="replace")

def send(stream, identifier, kind, payload):
    packet = struct.pack("<ii", identifier, kind) + payload.encode("utf-8") + b"\0\0"
    stream.sendall(struct.pack("<i", len(packet)) + packet)

def commands(values):
    props = dict(line.split("=", 1) for line in (SERVER / "server.properties").read_text().splitlines()
                 if "=" in line and not line.startswith("#"))
    if props.get("server-ip") != "127.0.0.1":
        raise ValueError("Audit server must bind to loopback")
    with socket.create_connection(("127.0.0.1", int(props["rcon.port"])), timeout=15) as stream:
        stream.settimeout(2400)
        send(stream, 1, 3, props["rcon.password"])
        if receive(stream)[0] == -1:
            raise PermissionError("Isolated console authentication failed")
        for index, command in enumerate(values, 2):
            start = time.monotonic()
            send(stream, index, 2, command)
            # Native RCON expects one request per read; wait for the first response before
            # sending a delimiter so two small requests cannot arrive in the same read.
            identifier, kind, message = receive(stream)
            if identifier != index:
                raise ValueError("Unexpected first console response identifier")
            parts = [message]
            send(stream, index + 1_000_000, 2, "")
            while True:
                identifier, kind, message = receive(stream)
                if identifier == index + 1_000_000:
                    break
                if identifier != index:
                    raise ValueError("Unexpected console response identifier")
                parts.append(message)
            yield {"command": command, "seconds": round(time.monotonic() - start, 3), "response": "".join(parts)}

if __name__ == "__main__":
    for result in commands(sys.argv[1:]):
        print(json.dumps(result, ensure_ascii=False), flush=True)
