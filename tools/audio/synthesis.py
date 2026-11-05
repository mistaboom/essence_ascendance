"""Recipe-independent DSP. Every layer is retained as an editable project track."""
import math
import wave
import numpy as np

def keys(value, allowed):
    unknown = set(value) - set(allowed.split())
    if unknown:
        raise ValueError(f'Unknown recipe fields: {sorted(unknown)}')

def bounded(value, low, high):
    if not isinstance(value, (int, float)) or not math.isfinite(value) or not low <= value <= high:
        raise ValueError(f'Recipe number outside {low}..{high}: {value}')

def validate(recipe):
    keys(recipe, 'schema assetId seed sampleRate duration peakDb layers loop design')
    if recipe['schema'] != 1 or not isinstance(recipe['seed'], int) or recipe['seed'] < 0:
        raise ValueError('Unsupported recipe schema or seed')
    bounded(recipe.get('peakDb', -6), -60, -1)
    for layer in recipe['layers']:
        keys(layer, 'name type frequency frequencyEnd frequencyPoints harmonics variationCents gain fm am envelope lowHz highHz startSeconds lengthSeconds seed partials decaySeconds phase spectralTiltDbOct lowHzEnd highHzEnd events')
        if 'events' in layer:
            if recipe.get('loop') or 'startSeconds' in layer or 'lengthSeconds' in layer:
                raise ValueError('Scored layers use event timing and cannot be loops')
            if not isinstance(layer['events'], list) or not 1 <= len(layer['events']) <= 256:
                raise ValueError('Scored layers need 1..256 events')
            for event in layer['events']:
                keys(event, 'startSeconds lengthSeconds frequency frequencyEnd frequencyPoints gain seed envelope harmonics partials decaySeconds phase fm am lowHz highHz spectralTiltDbOct lowHzEnd highHzEnd variationCents')
                if 'startSeconds' not in event or 'lengthSeconds' not in event:
                    raise ValueError('Each score event needs explicit start and length')
        bounded(layer['gain'], -1, 1)
        bounded(layer.get('variationCents', 0), 0, 100)
        bounded(layer.get('phase', 0), -1e6, 1e6)
        if 'decaySeconds' in layer:
            bounded(layer['decaySeconds'], 0.001, 120)
        if 'spectralTiltDbOct' in layer:
            bounded(layer['spectralTiltDbOct'], -24, 12)
        if 'frequencyPoints' in layer:
            points = np.asarray(layer['frequencyPoints'], dtype=float)
            if (points.ndim != 2 or points.shape[1] != 2 or len(points) < 2
                    or not np.all(np.isfinite(points)) or points[0, 0] != 0
                    or points[-1, 0] != 1 or np.any(np.diff(points[:, 0]) <= 0)
                    or np.any(points[:, 1] <= 0) or np.any(points[:, 1] >= 24000)):
                raise ValueError('Pitch points need increasing normalized times 0..1 and positive Hz below Nyquist')
            if 'frequencyEnd' in layer:
                raise ValueError('Use either pitch points or a linear frequencyEnd')
        if layer.get('type') == 'modal':
            if not 1 <= len(layer.get('partials', [])) <= 24:
                raise ValueError('Modal layers need 1..24 partials')
            for partial in layer['partials']:
                keys(partial, 'ratio gain decaySeconds phase')
                bounded(partial['ratio'], 0.01, 64)
                bounded(partial['gain'], -1, 1)
                bounded(partial.get('phase', 0), -1e6, 1e6)
                if 'decaySeconds' in partial:
                    bounded(partial['decaySeconds'], 0.001, 120)
        for modulation in ('am', 'fm'):
            if modulation in layer:
                keys(layer[modulation], 'rate depth')
                bounded(layer[modulation]['rate'], 0, 100)
                bounded(layer[modulation]['depth'], 0, 1 if modulation == 'am' else 10)
        if 'harmonics' in layer:
            if not 1 <= len(layer['harmonics']) <= 16:
                raise ValueError('Expected 1..16 harmonic amplitudes')
            for amplitude in layer['harmonics']:
                bounded(amplitude, -1, 1)
    if recipe.get('loop'):
        keys(recipe['loop'], 'crossfadeSeconds mode')
        if recipe['loop'].get('mode', 'overlap') not in {'overlap', 'periodic'}:
            raise ValueError('Unknown loop mode')
        if recipe['loop'].get('mode') != 'periodic':
            bounded(recipe['loop']['crossfadeSeconds'], 0.001, recipe['duration'] / 2 - 0.001)

