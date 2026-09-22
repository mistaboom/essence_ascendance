"""Export the authored Focus.bbmodel into the existing EAM1 static mesh format.

The source mesh is authored in Blockbench with its long axis on Z. Its center
is (7, 7, 24) pixels; the installed center is (8, 24, 8) block pixels.
"""
import base64
import json
import math
from pathlib import Path
import struct
import zlib


ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "tools" / "source_assets" / "Focus.bbmodel"
ASSETS = ROOT / "common" / "src" / "main" / "resources" / "assets" / "essence_ascendance"


def point(raw):
    return ((raw[0] + 1.0) / 16.0, raw[2] / 16.0, (raw[1] + 1.0) / 16.0)


def subtract(a, b):
    return tuple(x - y for x, y in zip(a, b))


def cross(a, b):
    return (a[1] * b[2] - a[2] * b[1],
            a[2] * b[0] - a[0] * b[2],
            a[0] * b[1] - a[1] * b[0])


def main():
    model = json.loads(SOURCE.read_text(encoding="utf-8"))
    mesh = model["elements"][0]
    vertices = mesh["vertices"]
    output = []
    center = (0.5, 1.5, 0.5)
    for face in mesh["faces"].values():
        names = face["vertices"]
        points = [point(vertices[name]) for name in names]
        normal = cross(subtract(points[1], points[0]), subtract(points[2], points[0]))
        midpoint = tuple(sum(p[i] for p in points) / 3.0 for i in range(3))
        outward = subtract(midpoint, center)
        if sum(normal[i] * outward[i] for i in range(3)) < 0:
            names = [names[0], names[2], names[1]]
            points = [points[0], points[2], points[1]]
            normal = tuple(-n for n in normal)
        length = math.sqrt(sum(n * n for n in normal))
        normal = tuple(n / length for n in normal)
        for name, pos in zip(names, points):
            uv = face["uv"][name]
            output.extend((*pos, uv[0] / 16.0, uv[1] / 16.0, *normal))

    mesh_path = ASSETS / "meshes" / "focus.eamesh"
    mesh_path.parent.mkdir(parents=True, exist_ok=True)
    mesh_path.write_bytes(struct.pack(">II", 0x45414D31, len(output) // 24)
                          + struct.pack(">" + "f" * len(output), *output))
    texture = model["textures"][0]["source"].split(",", 1)[1]
    if not base64.b64decode(texture).startswith(b"\x89PNG"):
        raise ValueError("Focus source texture is not PNG")
    texture_path = ASSETS / "textures" / "item" / "focus.png"
    texture_path.parent.mkdir(parents=True, exist_ok=True)
    # The authoring PNG uses eight white texels in one row, with transparent
    # pixels everywhere else. A white fill avoids filtering that row into
    # transparency; it keeps the supplied tint-neutral appearance.
    def chunk(kind, payload):
        return (struct.pack(">I", len(payload)) + kind + payload
                + struct.pack(">I", zlib.crc32(kind + payload) & 0xFFFFFFFF))

    pixels = b"".join(b"\0" + b"\xff\xff\xff\xff" * 16 for _ in range(16))
    texture_path.write_bytes(b"\x89PNG\r\n\x1a\n"
                             + chunk(b"IHDR", struct.pack(">IIBBBBB", 16, 16, 8, 6, 0, 0, 0))
                             + chunk(b"IDAT", zlib.compress(pixels))
                             + chunk(b"IEND", b""))


if __name__ == "__main__":
    main()
