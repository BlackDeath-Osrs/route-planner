"""Renders src/main/resources/com/routeplanner/sailing/bosun-call.wav: the "Bosun's call" picked for the
"all wrecks sunk" salvage alert. Same recipe as the preview page: a sine pipe gliding 1300 -> 2100 Hz,
held, then down to 1500 Hz, with a 7 Hz vibrato and a soft attack and release.

Usage: python3 bosun_call.py <out.wav>
"""
import math, struct, sys, wave

RATE = 44100
LENGTH = 1.05

def freq(t):
    if t < 0.35: return 1300 + (2100 - 1300) * t / 0.35
    if t < 0.65: return 2100
    if t < 0.95: return 2100 + (1500 - 2100) * (t - 0.65) / 0.30
    return 1500

def gain(t):
    peak = 0.22
    if t < 0.05: return 0.0001 * (peak / 0.0001) ** (t / 0.05)          # exponential attack
    if t < 0.85: return peak
    if t < 1.0: return peak * (0.0001 / peak) ** ((t - 0.85) / 0.15)    # exponential release
    return 0.0

phase = 0.0
frames = bytearray()
for i in range(int(RATE * LENGTH)):
    t = i / RATE
    f = freq(t) + 18 * math.sin(2 * math.pi * 7 * t)
    phase += 2 * math.pi * f / RATE
    sample = math.sin(phase) * gain(t) / 0.22 * 0.8                      # normalise to 80% of full scale
    frames += struct.pack('<h', int(max(-1, min(1, sample)) * 32767))

with wave.open(sys.argv[1], 'wb') as w:
    w.setnchannels(1)
    w.setsampwidth(2)
    w.setframerate(RATE)
    w.writeframes(bytes(frames))
