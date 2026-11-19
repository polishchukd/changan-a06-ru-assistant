/*
 * Russian Voice Assistant modification for Changan A06 (C390).
 * Copyright (c) 2026 Tecrow.
 * Licensed under the PolyForm Noncommercial License 1.0.0 — noncommercial use only. See LICENSE.
 * Independent modification — not affiliated with or endorsed by Changan Automobile.
 */
package com.stand.tts;

import android.util.Log;

/**
 * Signal-mode audio: fixed success and error PCM tones used INSTEAD OF TeraTTS speech, saving ~370 MB
 * of model assets and all :tts RAM used to hold them.  Generated on-the-fly (no WAV assets required)
 * using the same sine-wave approach as PiperCaTts.TONE_TEST, but with a two-note rising chime for
 * success and a low double-beep for error so the driver can distinguish them without looking at the screen.
 *
 * Success signal: C5 (523 Hz) 200 ms → E5 (659 Hz) 250 ms, 20 ms gap, smooth fade envelope.
 * Error   signal: G3 (196 Hz) 130 ms → 60 ms gap → G3 (196 Hz) 130 ms, hard square-ish attack.
 *
 * Both signals are mono 16-bit LE PCM at the caller's rate.
 */
public final class SignalSound {
    private static final String TAG = "SignalSound";
    private static final float GAIN = 0.65f;   // leave headroom vs system max to avoid clipping

    private SignalSound() {}

    // ---- Cache (same LRU approach as TeraTts.CACHE, but only 2 entries) --------------------------------
    private static volatile byte[] okCache;
    private static volatile byte[] errCache;

    /** Rising two-note chime for successful command execution (~470 ms). Thread-safe. */
    public static byte[] successPcm(int rate) {
        byte[] c = okCache;
        if (c != null) return c;
        synchronized (SignalSound.class) {
            if (okCache != null) return okCache;
            Log.i(TAG, "generating success signal @" + rate);
            okCache = buildSuccess(rate);
            return okCache;
        }
    }

    /** Double low-beep for failed/unrecognised command (~320 ms). Thread-safe. */
    public static byte[] errorPcm(int rate) {
        byte[] c = errCache;
        if (c != null) return c;
        synchronized (SignalSound.class) {
            if (errCache != null) return errCache;
            Log.i(TAG, "generating error signal @" + rate);
            errCache = buildError(rate);
            return errCache;
        }
    }

    // ---- Signal builders (pure math, no I/O) -----------------------------------------------------------

    private static byte[] buildSuccess(int rate) {
        int dur1 = (int) (rate * 0.20);   // C5 523 Hz — 200 ms
        int gap  = (int) (rate * 0.02);   // 20 ms silence
        int dur2 = (int) (rate * 0.25);   // E5 659 Hz — 250 ms
        int total = dur1 + gap + dur2;
        byte[] pcm = new byte[total * 2];
        int pos = 0;
        pos = note(pcm, pos, rate, 523.25f, dur1, 30);   // 30 ms fade-in/out
        pos += gap * 2;
        pos = note(pcm, pos, rate, 659.25f, dur2, 40);   // slightly longer fade for pleasant release
        return pcm;
    }

    private static byte[] buildError(int rate) {
        int beep = (int) (rate * 0.13);   // 130 ms each
        int gap  = (int) (rate * 0.06);   // 60 ms gap
        int total = beep * 2 + gap;
        byte[] pcm = new byte[total * 2];
        int pos = 0;
        pos = note(pcm, pos, rate, 196.00f, beep, 5);    // hard attack, short fade
        pos += gap * 2;
        pos = note(pcm, pos, rate, 196.00f, beep, 5);
        return pcm;
    }

    /**
     * Write one sine note into pcm at byte-offset pos, return new pos.
     * fadeSamples = number of samples at start AND end for a raised-cosine envelope.
     */
    private static int note(byte[] pcm, int pos, int rate, float freqHz, int nSamples, int fadeSamples) {
        fadeSamples = Math.min(fadeSamples, nSamples / 2);
        for (int i = 0; i < nSamples; i++) {
            float env = 1f;
            if (i < fadeSamples) {
                env = 0.5f * (1f - (float) Math.cos(Math.PI * i / fadeSamples));
            } else if (i >= nSamples - fadeSamples) {
                env = 0.5f * (1f - (float) Math.cos(Math.PI * (nSamples - 1 - i) / fadeSamples));
            }
            float v = (float) (Math.sin(2.0 * Math.PI * freqHz * i / rate) * GAIN * env * 32767.0);
            int s = Math.round(Math.max(-32768f, Math.min(32767f, v)));
            pcm[pos++] = (byte) (s & 0xff);
            pcm[pos++] = (byte) ((s >> 8) & 0xff);
        }
        return pos;
    }
}
