/*
 * Russian Voice Assistant modification for Changan A06 (C390).
 * Copyright (c) 2026 Tecrow. All rights reserved.
 *
 * Required Notice: Copyright (c) 2026 Tecrow. Reverse engineering prohibited.
 * Required Notice: Noncommercial use only. See LICENSE (PolyForm Noncommercial 1.0.0).
 *
 * Licensed under the PolyForm Noncommercial License 1.0.0 — COMMERCIAL USE IS NOT PERMITTED.
 * Reverse engineering, decompilation, and disassembly are NOT permitted under this license,
 * except to the minimum extent applicable mandatory law expressly allows. This source and the
 * compiled result are protected by copyright; unauthorized redistribution is prohibited.
 * Independent mod — NOT affiliated with or endorsed by Changan Automobile. See LICENSE.
 */
package com.stand.asr;

import android.content.Context;
import android.content.res.AssetManager;
import android.util.Log;
import org.json.JSONObject;
import org.vosk.Model;
import org.vosk.Recognizer;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Vosk (vosk-model-small-ru-0.22) offline Russian ASR backend. Exposes an
 * init/ready/reset/accept/finish contract so VoskBridge.feed() can drive it without
 * touching the rest of the pipeline.
 *
 * Vosk is a streaming Kaldi recognizer, but we drive it in batch mode: the stock SR callback
 * gives clean start/end (wp==1 .. wp==3), so we feed every PCM chunk between them and read the
 * final result once at wp==3. No VAD needed.
 *
 * Model assets (~88 MB: am/ graph/ ivector/ conf/) unpack from apk assets to filesDir once.
 * Native deps: libvosk.so (JNA-based) + libjnidispatch.so (JNA), both bundled in lib/arm64-v8a.
 */
public final class VoskAsr {
    private static final String TAG = "VoskAsr";
    private static final String ASSET_DIR = "vosk-model";   // apk assets/vosk-model: am/ graph/ ivector/ conf/
    private static final int RATE = 16000;

    private static volatile Model model;
    private static volatile Recognizer rec;
    private static Context appCtx;

    public static void init(final Context c) {
        appCtx = c.getApplicationContext();
        android.util.Log.i("VoskAsr", com.stand.vosk.VoskBridge.NOTICE);  // legal notice (anchored in dex)
        if (model != null) return;
        new Thread(new Runnable() { public void run() { ensure(); } }).start();
    }

    private static synchronized boolean ensure() {
        if (model != null) return true;
        try {
            if (appCtx == null) return false;
            File dir = new File(appCtx.getFilesDir(), ASSET_DIR);
            if (!new File(dir, "am").exists() || !new File(dir, "graph").exists()) {
                Log.i(TAG, "unpacking Vosk model to " + dir + " (~88 MB, one-time)");
                unpackAssets(ASSET_DIR, dir);
            }
            long t0 = System.currentTimeMillis();
            model = new Model(dir.getAbsolutePath());
            rec = new Recognizer(model, RATE);
            Log.i(TAG, "recognizer ready in " + (System.currentTimeMillis() - t0) + "ms");
            return true;
        } catch (Throwable t) { Log.e(TAG, "ensure", t); return false; }
    }

    public static boolean ready() { return rec != null; }

    /** Start of a new utterance (wp==1): reset the recognizer. */
    public static void reset() {
        if (rec != null) { try { rec.reset(); } catch (Throwable ignored) {} }
    }

    /** Middle chunks: feed raw 16-bit LE PCM (Vosk accepts byte[] directly). */
    public static void accept(byte[] pcm, int len) {
        if (rec == null || pcm == null || len <= 0) return;
        try { rec.acceptWaveForm(pcm, Math.min(len, pcm.length)); }
        catch (Throwable t) { Log.e(TAG, "accept", t); }
    }

    /** End of utterance (wp==3): read the final result (JSON {"text": ...}) -> Russian text. */
    public static String finish() {
        if (!ensure()) { Log.e(TAG, "finish: recognizer not ready"); return ""; }
        try {
            long t0 = System.currentTimeMillis();
            String json = rec.getResult();
            String text = "";
            if (json != null && !json.isEmpty() && !json.equals("{}")) {
                try { text = new JSONObject(json).optString("text", ""); }
                catch (Throwable ignored) { text = json; }
            }
            Log.i(TAG, "decoded in " + (System.currentTimeMillis() - t0) + "ms: " + text);
            return text == null ? "" : text.trim();
        } catch (Throwable t) { Log.e(TAG, "finish", t); return ""; }
    }

    private static void unpackAssets(String assetDir, File outDir) throws Exception {
        AssetManager am = appCtx.getAssets();
        String[] list = am.list(assetDir);
        if (list == null || list.length == 0) {   // it's a file
            outDir.getParentFile().mkdirs();
            InputStream in = am.open(assetDir);
            OutputStream out = new FileOutputStream(outDir);
            byte[] bb = new byte[1 << 16]; int n;
            while ((n = in.read(bb)) > 0) out.write(bb, 0, n);
            in.close(); out.close();
            return;
        }
        outDir.mkdirs();
        for (String name : list) unpackAssets(assetDir + "/" + name, new File(outDir, name));
    }

    private VoskAsr() {}
}
