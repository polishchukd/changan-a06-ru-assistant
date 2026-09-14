# Third-party components

This repository bundles third‑party models, native libraries and build tools. They are **not** covered
by this project's PolyForm Noncommercial license — each keeps its own upstream license. Verify the exact
terms with the upstream project before redistribution; links are provided for that purpose.

| Component | Path | Upstream | License (verify upstream) |
|---|---|---|---|
| Vosk small‑RU (ASR model) | `stand/asr-android/vosk-model/` | https://alphacephei.com/vosk/models | Apache‑2.0 |
| TeraTTS `ru_f2` (TTS models) | `tools/tera-tts-java/assets/` | https://github.com/Tera2Space/TeraTTS · https://huggingface.co/TeraSpace | see upstream |
| ONNX Runtime JNI (`libonnxruntime4j_jni.so`, `ort-android-classes.jar`) | `stand/asr-android/` | https://github.com/microsoft/onnxruntime | MIT |
| baksmali / smali | `tools/baksmali.jar`, `tools/smali.jar` | https://github.com/google/smali | Apache‑2.0 |
| uber‑apk‑signer | `tools/uber-apk-signer.jar` | https://github.com/patrickfav/uber-apk-signer | Apache‑2.0 |
| AOSP platform test‑keys | `tools/platform-key/` | AOSP `build/target/product/security` | Apache‑2.0 (public test keys) |
| Vosk / JNA (ASR engine) | `stand/asr-android/libs/`, `piper/jni/arm64-v8a/libvosk.so`, `libjnidispatch.so` | https://github.com/alphacephei/vosk-api · https://github.com/java-native-access/jna | Apache‑2.0 / LGPL‑2.1+Apache |

Notes:

- The **AOSP test‑keys** are the well‑known public keys shipped in the Android source tree. They are
  required because the C390 firmware is signed with them; they are **not** secret and provide no
  security — anyone can sign with them.
- The **Vosk/JNA** jars + `libvosk.so`/`libjnidispatch.so` are the ASR engine (Vosk small‑RU). They are
  on the `d8` classpath and end up in `classes7.dex` + `lib/arm64-v8a/`.
- The stock **`SpeechAssistant.apk`** (Changan / iFlytek proprietary) is the build input and is **not**
  included in this repository. You must supply your own copy pulled from your device (see `README.md`).
