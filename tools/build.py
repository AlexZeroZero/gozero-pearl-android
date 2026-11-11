"""Portable build entry point. Signing credentials are read only from environment."""
import argparse,os,shutil,subprocess,hashlib,sys
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
p=argparse.ArgumentParser()
p.add_argument('--native',action='store_true')
p.add_argument('--gpu',action='store_true',help='Rebuild optional OpenCL worker; requires OPENCL_INCLUDE_DIR and OPENCL_LIBRARY (ARM64 link library only)')
p.add_argument('--release',action='store_true')
p.add_argument('--build-dir',type=Path,default=ROOT/'build/native')
args=p.parse_args()
def run(cmd):subprocess.run(list(map(str,cmd)),cwd=ROOT,check=True)
if args.native:
    ndk=Path(os.environ['ANDROID_NDK_HOME'])
    run(['cmake','-S',ROOT/'native/cppminer','-B',args.build_dir,'-G','Ninja',
         '-DCMAKE_TOOLCHAIN_FILE='+str(ndk/'build/cmake/android.toolchain.cmake'),
         '-DANDROID_ABI=arm64-v8a','-DANDROID_PLATFORM=android-28','-DANDROID_STL=c++_static',
         '-DCMAKE_BUILD_TYPE=Release','-DCP_PROOF_FFI=ON','-DCP_ENABLE_CPU=ON',
         '-DCP_ENABLE_OPENCL=OFF','-DBUILD_TESTING=ON','-DCMAKE_EXE_LINKER_FLAGS=-Wl,-z,max-page-size=16384'])
    run(['cmake','--build',args.build_dir,'--parallel','4'])
    dest=ROOT/'app/src/main/jniLibs/arm64-v8a/libpearlminer.so'
    dest.parent.mkdir(parents=True,exist_ok=True)
    shutil.copy2(args.build_dir/'cppminer',dest)
    digest=hashlib.sha256(dest.read_bytes()).hexdigest()
    (ROOT/'app/src/main/java/trade/gozero/pearl/CoreBuild.java').write_text(
        'package trade.gozero.pearl;\nfinal class CoreBuild { static final String ID = "'+digest+'"; }\n',encoding='utf-8')
if args.gpu:
    run([sys.executable,ROOT/'tools/build_vulkan_shaders.py'])
    ndk=Path(os.environ['ANDROID_NDK_HOME']);gpu_dir=args.build_dir.parent/(args.build_dir.name+'-gpu')
    run(['cmake','-S',ROOT/'native/cppminer','-B',gpu_dir,'-G','Ninja',
         '-DCMAKE_TOOLCHAIN_FILE='+str(ndk/'build/cmake/android.toolchain.cmake'),
         '-DANDROID_ABI=arm64-v8a','-DANDROID_PLATFORM=android-28','-DANDROID_STL=c++_static',
         '-DCMAKE_BUILD_TYPE=Release','-DCP_PROOF_FFI=ON','-DCP_ENABLE_CPU=ON','-DCP_ENABLE_OPENCL=ON','-DCP_ENABLE_VULKAN_SCAN=ON',
         '-DOpenCL_INCLUDE_DIR='+os.environ['OPENCL_INCLUDE_DIR'],
         '-DOpenCL_LIBRARY='+os.environ['OPENCL_LIBRARY'],
         '-DCMAKE_EXE_LINKER_FLAGS=-Wl,-z,max-page-size=16384'])
    run(['cmake','--build',gpu_dir,'--parallel','4'])
    dest=ROOT/'app/src/main/jniLibs/arm64-v8a/libpearlgpu.so';dest.parent.mkdir(parents=True,exist_ok=True)
    shutil.copy2(gpu_dir/'cppminer',dest)
    kernels=ROOT/'app/src/main/assets/kernels';kernels.mkdir(parents=True,exist_ok=True)
    for source in (ROOT/'native/cppminer/src/opencl/kernels').glob('*.cl'):shutil.copy2(source,kernels/source.name)
    import sys
    run([sys.executable,ROOT/'tools/gpu_digest.py'])
if args.release and not (os.environ.get('BNT_KEYSTORE') and os.environ.get('BNT_STORE_PASSWORD')):
    raise SystemExit('Set BNT_KEYSTORE and BNT_STORE_PASSWORD in your environment for release signing (alias gozero-bnt).')
gradle=os.environ.get('GRADLE','gradle.bat' if os.name=='nt' else 'gradle')
run([gradle,'--no-daemon',':app:testDebugUnitTest',':app:lintDebug',':app:assembleRelease' if args.release else ':app:assembleDebug'])
