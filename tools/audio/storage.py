"""Storage and immutable revisions; no workstation paths are build inputs."""
import contextlib
import hashlib
import json
import os
import time
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]

def read(path):
    def unique(pairs):
        result = {}
        for key, value in pairs:
            if key in result:
                raise ValueError(f"Duplicate JSON key: {key}")
            result[key] = value
        return result
    return json.loads(Path(path).read_text(encoding="utf-8"), object_pairs_hook=unique,
                      parse_constant=lambda x: (_ for _ in ()).throw(ValueError(x)))

def write(path, data):
    path = Path(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    temp = path.with_suffix(path.suffix + ".tmp")
    temp.write_text(json.dumps(data, indent=2, allow_nan=False) + "\n", encoding="utf-8")
    # Windows indexers can briefly hold a read handle without delete sharing.
    # Keep the atomic rename and bounded failure, never truncate the owner file.
    for attempt in range(10):
        try:
            os.replace(temp, path)
            break
        except PermissionError:
            if attempt == 9:
                raise
            time.sleep(.1)

def digest(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()

def inside(root, relative):
    root = Path(root).resolve()
    path = root / relative
    if Path(relative).is_absolute() or ".." in Path(relative).parts:
        raise ValueError(f"Unsafe relative path: {relative}")
    if not path.resolve().is_relative_to(root):
        raise ValueError(f"Redirected path: {path}")
    return path

@contextlib.contextmanager
def lease(root):
    path = Path(root) / ".audio-workflow.lock"
    path.parent.mkdir(parents=True, exist_ok=True)
    # Deliberately no stale-lock guessing: an interrupted operation needs inspection.
    with path.open("x", encoding="utf-8") as stream:
        stream.write(str(os.getpid()))
    try:
        yield
    finally:
        path.unlink()

class Storage:
    def __init__(self, auxiliary):
        self.root = Path(auxiliary).resolve() / "Audio"
        for name in ("projects", "masters", "previews", "exports", "references", "backups"):
            (self.root / name).mkdir(parents=True, exist_ok=True)

    def path(self, kind, family, cue, revision):
        return inside(self.root, f"{kind}/{family}/{cue}/{revision}")

    def revision(self, family, cue):
        parent = self.path("masters", family, cue, "")
        parent.mkdir(parents=True, exist_ok=True)
        number = 1
        while (parent / f"r{number:04d}").exists():
            number += 1
        revision = f"r{number:04d}"
        # Reserve before authoring. Failed attempts stay available for diagnosis.
        (parent / revision).mkdir()
        return revision
