Place Sherpa-ONNX streaming ASR model files here (or under app filesDir/asr after first run unpack):

  tokens.txt
  encoder.onnx
  decoder.onnx
  joiner.onnx

Recommended: sherpa-onnx streaming Zipformer English / Paraformer Hindi quantized models
from https://github.com/k2-fsa/sherpa-onnx/releases

Until models are present, Live ISL uses Android SpeechRecognizer with EXTRA_PREFER_OFFLINE.
