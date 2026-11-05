"""Audacity pipe owner. No UI input, restart, Exit, or closing pre-existing projects."""
import ctypes
import contextlib
import hashlib
import json
import os
import queue
import shutil
import sqlite3
import threading
import time
from pathlib import Path
from storage import digest

def installation(executable):
    executable = Path(executable)
    module = executable.parent / 'modules/mod-script-pipe.dll'
    version_api = ctypes.WinDLL('version', use_last_error=True)
    size = version_api.GetFileVersionInfoSizeW(str(executable), None)
    if not size:
        raise ValueError('Cannot read installed Audacity version')
    data = ctypes.create_string_buffer(size)
    if not version_api.GetFileVersionInfoW(str(executable), 0, size, data):
        raise ValueError('Cannot read installed Audacity version data')
    pointer, length = ctypes.c_void_p(), ctypes.c_uint()
    if not version_api.VerQueryValueW(data, '\\', ctypes.byref(pointer), ctypes.byref(length)):
        raise ValueError('Cannot read installed Audacity version fields')
    fields = ctypes.cast(pointer, ctypes.POINTER(ctypes.c_uint32))
    version = '.'.join(map(str, [fields[2] >> 16, fields[2] & 65535, fields[3] >> 16, fields[3] & 65535]))
    return {"executable": str(executable), "version": version, "sha256": digest(executable),
            "moduleSha256": digest(module)}

class Audacity:
    def __init__(self, timeout=15, log=None):
        if os.name != 'nt':
            raise RuntimeError('This tested pipe adapter supports Windows only')
        self.timeout = timeout
        self.broken = False
        self.log = log
        # Opening an absent pipe fails immediately; never launch/restart the DAW.
        self.to = open(r'\\.\pipe\ToSrvPipe', 'w', encoding='utf-8', newline='\n')
        self.from_ = open(r'\\.\pipe\FromSrvPipe', 'r', encoding='utf-8')

    def command(self, text):
        if text.startswith('GetInfo:') and 'Type=Preferences' in text:
            raise ValueError('GetInfo Preferences crashes installed Audacity 3.7.9; query individual known preferences')
        if text.split(':')[0] in {'Close', 'Exit', 'CloseProject'}:
            raise ValueError('Scripted close is unsafe in the installed Audacity 3.7.9; close projects manually, then finalize')
        if self.broken or '\n' in text or '\r' in text:
            raise ValueError('Unsafe command or expired pipe connection')
        if self.log:
            with self.log.open('a', encoding='utf-8') as stream:
                stream.write('> ' + text + '\n')
        result = queue.Queue()
        def exchange():
            try:
                self.to.write(text + '\n')
                self.to.flush()
                lines = []
                while True:
                    line = self.from_.readline()
                    if not line:
                        raise RuntimeError('Audacity pipe closed')
                    lines.append(line)
                    if 'BatchCommand finished:' in line:
                        # Consume response terminator so the next command starts cleanly.
                        self.from_.readline()
                        break
                answer = ''.join(lines)
                if 'BatchCommand finished: OK' not in answer:
                    raise RuntimeError(answer)
                result.put((True, answer))
            except Exception as error:
                result.put((False, error))
        threading.Thread(target=exchange, daemon=True).start()
        try:
            ok, answer = result.get(timeout=self.timeout)
        except queue.Empty:
            self.broken = True
            raise TimeoutError('Audacity did not answer. Project left open; inspect it before retrying.')
        if not ok:
            raise answer
        if self.log:
            with self.log.open('a', encoding='utf-8') as stream:
                stream.write(answer + '\n')
        return answer

    @staticmethod
    def quoted(path):
        value = str(Path(path).resolve()).replace('\\', '/')
        if any(c in value for c in '"\r\n'):
            raise ValueError('Unsupported scripting filename')
        return '"' + value + '"'

    def project(self, stems, destination):
        if destination.exists():
            raise FileExistsError(destination)
        self.command('New:')
        self.command('SetProject: Rate=48000')
        # The project is now owned by this invocation. On any failure leave it intact.
        # Effect stems must never be tempo-matched. Restore the exact previous
        # preferences in finally, and retain their values in the command log.
        names = ['/FileFormats/MusicFileImportSettingChoice',
                 '/FileFormats/MusicFileImportOnExistingProjectSettingChoice']
        previous = {}
        try:
            for name, value in zip(names, ('No','DoNothing')):
                response = self.command(f'GetPreference: Name="{name}"')
                previous[name] = response.split('BatchCommand finished:')[0].strip().strip('"')
                if previous[name] not in {'Yes','Ask','No','Match','MatchIfBeatsAndMeasures','DoNothing'}:
                    raise ValueError('Unexpected Audacity music import preference')
                self.command(f'SetPreference: Name="{name}" Value="{value}" Reload=0')
            for stem in stems:
                self.command('Import2: Filename=' + self.quoted(stem))
        finally:
            for name, value in previous.items():
                self.command(f'SetPreference: Name="{name}" Value="{value}" Reload=0')
        tracks = self.command('GetInfo: Type=Tracks')
        tracks = json.loads(tracks[:tracks.index('BatchCommand finished:')])
        if len(tracks) != len(stems) or any(t['name'] != s.stem for t, s in zip(tracks, stems)):
            raise ValueError('Audacity track organization differs from lossless stems')
        self.command('SelectAll:')
        self.command('Export2: Filename=' + self.quoted(destination.parent / 'project-render.wav') + ' NumChannels=1')
        self.command('SaveProject2: Filename=' + self.quoted(destination))
        return {"saved": True, "closed": False, "tracks": tracks}

    def capture(self, source, raw_render, destination):
        # Only saved, closed owned projects are accepted; opening a new project does
        # not replace the active user project or commit the user's unsaved work.
        ensure_closed(source)
        # Work on a closed-project copy, never mutate a historical revision.
        closed_backup(source, destination)
        self.command('OpenProject2: Filename=' + self.quoted(destination))
        self.command('SelectAll:')
        self.command('Export2: Filename=' + self.quoted(raw_render) + ' NumChannels=1')
        self.command('Save:')
        return {"saved": True, "closed": False}