def envelope(points, size):
    points = np.asarray(points, dtype=float)
    if points.ndim != 2 or points.shape[1] != 2 or len(points) < 2:
        raise ValueError("Envelope needs [normalized time, amplitude] points")
    if points[0, 0] != 0 or points[-1, 0] != 1 or np.any(np.diff(points[:, 0]) <= 0):
        raise ValueError("Envelope times must increase from 0 to 1")
    if not np.all(np.isfinite(points)) or np.any(points[:, 1] < 0) or np.any(points[:, 1] > 1):
        raise ValueError('Envelope amplitudes must be finite and in 0..1')
    return np.interp(np.linspace(0, 1, size), points[:, 0], points[:, 1])

def filtered_noise(rng, size, rate, low=0, high=20000, tilt=0):
    if not 0 <= low < high <= rate / 2:
        raise ValueError("Invalid noise passband")
    spectrum = np.fft.rfft(rng.normal(size=size))
    frequencies = np.fft.rfftfreq(size, 1 / rate)
    spectrum[(frequencies < low) | (frequencies > high)] = 0
    if tilt:
        spectrum *= np.power(np.maximum(frequencies, 20) / 1000, tilt / 6.020599913)
    signal = np.fft.irfft(spectrum, n=size)
    return signal / max(float(np.max(np.abs(signal))), 1e-12)

def loop_crossfade(signal, samples):
    if not 1 < samples < len(signal) // 2:
        raise ValueError("Loop crossfade must be shorter than half the signal")
    # Overlap tail with head; resulting period is shorter by the overlap.
    blend = np.linspace(0, 1, samples)
    joint = signal[-samples:] * (1 - blend) + signal[:samples] * blend
    return np.concatenate((signal[samples:-samples], joint))

