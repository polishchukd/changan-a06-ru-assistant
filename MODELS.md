# Model weights not tracked by git

One model file exceeds GitHub's 100 MB per‑file limit and is therefore **excluded from git**
(see `.gitignore`). It is required to build. Place it at exactly this path:

| File                               | Size   | Path                                                                  |
|------------------------------------|--------|-----------------------------------------------------------------------|
| TeraTTS distilled sampler (4‑step) | 245 MB | `tools/tera-tts-java/assets/models/sampler_distilled_cfg3_4step.onnx` |

All the other model files **are** committed, including the ASR model:

- **Vosk small‑RU** (`vosk-model-small-ru-0.22`, ~88 MB) — the offline Russian ASR engine, at
  `stand/asr-android/vosk-model/`. Every file inside is under 100 MB (largest: `graph/HCLr.fst`, 32 MB),
  so the whole tree is tracked by git.
- TeraTTS `text_encoder` / `duration_predictor` / `vocoder`, the tokens, styles, accent dict, and the
  JNI `.so` libs (`libvosk.so`, `libjnidispatch.so`, `libonnxruntime4j_jni.so`).

## Where to get them

- **Vosk small‑RU** — <https://alphacephei.com/vosk/models> → `vosk-model-small-ru-0.22.zip`,
  unpacked so that `am/`, `graph/`, `ivector/`, `conf/` sit directly under `stand/asr-android/vosk-model/`.
- **TeraTTS sampler** — from the upstream TeraTTS / TeraSpace weights
  <https://github.com/Tera2Space/TeraTTS> · <https://huggingface.co/TeraSpace>.

If you distribute a release, attach the TeraTTS sampler as a **release asset** (or use Git LFS) rather
than committing it.
