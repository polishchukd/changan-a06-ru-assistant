/*
 * Russian Voice Assistant modification for Changan A06 (C390).
 * Copyright (c) 2026 Tecrow.
 * Licensed under the PolyForm Noncommercial License 1.0.0 — noncommercial use only. See LICENSE.
 * Independent modification — not affiliated with or endorsed by Changan Automobile.
 */
package com.stand.tts;

import android.content.Context;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

/**
 * Signal-mode audio: fixed success and error sounds used INSTEAD OF TeraTTS speech, saving ~370 MB
 * of model assets and all :tts RAM used to hold them.  Preferred source: owner-provided WAV assets
 * assets/stand/signal_success.wav / signal_error.wav (decoded + resampled via WakeChime's RIFF
 * decoder, same as the wake chime).  If the asset is missing or undecodable (e.g. minimal build
 * without signal WAVs), falls back to on-the-fly synthesized tones so the mode never goes silent.
 *
 * Fallback success signal: C5 (523 Hz) 200 ms → E5 (659 Hz) 250 ms, 20 ms gap, smooth fade envelope.
 * Fallback error   signal: G3 (196 Hz) 130 ms → 60 ms gap → G3 (196 Hz) 130 ms, hard square-ish attack.
 *
 * Both signals are mono 16-bit LE PCM at the caller's rate.
 */
public final class SignalSound {
    private static final String TAG = "SignalSound";
    private static final float GAIN = 0.65f;   // leave headroom vs system max to avoid clipping

    // Per-signal post-decode gain, calibrated so the owner WAVs land near the synthesized reference
    // level (~0.65 FS): success.wav peaks at 0.39 FS (needs a lift), error.wav at 0.89 FS (slight cut).
    private static final float SUCCESS_GAIN = 1.6f;
    private static final float ERROR_GAIN = 0.8f;

    private SignalSound() {}

    // ---- Cache (same LRU approach as TeraTts.CACHE, but only 2 entries) --------------------------------
    private static volatile byte[] okCache;
    private static volatile byte[] errCache;

    /** Success signal for command execution: real WAV asset if bundled, synthesized chime otherwise.
     *  Thread-safe; decoded once per process. */
    public static byte[] successPcm(Context ctx, int rate) {
        byte[] c = okCache;
        if (c != null) return c;
        synchronized (SignalSound.class) {
            if (okCache != null) return okCache;
            byte[] a = decodeAsset(ctx, "stand/signal_success.wav", rate, SUCCESS_GAIN);
            okCache = a != null ? a : buildSuccess(rate);
            return okCache;
        }
    }

    /** Error signal for failed/unrecognised command: real WAV asset if bundled, synthesized beep otherwise. */
    public static byte[] errorPcm(Context ctx, int rate) {
        byte[] c = errCache;
        if (c != null) return c;
        synchronized (SignalSound.class) {
            if (errCache != null) return errCache;
            byte[] a = decodeAsset(ctx, "stand/signal_error.wav", rate, ERROR_GAIN);
            errCache = a != null ? a : buildError(rate);
            return errCache;
        }
    }

    /** Decode a bundled signal WAV to 16-bit mono PCM at outRate, or null if absent/undecodable. */
    private static byte[] decodeAsset(Context ctx, String path, int outRate, float gain) {
        if (ctx == null) return null;
        try {
            InputStream in = ctx.getAssets().open(path);
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[16384]; int n;
            while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
            in.close();
            byte[] pcm = WakeChime.wavToPcm16Mono(bo.toByteArray(), outRate);
            applyGain(pcm, gain);
            Log.i(TAG, "decoded " + path + " -> " + pcm.length + " bytes @" + outRate);
            return pcm;
        } catch (Throwable t) {
            Log.i(TAG, "signal asset " + path + " unavailable, using synthesized tone: " + t);
            return null;
        }
    }

    /** In-place 16-bit LE gain with hard clamp. */
    private static void applyGain(byte[] pcm, float gain) {
        if (gain == 1f) return;
        for (int i = 0; i + 1 < pcm.length; i += 2) {
            int s = (short) ((pcm[i] & 0xff) | (pcm[i + 1] << 8));
            s = Math.round(s * gain);
            s = Math.max(-32768, Math.min(32767, s));
            pcm[i] = (byte) (s & 0xff); pcm[i + 1] = (byte) ((s >> 8) & 0xff);
        }
    }

    // ---- Signal builders (pure math, no I/O; fallback when WAV assets are not bundled) ------------------

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
