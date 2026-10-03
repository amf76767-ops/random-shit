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

sounds = {
    # kurzes, weiches Tick
    "click": norm(fade(tone(1900, 0.05, (1.0, 0.25), glide=1500))),
    # zwei steigende Töne
    "toggle_on": norm(fade(np.concatenate([tone(660, 0.07), tone(990, 0.11)]))),
    # zwei fallende Töne
    "toggle_off": norm(fade(np.concatenate([tone(880, 0.07), tone(587, 0.11)]))),
}
(OUT / "sounds/ui").mkdir(parents=True, exist_ok=True)
for name, data in sounds.items():
    sf.write(OUT / f"sounds/ui/{name}.ogg", data, SR, format="OGG", subtype="VORBIS")
events = {f"ui.{n}": {"sounds": [f"dihclient:ui/{n}"]} for n in sounds}
(OUT / "sounds.json").write_text(json.dumps(events, indent=2))
print("OK", [f"{n}: {len(d) / SR * 1000:.0f} ms" for n, d in sounds.items()])
