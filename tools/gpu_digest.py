"""Bind the on-device proof gate to both executable and signed kernel assets."""
from pathlib import Path
import hashlib

root = Path(__file__).resolve().parents[1]
files = [root/'app/src/main/jniLibs/arm64-v8a/libpearlgpu.so']
files += sorted((root/'app/src/main/assets/kernels').glob('*.cl'))
digest = hashlib.sha256()
for path in files:
    digest.update(path.name.encode() + b'\0')
    digest.update(hashlib.sha256(path.read_bytes()).digest())
(root/'app/src/main/java/trade/gozero/pearl/GpuBuild.java').write_text(
    'package trade.gozero.pearl;\n// Executable + bundled kernel digest.\n'
    'final class GpuBuild { static final String ID = "' + digest.hexdigest() + '"; }\n', encoding='utf-8')
