"""Regenerate every bundled Evernight sound from code.

All interface sounds are synthesised here with numpy; the killstreak announcer lines are rendered by
scripts/audio/render_tts.ps1 with the Windows speech synthesiser and encoded by this script. Neither
step uses third-party recordings, so the output can ship under the project licence.

Usage:
    python scripts/audio/generate.py            # interface sounds only
    python scripts/audio/generate.py --tts-dir build/audio-tts   # also encode announcer lines
"""

from __future__ import annotations

import argparse
from pathlib import Path

import numpy as np
import soundfile as sf

ROOT = Path(__file__).resolve().parents[2]
SOUNDS = ROOT / "src/main/resources/assets/evernight/sounds"
KILLSTREAKS = ROOT / "src/main/resources/assets/evernight/offline/killstreaks"
SAMPLE_RATE = 48000

# Names that belonged to third-party products; the audio and the identifiers were both replaced.
LEGACY_NAMES = (
    "kaban", "neverlose", "css", "cod", "quake", "hl2",
    "steam", "whatsapp", "vk", "icq", "stalker",
)

KILLSTREAK_LINES = (
    ("doublekill_2/doublekill.ogg", "Double kill"),
    ("triplekill_3/triplekill.ogg", "Triple kill"),
    ("triplekill_3/multikill.ogg", "Multi kill"),
    ("dominating_4/dominating.ogg", "Dominating"),
    ("dominating_4/killingspree.ogg", "Killing spree"),
    ("dominating_4/headhunter.ogg", "Head hunter"),
    ("megakill_5/megakill.ogg", "Mega kill"),
    ("megakill_5/impressive.ogg", "Impressive"),
    ("megakill_5/humiliation.ogg", "Humiliation"),
    ("unstoppable_6/unstoppable.ogg", "Unstoppable"),
    ("unstoppable_6/rampage.ogg", "Rampage"),
    ("wickedsick_7/wickedsick.ogg", "Wicked sick"),
    ("wickedsick_7/ultrakill.ogg", "Ultra kill"),
    ("wickedsick_7/ludicrouskill.ogg", "Ludicrous kill"),
    ("monsterkill_8up/monsterkill.ogg", "Monster kill"),
    ("monsterkill_8up/godlike.ogg", "Godlike"),
    ("monsterkill_8up/perfect.ogg", "Perfect"),
)


def timeline(seconds: float) -> np.ndarray:
    return np.arange(int(SAMPLE_RATE * seconds)) / SAMPLE_RATE


def tone(freq: float, seconds: float, tau: float | None = None, harmonics: int = 1) -> np.ndarray:
    x = timeline(seconds)
    wave = np.zeros_like(x)
    for index in range(1, harmonics + 1):
        wave += np.sin(2 * np.pi * freq * index * x) / index
    if tau is not None:
        wave *= np.exp(-x / tau)
    return wave


def sweep(start: float, end: float, seconds: float, tau: float) -> np.ndarray:
    x = timeline(seconds)
    freq = np.linspace(start, end, len(x))
    return np.sin(2 * np.pi * np.cumsum(freq) / SAMPLE_RATE) * np.exp(-x / tau)


def noise(seconds: float, seed: int = 7) -> np.ndarray:
    return np.random.default_rng(seed).uniform(-1.0, 1.0, int(SAMPLE_RATE * seconds))


def lowpass(samples: np.ndarray, alpha: float) -> np.ndarray:
    out = np.empty_like(samples)
    acc = 0.0
    for index, value in enumerate(samples):
        acc += alpha * (value - acc)
        out[index] = acc
    return out


def highpass(samples: np.ndarray, alpha: float) -> np.ndarray:
    return samples - lowpass(samples, alpha)


def silence(seconds: float) -> np.ndarray:
    return np.zeros(int(SAMPLE_RATE * seconds))


def sequence(*parts: np.ndarray) -> np.ndarray:
    return np.concatenate(parts)