def ensure_closed(path):
    with closed_reader(path):
        pass

@contextlib.contextmanager
def closed_reader(path):
    path = Path(path)
    for suffix in ('-wal', '-shm', '-journal'):
        if Path(str(path) + suffix).exists():
            raise RuntimeError(f'Project has a database sidecar; close it safely first: {path}')
    # An exclusive Windows handle detects an open DAW database, even without WAL.
    kernel = ctypes.WinDLL('kernel32', use_last_error=True)
    kernel.CreateFileW.restype = ctypes.c_void_p
    kernel.CreateFileW.argtypes = [ctypes.c_wchar_p, ctypes.c_uint32, ctypes.c_uint32,
                                  ctypes.c_void_p, ctypes.c_uint32, ctypes.c_uint32, ctypes.c_void_p]
    handle = kernel.CreateFileW(str(path), 0x80000000, 0, None, 3, 0, None)
    if handle == ctypes.c_void_p(-1).value:
        raise RuntimeError(f'Cannot exclusively read closed project: {path}')
    import msvcrt
    # Transfer ownership to Python while retaining share=0 for the entire copy.
    fd = msvcrt.open_osfhandle(handle, os.O_RDONLY | os.O_BINARY)
    with os.fdopen(fd, 'rb') as stream:
        yield stream

def closed_backup(project, backup):
    for attempt in range(40):
        try:
            ensure_closed(project)
            break
        except RuntimeError:
            if attempt == 39:
                raise
            time.sleep(0.1)
    if backup.exists():
        raise FileExistsError(backup)
    with closed_reader(project) as source, backup.open('xb') as target:
        shutil.copyfileobj(source, target)
    with contextlib.closing(sqlite3.connect(backup.as_uri() + '?mode=ro&immutable=1', uri=True)) as copied:
        if copied.execute('PRAGMA integrity_check').fetchone()[0] != 'ok':
            raise ValueError('Invalid Audacity database')
    # Reacquire exclusive access so an immediately reopened project cannot be
    # declared safely closed after the snapshot.
    with closed_reader(project) as source:
        project_hash = hashlib.sha256(source.read()).hexdigest()
        if project_hash != digest(backup):
            raise ValueError('Closed project backup hash mismatch')
    return {"projectSha256": project_hash, "backupSha256": digest(backup), "closed": True}
