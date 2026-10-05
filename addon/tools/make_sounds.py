#!/usr/bin/env python3
"""Erzeugt die GUI-Sounds (OGG Vorbis, mono) im Code. Braucht: pip install numpy soundfile"""
import json
from pathlib import Path
import numpy as np
import soundfile as sf

SR = 44100
OUT = Path(__file__).resolve().parent.parent / "src/resources/assets/dihclient"

def env(n, attack=0.004, release=0.9):
    t = np.arange(n) / SR
    a = np.minimum(1.0, t / attack)
    return a * np.exp(-t * (6.0 / (n / SR * release)))

def tone(freq, dur, harm=(1.0, 0.35, 0.12), glide=None):
    n = int(SR * dur)
    t = np.arange(n) / SR
    f = np.full(n, float(freq)) if glide is None else np.linspace(freq, glide, n)
    phase = 2 * np.pi * np.cumsum(f) / SR
    wave = sum(h * np.sin(phase * (k + 1)) for k, h in enumerate(harm))
    return wave * env(n)

def norm(x, peak=0.55):
    return (x / max(1e-9, np.max(np.abs(x))) * peak).astype(np.float32)

def fade(x, ms=3):
    k = int(SR * ms / 1000)
    x = x.copy(); x[-k:] *= np.linspace(1, 0, k); return x

def bell(freqs, amps, decays, dur, attack=0.0006, start=0.0):
    n = int(SR * dur)
    t = np.arange(n) / SR
    x = np.zeros(n)
    for f, a, d in zip(freqs, amps, decays):
        x += a * np.sin(2 * np.pi * f * t) * np.exp(-t * d)
    x *= np.minimum(1.0, t / attack)
    if start > 0:
        x = np.concatenate([np.zeros(int(SR * start)), x])
    return x

def glass_note(f, dur=0.34, start=0.0):
    return bell([f, f * 2.76, f * 5.40, f * 8.93], [1.0, 0.38, 0.14, 0.05], [11, 22, 38, 60], dur, start=start)

def mix(*parts):
    n = max(len(p) for p in parts)
    out = np.zeros(n)
    for p in parts:
        out[:len(p)] += p
    return out

rng = np.random.default_rng(7)
tick_noise = rng.standard_normal(int(SR * 0.004)) * np.exp(-np.arange(int(SR * 0.004)) / (SR * 0.0008))

sounds = {
    "glass_click": norm(fade(mix(bell([3300, 5200, 7600], [1.0, 0.5, 0.22], [95, 130, 170], 0.1), np.concatenate([tick_noise * 0.25, np.zeros(10)])), 6), 0.5),
    "glass_on": norm(fade(mix(glass_note(1318.5, 0.42), glass_note(1975.5, 0.42, start=0.065)), 25), 0.55),
    "glass_off": norm(fade(mix(glass_note(1046.5, 0.3), glass_note(698.5, 0.3, start=0.055)), 20), 0.45),
    "click": norm(fade(tone(1900, 0.05, (1.0, 0.25), glide=1500))),
    "toggle_on": norm(fade(np.concatenate([tone(660, 0.07), tone(990, 0.11)]))),
    "toggle_off": norm(fade(np.concatenate([tone(880, 0.07), tone(587, 0.11)]))),
}
(OUT / "sounds/ui").mkdir(parents=True, exist_ok=True)
for name, data in sounds.items():
    sf.write(OUT / f"sounds/ui/{name}.ogg", data, SR, format="OGG", subtype="VORBIS")
events = {f"ui.{n}": {"sounds": [f"dihclient:ui/{n}"]} for n in sounds}
(OUT / "sounds.json").write_text(json.dumps(events, indent=2))
print("OK", [f"{n}: {len(d) / SR * 1000:.0f} ms" for n, d in sounds.items()])
