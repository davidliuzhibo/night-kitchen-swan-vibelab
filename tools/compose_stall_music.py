"""Render the original eight-bar 夜摊小调 loop. Synthesis only, no external samples."""
from pathlib import Path
import wave
import numpy as np

RATE = 22050
BEAT = 60 / 96
LENGTH = int(32 * BEAT * RATE)
mix = np.zeros(LENGTH)


def note(start, midi, duration, level, bass=False):
    """A soft electric-piano attack with a quiet second string. Wrap tails across the seam."""
    t = np.arange(round(RATE * duration)) / RATE
    f = 440 * 2 ** ((midi - 69) / 12)
    phase = 2 * np.pi * f * t
    body = np.sin(phase) + .26 * np.sin(2 * phase + .15) * np.exp(-3 * t)
    body += (.08 if bass else .17) * np.sin(phase * 1.0025 + .09)
    envelope = (1 - np.exp(-t / (.027 if bass else .018))) * np.exp(-t / (duration * .32))
    tail = np.minimum(1, (duration - t) / .13).clip(0, 1)
    samples = level * envelope * tail**2 * body
    positions = (round(start * BEAT * RATE) + np.arange(len(t))) % LENGTH
    mix[positions] += samples


# Dmaj9, Bm7, Gmaj7, A6, D/F#, G6, Em7, Asus resolving back into the first bar.
bars = [
    (38, (54, 57, 61), ((.5,66), (2,69), (3.35,64))),
    (35, (54, 57, 62), ((.65,62), (2.5,66))),
    (31, (55, 59, 62), ((.5,67), (2,66), (3,62))),
    (33, (52, 57, 61), ((1,64), (2.65,61))),
    (30, (54, 57, 62), ((.5,66), (1.75,69), (3,71))),
    (31, (52, 55, 59), ((.5,67), (2.5,64))),
    (40, (55, 59, 62), ((1,62), (2.75,59))),
    (33, (52, 57, 62), ((.5,61), (2,64))),
]
for bar, (bass, chord, melody) in enumerate(bars):
    beat = 4 * bar
    note(beat, bass, 1.9, .34, bass=True)
    for i, pitch in enumerate(chord):
        note(beat + .14 + i * .19, pitch, 1.65, .135)
    note(beat + 2.35, chord[1], 1.45, .09)
    for offset, pitch in melody:
        note(beat + offset, pitch, 1.45, .23)

mix += .12 * np.roll(mix, round(.122 * RATE)) + .055 * np.roll(mix, round(.217 * RATE))
pcm = np.rint(mix * (.63 * 32767 / np.max(np.abs(mix)))).astype('<i2')
out = Path(__file__).resolve().parents[1] / 'app/src/main/assets/night_stall_music.wav'
with wave.open(str(out), 'wb') as wav:
    wav.setparams((1, 2, RATE, 0, 'NONE', 'not compressed'))
    wav.writeframes(pcm.tobytes())