def render(recipe, _normalize=True):
    validate(recipe)
    rate, duration = recipe["sampleRate"], recipe["duration"]
    if rate != 48000 or not 0.02 <= duration <= 30:
        raise ValueError("Authoring supports 48 kHz, 0.02..30 seconds")
    rng = np.random.default_rng(recipe["seed"])
    size = round(rate * duration)
    t = np.arange(size) / rate
    stems = {}
    if not 1 <= len(recipe["layers"]) <= 16:
        raise ValueError("Recipe needs 1..16 layers")
    for layer in recipe["layers"]:
        name = layer["name"]
        if name in stems or not name.replace('_', '').isalnum():
            raise ValueError("Unique simple stem names required")
        if 'events' in layer:
            # A score is data on one instrument stem. Each event uses the same
            # shared synthesis/envelope path; normalize only the final whole mix.
            base = {key:value for key,value in layer.items() if key != 'events'}
            signal = np.zeros(size)
            for index, event in enumerate(layer['events']):
                voice = {**base, **event}
                voice.setdefault('seed', int(rng.integers(0, 2**63)))
                if 'seed' in base and 'seed' not in event:
                    voice['seed'] = (base['seed'] + index * 104729) % 2**63
                child = {'schema':1, 'assetId':recipe['assetId'], 'seed':recipe['seed'],
                         'sampleRate':rate, 'duration':duration, 'peakDb':recipe.get('peakDb', -6),
                         'layers':[voice]}
                _, parts = render(child, _normalize=False)
                signal += parts[name]
            stems[name] = signal
            continue
        start = layer.get('startSeconds', 0)
        length = layer.get('lengthSeconds', duration - start)
        bounded(start, 0, duration - 0.001)
        bounded(length, 0.001, duration - start + 1e-9)
        count, offset = round(length * rate), round(start * rate)
        local_t = np.arange(count) / rate
        periodic = recipe.get('loop', {}).get('mode') == 'periodic'
        if periodic and (start != 0 or length != duration or any(key in layer for key in
                        ('frequencyEnd', 'frequencyPoints', 'lowHzEnd', 'highHzEnd', 'decaySeconds'))):
            raise ValueError('Periodic layers must span the complete period without sweeps')
        layer_rng = np.random.default_rng(layer['seed']) if 'seed' in layer else rng
        if layer["type"] in {"tone", "modal"}:
            variation = layer_rng.uniform(-layer.get("variationCents", 0), layer.get("variationCents", 0))
            frequency = layer["frequency"] * 2 ** (variation / 1200)
            end = layer.get('frequencyEnd', layer['frequency']) * 2 ** (variation / 1200)
            if periodic:
                frequency = max(1, round(frequency * duration)) / duration
            pitch_values = [frequency, end]
            if 'frequencyPoints' in layer:
                pitch_values += [point[1] * 2 ** (variation / 1200) for point in layer['frequencyPoints']]
            highest_ratio = (max(partial['ratio'] for partial in layer['partials'])
                             if layer['type'] == 'modal' else len(layer.get('harmonics', [1])))
            if not 0 < min(pitch_values) or max(pitch_values) * highest_ratio >= rate / 2:
                raise ValueError("Tone outside Nyquist range")
            fm = dict(layer.get("fm", {"rate": 0, "depth": 0}))
            if periodic:
                fm['rate'] = round(fm['rate'] * duration) / duration
            if 'frequencyPoints' in layer:
                points = np.asarray(layer['frequencyPoints'], dtype=float)
                hz = np.interp(local_t / length, points[:, 0], points[:, 1]) * 2 ** (variation / 1200)
                phase = 2 * np.pi * np.concatenate(([0.0], np.cumsum((hz[:-1] + hz[1:]) / (2 * rate))))
            elif 'frequencyEnd' in layer:
                phase = 2 * np.pi * (frequency * local_t + (end-frequency) * local_t**2 / (2*length))
            else:
                phase = 2 * np.pi * frequency * local_t
            phase += fm["depth"] * np.sin(2 * np.pi * fm["rate"] * local_t)
            if layer['type'] == 'modal':
                signal = np.zeros(count)
                for partial in layer['partials']:
                    if periodic and 'decaySeconds' in partial:
                        raise ValueError('Periodic modal partials cannot decay')
                    # Independent grid quantization keeps inharmonic chime spectra periodic.
                    modal_phase = (2 * np.pi * max(1, round(frequency * partial['ratio'] * duration)) / duration * local_t
                                   if periodic else phase * partial['ratio'])
                    if periodic:
                        modal_phase += fm['depth'] * np.sin(2 * np.pi * fm['rate'] * local_t) * partial['ratio']
                    modal_phase += partial.get('phase', 0) + layer.get('phase', 0)
                    voice = partial['gain'] * np.sin(modal_phase)
                    decay = partial.get('decaySeconds', layer.get('decaySeconds'))
                    if decay is not None:
                        voice *= np.exp(-local_t / decay)
                    signal += voice
            else:
                signal = sum(amplitude * np.sin(phase * (i + 1) + layer.get('phase', 0))
                             for i, amplitude in enumerate(layer.get("harmonics", [1])))
                if 'decaySeconds' in layer:
                    signal *= np.exp(-local_t / layer['decaySeconds'])
        elif layer["type"] == "noise":
            noise_state = layer_rng.bit_generator.state
            signal = filtered_noise(layer_rng, count, rate, layer["lowHz"], layer["highHz"], layer.get('spectralTiltDbOct', 0))
            if 'lowHzEnd' in layer or 'highHzEnd' in layer:
                # Correlated filter morph: no independent random stream or combing at the crossfade.
                layer_rng.bit_generator.state = noise_state
                tail = filtered_noise(layer_rng, count, rate, layer.get('lowHzEnd', layer['lowHz']),
                                      layer.get('highHzEnd', layer['highHz']), layer.get('spectralTiltDbOct', 0))
                blend = (1 - np.cos(np.linspace(0, np.pi, count))) / 2
                signal = signal * (1 - blend) + tail * blend
        else:
            raise ValueError(f"Unknown layer type: {layer['type']}")
        am = dict(layer.get("am", {"rate": 0, "depth": 0}))
        if periodic:
            am['rate'] = round(am['rate'] * duration) / duration
        if not 0 <= am["depth"] <= 1:
            raise ValueError("AM depth outside 0..1")
        signal *= 1 - am["depth"] / 2 + am["depth"] / 2 * np.cos(2 * np.pi * am["rate"] * local_t)
        signal *= envelope(layer["envelope"], count) * layer["gain"]
        if periodic and layer['envelope'][0][1] != layer['envelope'][-1][1]:
            raise ValueError('Periodic envelopes must meet')
        if start or length != duration:
            signal = np.pad(signal, (offset, size-offset-count))
        if recipe.get("loop") and not periodic:
            signal = loop_crossfade(signal, round(recipe["loop"]["crossfadeSeconds"] * rate))
        stems[name] = signal
    mix = sum(stems.values())
    peak = float(np.max(np.abs(mix)))
    if not math.isfinite(peak) or peak <= 0:
        raise ValueError("Silent or non-finite synthesis")
    gain = min(1, 10 ** (recipe.get("peakDb", -6) / 20) / peak) if _normalize else 1
    return rate, {name: signal * gain for name, signal in stems.items()}

def wav(path, signal, rate):
    path.parent.mkdir(parents=True, exist_ok=True)
    if not np.all(np.isfinite(signal)) or np.max(np.abs(signal)) >= 1:
        raise ValueError("Non-finite or clipping lossless source")
    # PCM 24-bit master and stems, deterministic and usable without a Python decoder.
    pcm = np.rint(signal * 8388607).astype('<i4').view(np.uint8).reshape(-1, 4)[:, :3].tobytes()
    with wave.open(str(path), 'wb') as out:
        out.setnchannels(1)
        out.setsampwidth(3)
        out.setframerate(rate)
        out.writeframes(pcm)
