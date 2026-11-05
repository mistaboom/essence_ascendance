"""Objective QC and gapless decoded repeat fixtures, separate from listening approval."""
import numpy as np
from numbers import Integral, Real
import encoding
from synthesis import wav


def decode(path):
    raw = encoding.run(['-v', 'error', '-xerror', '-nostdin', '-i', path,
                        '-ac', '1', '-ar', '48000', '-f', 'f32le', '-']).stdout
    return np.frombuffer(raw, dtype='<f4').astype(float)


def inspect(path, loop=False):
    x = decode(path)
    rate = 48000
    active = np.flatnonzero(np.abs(x) > 10**(-60/20))
    # Whole-clip Hann would underweight the onset of a transient. Use Parseval
    # power over all decoded samples; these are screening metrics, not hearing.
    spectrum = np.abs(np.fft.rfft(x))**2
    hz = np.fft.rfftfreq(len(x), 1/rate)
    total = max(float(spectrum.sum()), 1e-30)
    frame = round(rate * .01)
    rms = [float(np.sqrt(np.mean(x[i:i+frame]**2))) for i in range(0, len(x), frame)]
    report = {'spectralMethod':'full decoded clip power; no onset-weighting window',
              'leadingBelowMinus60DbMs': float(active[0]/rate*1000) if len(active) else len(x)/rate*1000,
              'trailingBelowMinus60DbMs': float((len(x)-1-active[-1])/rate*1000) if len(active) else len(x)/rate*1000,
              'edgeAmplitude': float(max(abs(x[0]), abs(x[-1]))),
              'maximumAdjacentDelta': float(np.max(np.abs(np.diff(x)))),
              'energy2To5kFraction': float(spectrum[(hz>=2000)&(hz<5000)].sum()/total),
              'energyAbove6kFraction': float(spectrum[hz>=6000].sum()/total),
              'quiet10msWindowsBelowMinus60Db': sum(v < .001 for v in rms),
              'listening': 'pending human timbre, fatigue, repetition and native mix review'}
    if loop:
        repeated = np.tile(x, 8)
        edges = np.arange(1, 8)*len(x)
        window = round(.01*rate)
        reference = float(np.sqrt(np.mean(x**2)))
        ratios = [float(np.sqrt(np.mean(repeated[e-window:e+window]**2))/reference) for e in edges]
        report['decodedLoop'] = {'repeats':8, 'joinsTested':7,
            'periodFrames':len(x), 'totalFrames':len(repeated),
            'seamDelta':float(abs(x[-1]-x[0])),
            'join10msRmsRatio': ratios,
            'minimum10msRmsRatio':min(rms)/reference,
            'gapOrClickFlag':bool(abs(x[-1]-x[0])>.01 or min(ratios)<.5 or min(rms)/reference<.25),
            'perceptualLoopReview':'pending; numeric seams do not establish audibility'}
    return report


def repeated_preview(source, destination, loop=False, count=None, interval=None):
    x = decode(source)
    if loop:
        result = np.tile(x, count or 8)
    else:
        # Preserve full tails and source level; intentional review spacing is not QC silence.
        spacing = .18 if interval is None else max(0, interval-len(x)/48000)
        result = np.tile(np.r_[x, np.zeros(round(spacing*48000))], count or 8)
    wav(destination, result, 48000)


def overlapping_preview(source, destination, interval, count=8):
    """Sum complete decoded tails at scheduled onsets, without level changes."""
    rate = 48000
    if (isinstance(interval, bool) or not isinstance(interval, Real)
            or not np.isfinite(interval) or interval <= 0):
        raise ValueError('Overlap interval must be finite and positive')
    if isinstance(count, bool) or not isinstance(count, Integral) or count < 2:
        raise ValueError('Overlap count must be an integer of at least 2')
    spacing = float(interval) * rate
    if not np.isfinite(spacing) or spacing < 1 or not np.isfinite((count - 1) * spacing):
        raise ValueError('Overlap timing must be finite and at least one audio frame apart')
    # Round each absolute onset, avoiding cumulative drift for fractional frames.
    onsets = [round(index * spacing) for index in range(count)]
    x = decode(source)
    if x.ndim != 1 or not len(x) or not np.all(np.isfinite(x)):
        raise ValueError('Overlap source must contain finite, nonempty mono audio')
    result = np.zeros(onsets[-1] + len(x))
    for onset in onsets:
        result[onset:onset + len(x)] += x
    peak = float(np.max(np.abs(result)))
    if not np.all(np.isfinite(result)) or peak >= 1:
        raise ValueError(f'Overlapping preview clips at unscaled peak {peak:.6g}; no output written')
    wav(destination, result, rate)
    return {'onsetSeconds': [onset / rate for onset in onsets],
            'onsetFrames': onsets, 'frameCount': len(result), 'sourceFrames': len(x),
            'unscaledPeak': peak, 'intervalSeconds': float(interval),
            'count': int(count), 'sampleRate': rate}


def loop_audition(source, destination, periods=2, fade_seconds=.65):
    """Demonstrate lifecycle fades without changing the exported loop or its joins."""
    result = np.tile(decode(source), periods)
    count = min(round(fade_seconds * 48000), len(result) // 2)
    ramp = np.sin(np.linspace(0, np.pi / 2, count))**2
    result[:count] *= ramp
    result[-count:] *= ramp[::-1]
    wav(destination, result, 48000)


def comparison(sources, destination, gap=.45, repeats=1, interval=None):
    """Level-preserving ordered comparison, with exact segment index."""
    parts, index, cursor = [], [], 0
    for _ in range(repeats):
        for asset, source in sources:
            x=decode(source)
            index.append({'asset':asset,'startSeconds':cursor/48000,'duration':len(x)/48000})
            spacing = gap if interval is None else max(0,interval-len(x)/48000)
            part=np.r_[x,np.zeros(round(spacing*48000))]
            parts.append(part);cursor+=len(part)
    wav(destination,np.concatenate(parts),48000)
    return index
