"""Generate the original, periodic AC motor/fan loop and check its decoded audio.

Requires NumPy and SoundFile (including libsndfile's Vorbis encoder). For an
isolated local install: py -3 -m pip install --target build/audio-tools soundfile
Then run with PYTHONPATH=build/audio-tools from the project root.
"""

import json
from pathlib import Path

import numpy as np
import soundfile as sf


ROOT = Path(__file__).resolve().parent.parent
OUTPUT = ROOT / "src/main/resources/assets/biome_based_crops/sounds/compressor.ogg"
RATE = 22050
SECONDS = 8


def rms(samples):
    return float(np.sqrt(np.mean(samples * samples)))


def main():
    count = RATE * SECONDS
    time = np.arange(count) / RATE
    motor = sum(
        amplitude * np.sin(2 * np.pi * frequency * time + phase)
        for frequency, amplitude, phase in (
            (60, 1.0, 0.2), (120, 0.2, 1.1), (180, 0.08, 0.6),
            (240, 0.05, 2.0), (360, 0.03, 1.5)
        )
    )
    motor /= rms(motor)

    # Fourier bins and integer motor frequencies are exactly periodic across the
    # entire eight seconds. No envelope, startup, shutdown or crossfade can cause
    # the volume to dip whenever OpenAL loops this buffer.
    random = np.random.default_rng(731241)
    frequencies = np.fft.rfftfreq(count, 1 / RATE)
    weight = (frequencies / 140) ** 2 / (1 + (frequencies / 140) ** 2)
    weight *= np.exp(-(frequencies / 1800) ** 2) / np.sqrt(np.maximum(frequencies, 80))
    spectrum = weight * np.exp(1j * random.uniform(0, 2 * np.pi, len(frequencies)))
    spectrum[0] = spectrum[-1] = 0
    fan = np.fft.irfft(spectrum, count)
    fan /= rms(fan)
    samples = 0.85 * motor + 0.45 * fan
    # Match the previous beacon sample's overall level at the existing 0.12 gain,
    # while keeping the level constant throughout the new recording.
    samples *= 0.04 / rms(samples)
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    sf.write(OUTPUT, samples, RATE, format="OGG", subtype="VORBIS", compression_level=0.5)

    decoded, rate = sf.read(OUTPUT)
    windows = np.array_split(decoded, SECONDS * 4)
    levels = [rms(window) for window in windows]
    differences = np.abs(np.diff(decoded))
    seam = abs(float(decoded[0] - decoded[-1]))
    report = {
        "sample_rate": rate,
        "channels": 1,
        "seconds": len(decoded) / rate,
        "rms": rms(decoded),
        "quarter_second_rms_swing_db": float(20 * np.log10(max(levels) / min(levels))),
        "peak": float(np.max(np.abs(decoded))),
        "dc_offset": float(np.mean(decoded)),
        "seam_step": seam,
        "ordinary_sample_step_99th_percentile": float(np.percentile(differences, 99)),
        "bytes": OUTPUT.stat().st_size,
    }
    assert rate == RATE and len(decoded) == count and np.isfinite(decoded).all()
    assert report["peak"] < 1 and abs(report["dc_offset"]) < 0.0001
    assert report["quarter_second_rms_swing_db"] < 1
    assert seam < report["ordinary_sample_step_99th_percentile"]
    report_path = ROOT / "build/compressor-sound-analysis.json"
    report_path.parent.mkdir(parents=True, exist_ok=True)
    report_path.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(report, indent=2))


if __name__ == "__main__":
    main()
