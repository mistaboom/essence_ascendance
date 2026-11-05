"""One encoder and content validator for authored, revised and promoted takes."""
import json
import math
import re
import subprocess
import numpy as np
from storage import digest

def binary():
    import imageio_ffmpeg
    return imageio_ffmpeg.get_ffmpeg_exe()

def run(args):
    result = subprocess.run([binary(), *map(str, args)], capture_output=True, timeout=60)
    if result.returncode:
        raise ValueError(result.stderr.decode(errors="replace"))
    return result

def encode(master, output):
    options = ['-map_metadata', '-1', '-c:a', 'libvorbis', '-q:a', '5', '-ar', '48000', '-ac', '1',
               '-fflags', '+bitexact', '-flags:a', '+bitexact']
    run(['-v', 'error', '-nostdin', '-n', '-i', master, *options, output])
    return {"version": run(['-version']).stdout.decode().splitlines()[0],
            "sha256": digest(binary()), "options": options}

def lossless_master(source, output):
    # Audacity's Export2 format options come from preferences. Preserve its raw
    # WAV render and standardize through this shared export owner, never global DAW prefs.
    report = run(['-hide_banner', '-nostdin', '-i', source, '-f', 'null', '-']).stderr.decode()
    if not re.search(r'Audio: pcm_(s16le|s24le|s32le|f32le|f64le)[, ]', report):
        raise ValueError('Audacity render must contain lossless PCM audio')
    run(['-v', 'error', '-nostdin', '-n', '-i', source, '-map_metadata', '-1',
         '-ar', '48000', '-ac', '1', '-c:a', 'pcm_s24le', output])

def validate(path, *, vorbis=True, scope="world", expected_duration=None, loop=False):
    probe = run(['-hide_banner', '-nostdin', '-i', path, '-f', 'null', '-'])
    info = probe.stderr.decode(errors='replace')
    if info.split('Stream mapping:')[0].count('Audio:') != 1:
        raise ValueError('Exactly one audio stream required')
    codec = re.search(r'Audio: ([^, ]+).*?, (\d+) Hz, ([^,\r\n]+)', info)
    if not codec:
        raise ValueError("Missing audio stream")
    name, rate, layout = codec.groups()
    if not vorbis and name not in {'pcm_s16le', 'pcm_s24le', 'pcm_s32le', 'pcm_f32le', 'pcm_f64le', 'flac'}:
        raise ValueError('A lossless PCM/FLAC master is required')
    channels = {"mono": 1, "stereo": 2}.get(layout)
    if (vorbis and (name != 'vorbis' or not path.read_bytes().startswith(b'OggS'))) or channels is None:
        raise ValueError("Expected actual Ogg Vorbis mono/stereo content")
    if int(rate) != 48000 or (scope == "world" and channels != 1):
        raise ValueError("Expected 48 kHz; positional sounds must be mono")
    raw = run(['-v', 'error', '-xerror', '-err_detect', 'explode', '-nostdin', '-i', path,
               '-f', 'f32le', '-c:a', 'pcm_f32le', '-']).stdout
    samples = np.frombuffer(raw, dtype='<f4').reshape(-1, channels)
    if not len(samples) or not np.all(np.isfinite(samples)):
        raise ValueError("Empty, non-finite or undecodable audio")
    duration = len(samples) / int(rate)
    peak = float(np.max(np.abs(samples)))
    rms = float(np.sqrt(np.mean(samples.astype(float) ** 2)))
    if not 0.02 <= duration <= 30 or rms <= 1e-6:
        raise ValueError("Invalid duration or silent content")
    if expected_duration is not None and abs(duration - expected_duration) > 0.03:
        raise ValueError("Unexpected decoded duration")
    stats = run(['-hide_banner', '-nostdin', '-i', path, '-af',
                 'apad=whole_dur=0.4,loudnorm=I=-23:TP=-1:LRA=7:print_format=json', '-f', 'null', '-']).stderr.decode()
    measurement = json.loads(stats[stats.rfind('{'):stats.rfind('}') + 1])
    true_peak = float(measurement['input_tp'])
    loudness = float(measurement['input_i'])
    if not math.isfinite(true_peak) or not math.isfinite(loudness) or true_peak > -1 or peak > 10 ** (-1 / 20):
        raise ValueError(f"True peak {true_peak} exceeds -1 dBTP")
    result = {"codec": name, "sampleRate": int(rate), "channels": channels, "duration": duration,
              "samplePeakDb": 20 * math.log10(peak), "rmsDb": 20 * math.log10(rms),
              "truePeakDb": true_peak, "integratedLufs": loudness, "decodedFrames": len(samples)}
    if duration < 0.4:
        result['loudnessMethod'] = 'zero_padded_to_400ms'
    if loop:
        result["seamDelta"] = float(np.max(np.abs(samples[0] - samples[-1])))
        # Reject gross discontinuities; listening is still an explicit approval gate.
        if result["seamDelta"] > 0.02:
            raise ValueError("Loop seam discontinuity exceeds 0.02")
    return result
