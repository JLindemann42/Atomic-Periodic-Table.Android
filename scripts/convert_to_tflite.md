Goal: convert a small seq2seq model (e.g., t5-small or flan-t5-small) to a TFLite model suitable for Android.

High level steps (recommended path):

1) Choose a compact model
   - flan-t5-small or t5-small are reasonable starting points.

2) Export to TensorFlow SavedModel
   - Use Hugging Face Transformers TF conversion utilities or example notebooks.
   - Example: use `TFAutoModelForSeq2SeqLM.from_pretrained(...)` then `model.save_pretrained('saved_model_dir')`.

3) Convert SavedModel to TFLite
   - Use the TFLite converter (tf.lite.TFLiteConverter.from_saved_model)
   - Consider post-training quantization (float16 or int8) to reduce size and speed up on mobile.
   - Example:
     converter = tf.lite.TFLiteConverter.from_saved_model('saved_model_dir')
     converter.optimizations = [tf.lite.Optimize.DEFAULT]
     converter.target_spec.supported_types = [tf.float16]
     tflite_model = converter.convert()

4) Test locally with the TFLite interpreter (Python) and verify outputs match.

5) Bundle smaller TFLite model into Android assets and call via Interpreter API.

Notes and tradeoffs:
 - Converting complex transformer decoders to TFLite can be non-trivial; some ops may not be supported. Another option is to use ONNX + ONNX Runtime Mobile or use a distilled model specifically prepared for mobile (see TFLite community models).
 - For tiny offline usage, consider a retrieval-only approach with templated answers, or a very small seq2seq distilled model.

If needed, the scripts/ folder can be extended with conversion helper notebooks. Ask if you want an automated conversion notebook added.


Embedding model conversion (sentence-transformers/all-MiniLM-L6-v2 -> TFLite, dynamic range quantization)

1) Install dependencies (use a virtualenv):
   pip install transformers onnx onnxruntime-optimum sentence-transformers torch tf2onnx tensorflow

2) Export a PyTorch model to ONNX (simplified example):
   python - <<'PY'
import torch
from sentence_transformers import SentenceTransformer
model = SentenceTransformer('sentence-transformers/all-MiniLM-L6-v2')
model.eval()
# create a dummy input (this example uses tokens; exact input depends on export wrapper used)
# Many sentence-transformers models wrap a HuggingFace model; follow HF export examples to produce an ONNX for the underlying transformer encoder.
PY

3) Convert ONNX -> TensorFlow SavedModel using tf2onnx or onnx-tf, then use TFLiteConverter with dynamic range quantization:
   converter = tf.lite.TFLiteConverter.from_saved_model('saved_model_dir')
   converter.optimizations = [tf.lite.Optimize.DEFAULT]
   tflite_model = converter.convert()
   with open('embed_dynamic.tflite','wb') as f: f.write(tflite_model)

4) Place embed_dynamic.tflite into app/src/main/assets/data/<lang>/embed.tflite

5) The Android app now looks for data/{lang}/embed.tflite and will attempt to load it. The Kotlin embedding inference is scaffolded in TfliteRagAgent.kt; it currently falls back to the deterministic stub if model input/output shapes differ. Implementing exact I/O mapping depends on the converted model signature.

Notes:
- Model conversion paths differ by model; this is a pragmatic guideline. If you want, the next step is to attempt an automated conversion for all-MiniLM-L6-v2 here and I can run it; expect additional dependency installs and possible manual fixes for op support.