def interface_sounds() -> dict[str, np.ndarray]:
    click = highpass(noise(0.03, 11), 0.55) * np.exp(-timeline(0.03) / 0.004)
    click = click + tone(2100, 0.03, tau=0.006) * 0.35
    right_click = highpass(noise(0.045, 12), 0.4) * np.exp(-timeline(0.045) / 0.006)
    right_click = right_click + tone(1350, 0.045, tau=0.01) * 0.4
    hover = tone(1620, 0.028, tau=0.007) * 0.5
    blip = tone(880, 0.11, tau=0.045)
    blip2 = sequence(tone(660, 0.06, tau=0.03), tone(990, 0.08, tau=0.04))
    pickup = sequence(*(tone(freq, 0.055, tau=0.035) for freq in (880, 1100, 1320)))
    pickup2 = sequence(*(tone(freq, 0.045, tau=0.03) for freq in (660, 880, 1100, 1320, 1568)))
    beep_a = tone(440, 0.12, tau=0.09, harmonics=3)
    warning = sequence(beep_a, silence(0.05), beep_a)
    thump = sweep(140, 55, 0.22, tau=0.09)
    splat = lowpass(noise(0.22, 13), 0.06) * np.exp(-timeline(0.22) / 0.07)
    bodysplat = thump * 0.7 + splat * 0.9
    metallic = tone(1600, 0.26, tau=0.09) + tone(2400, 0.26, tau=0.06) + tone(3300, 0.26, tau=0.04)
    toolbox = sequence(click * 0.8, metallic * 0.55)
    drone = tone(55, 2.0) + tone(58.3, 2.0) + lowpass(noise(2.0, 17), 0.02) * 0.6
    fade = np.minimum(1.0, np.minimum(timeline(2.0) / 0.4, (2.0 - timeline(2.0)) / 0.7))
    horror = drone * fade
    chime = sum(tone(freq, 0.9, tau=0.35) / index for index, freq in enumerate((1046.5, 1568.0, 2093.0, 2637.0), start=1))
    clear = sequence(tone(784, 0.09, tau=0.05), tone(1175, 0.14, tau=0.07))
    beep = tone(1000, 0.08, tau=0.07, harmonics=3)
    hitmarker = sequence(click * 0.7, tone(2100, 0.06, tau=0.02) * 0.8)
    impact = sweep(95, 45, 0.2, tau=0.08) + lowpass(noise(0.2, 19), 0.1) * np.exp(-timeline(0.2) / 0.05) * 0.7
    suit = sequence(*(tone(freq, 0.05, tau=0.025) for freq in (600, 800, 1000)), silence(0.03))
    notify = tone(1318, 0.26, tau=0.11) + tone(1760, 0.26, tau=0.09)
    message = sequence(*(tone(freq, 0.07, tau=0.045) for freq in (1568, 1318, 1046)))
    alert = sequence(*(tone(880, 0.08, tau=0.07, harmonics=2) for _ in range(3)), silence(0.04))
    ping = tone(1200, 0.14, tau=0.05)
    squelch = highpass(noise(0.09, 23), 0.35) * np.exp(-timeline(0.09) / 0.03)
    radio = sequence(squelch * 0.8, tone(900, 0.1, tau=0.06), silence(0.03))

    return {
        "click": click,
        "rclick": right_click,
        "hover": hover,
        "blip": blip,
        "blip2": blip2,
        "pickup": pickup,
        "pickup2": pickup2,
        "warning": warning,
        "bodysplat": bodysplat,
        "toolbox": toolbox,
        "horror": horror,
        "chime": chime,
        "clear": clear,
        "beep": beep,
        "hitmarker": hitmarker,
        "impact": impact,
        "suit": suit,
        "notify": notify,
        "message": message,
        "alert": alert,
        "ping": ping,
        "radio": radio,
    }


def normalise(samples: np.ndarray, gain: float = 0.6) -> np.ndarray:
    """Fade the edges first, then scale so every clip peaks at the requested gain."""
    data = np.array(samples, dtype=np.float32)
    fade = min(48, len(data) // 12)
    if fade > 8:
        data[:fade] *= np.linspace(0.0, 1.0, fade)
        data[-fade:] *= np.linspace(1.0, 0.0, fade)
    peak = float(np.max(np.abs(data))) or 1.0
    return data / peak * gain


def write_ogg(path: Path, samples: np.ndarray, gain: float = 0.6) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    sf.write(path, normalise(samples, gain), SAMPLE_RATE, format="OGG", subtype="VORBIS")


def trim(samples: np.ndarray, threshold: float = 0.004) -> np.ndarray:
    loud = np.flatnonzero(np.abs(samples) > threshold)
    if loud.size == 0:
        return samples
    start = max(0, loud[0] - int(0.005 * SAMPLE_RATE))
    end = min(len(samples), loud[-1] + int(0.02 * SAMPLE_RATE))
    return samples[start:end]


def main() -> int:
    parser = argparse.ArgumentParser(description="Regenerate bundled Evernight sounds")
    parser.add_argument("--tts-dir", type=Path, default=None,
                        help="directory holding the WAV files rendered by render_tts.ps1")
    args = parser.parse_args()

    for name in LEGACY_NAMES:
        (SOUNDS / f"{name}.ogg").unlink(missing_ok=True)

    for name, samples in interface_sounds().items():
        write_ogg(SOUNDS / f"{name}.ogg", samples)
        print(f"interface {name}.ogg")

    if args.tts_dir is not None:
        for relative, text in KILLSTREAK_LINES:
            source = args.tts_dir / (relative.replace("/", "_").replace(".ogg", ".wav"))
            if not source.is_file():
                print(f"missing announcer clip for {text!r}: {source}")
                return 1
            samples, rate = sf.read(source, dtype="float32")
            if samples.ndim > 1:
                samples = samples.mean(axis=1)
            if rate != SAMPLE_RATE:
                raise SystemExit(f"unexpected sample rate in {source}")
            write_ogg(KILLSTREAKS / relative, trim(samples), gain=0.7)
            print(f"killstreak {relative}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
