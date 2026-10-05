"""Build, sign and check hangul-daily.apk with the Android SDK tools directly (no Gradle).

Usage:  python tools/build_apk.py

Needs a JDK (17 or newer) and an Android SDK with a platform and build-tools installed,
ideally platforms;android-35 and build-tools;35.0.1. If those exact versions are
missing, the newest installed ones are used. Override with ANDROID_PLATFORM and
ANDROID_BUILD_TOOLS. The SDK is found through ANDROID_HOME, ANDROID_SDK_ROOT, or
%LOCALAPPDATA%\\Android\\Sdk.

Steps: tests -> words.tsv -> aapt2 compile/link -> javac -> plain-JDK logic test ->
d8 -> add classes.dex -> zipalign -> apksigner sign + verify -> aapt2 dump badging.
Everything generated goes to build/; the signed APK lands at hangul-daily.apk.
"""

import os
import re
import shutil
import subprocess
import sys
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
TOOLS = ROOT / 'tools'
MAIN = ROOT / 'app' / 'src' / 'main'
BUILD = ROOT / 'build'
OUT_APK = ROOT / 'hangul-daily.apk'

PLATFORM = 'android-35'
BUILD_TOOLS = '35.0.1'
MIN_SDK = 26
VERSION_CODE = 5
VERSION_NAME = '1.4'

# A debug key in the standard Android debug-key format. Its password is the public
# convention ("android"), not a secret. Keep this file: an update must be signed with
# the same key, or the phone makes you uninstall first (losing the app's progress).
KEYSTORE = ROOT / 'keystore' / 'debug.keystore'
KEY_ALIAS = 'androiddebugkey'
KEY_PASS = 'android'


def sdk_dir():
    for var in ('ANDROID_HOME', 'ANDROID_SDK_ROOT'):
        if os.environ.get(var):
            return Path(os.environ[var])
    return Path(os.environ.get('LOCALAPPDATA', str(Path.home()))) / 'Android' / 'Sdk'


def pick_version(parent, wanted, env_var, pattern):
    """The requested version if installed, else the newest installed one matching pattern."""
    if os.environ.get(env_var):
        return os.environ[env_var]
    if (parent / wanted).exists() or not parent.exists():
        return wanted
    have = [p.name for p in parent.iterdir() if p.is_dir() and re.fullmatch(pattern, p.name)]
    if not have:
        return wanted
    newest = max(have, key=lambda name: [int(n) for n in re.findall(r'\d+', name)])
    print(f'note: {parent.name}/{wanted} is not installed; using {newest}')
    return newest


def jdk_tool(name):
    """javac/keytool from the JDK that runs `java`; Oracle's PATH shim lacks keytool."""
    found = shutil.which(name)
    if found:
        return found
    out = subprocess.run(['java', '-XshowSettings:properties', '-version'],
                         capture_output=True, text=True).stderr
    for line in out.splitlines():
        if line.strip().startswith('java.home ='):
            home = Path(line.split('=', 1)[1].strip())
            for candidate in (home / 'bin' / (name + '.exe'), home / 'bin' / name):
                if candidate.exists():
                    return str(candidate)
    sys.exit(f'cannot find {name}; install a JDK 17+ and put its bin folder on PATH')


def native(path):
    """aapt2 and zipalign ship as .exe on Windows."""
    exe = path.with_suffix('.exe')
    return exe if exe.exists() else path


def run(cmd, **kwargs):
    cmd = [str(c) for c in cmd]
    print('>', Path(cmd[0]).name, *cmd[1:4], '...' if len(cmd) > 4 else '', flush=True)
    subprocess.run(cmd, check=True, **kwargs)


def ensure_keystore(keytool):
    if KEYSTORE.exists():
        return
    KEYSTORE.parent.mkdir(parents=True, exist_ok=True)
    run([keytool, '-genkeypair', '-keystore', KEYSTORE, '-storetype', 'PKCS12',
         '-storepass', KEY_PASS, '-keypass', KEY_PASS, '-alias', KEY_ALIAS,
         '-keyalg', 'RSA', '-keysize', '2048', '-validity', '10000',
         '-dname', 'CN=Hangul Daily Debug'])


def main():
    sdk = sdk_dir()
    platform = pick_version(sdk / 'platforms', PLATFORM, 'ANDROID_PLATFORM', r'android-\d+')
    build_tools = pick_version(sdk / 'build-tools', BUILD_TOOLS, 'ANDROID_BUILD_TOOLS', r'\d+\.\d+\.\d+')
    target_sdk = int(platform.split('-')[1])
    bt = sdk / 'build-tools' / build_tools
    android_jar = sdk / 'platforms' / platform / 'android.jar'
    for needed in (bt, android_jar):
        if not needed.exists():
            sys.exit(f'missing {needed}\ninstall with: sdkmanager "platforms;{PLATFORM}" '
                     f'"build-tools;{BUILD_TOOLS}"')
    print(f'SDK {sdk}: {platform}, build-tools {build_tools}, minSdk {MIN_SDK}, targetSdk {target_sdk}')
    aapt2 = native(bt / 'aapt2')
    zipalign = native(bt / 'zipalign')
    d8_jar = bt / 'lib' / 'd8.jar'
    apksigner_jar = bt / 'lib' / 'apksigner.jar'
    javac = jdk_tool('javac')
    keytool = jdk_tool('keytool')
    py = sys.executable

    # 1. Word list: test the romanizer and parser, then regenerate assets/words.tsv.
    run([py, TOOLS / 'test_tools.py'])
    run([py, TOOLS / 'build_words.py'])

    gen, classes, dex, test = (BUILD / d for d in ('gen', 'classes', 'dex', 'test'))
    for d in (gen, classes, dex, test):
        d.mkdir(parents=True, exist_ok=True)

    # 2. Resources and manifest.
    res_zip = BUILD / 'res.zip'
    unsigned = BUILD / 'app-unsigned.apk'
    run([aapt2, 'compile', '--dir', MAIN / 'res', '-o', res_zip])
    run([aapt2, 'link', '-o', unsigned, '-I', android_jar,
         '--manifest', MAIN / 'AndroidManifest.xml', '-A', MAIN / 'assets', '--java', gen,
         '--min-sdk-version', MIN_SDK, '--target-sdk-version', target_sdk,
         '--version-code', VERSION_CODE, '--version-name', VERSION_NAME, res_zip])

    # 3. Java: the app against android.jar, then the plain-JDK logic test.
    sources = sorted((MAIN / 'java').rglob('*.java')) + sorted(gen.rglob('*.java'))
    run([javac, '--release', '11', '-encoding', 'UTF-8', '-Xlint:all,-options',
         '-classpath', android_jar, '-d', classes, *sources])
    pure = [MAIN / 'java' / 'app' / 'hanguldaily' / n
            for n in ('WordList.java', 'DailyPlan.java', 'Sizing.java')]
    run([javac, '--release', '11', '-encoding', 'UTF-8', '-d', test, *pure, TOOLS / 'LogicTest.java'])
    run(['java', '-cp', test, 'app.hanguldaily.LogicTest', MAIN / 'assets' / 'words.tsv'])

    # 4. Dex, package, align, sign.
    run(['java', '-cp', d8_jar, 'com.android.tools.r8.D8', '--release', '--min-api', MIN_SDK,
         '--lib', android_jar, '--output', dex, *sorted(classes.rglob('*.class'))])
    with_dex = BUILD / 'app-with-dex.apk'
    aligned = BUILD / 'app-aligned.apk'
    shutil.copyfile(unsigned, with_dex)
    with zipfile.ZipFile(with_dex, 'a', compression=zipfile.ZIP_DEFLATED) as apk:
        apk.write(dex / 'classes.dex', 'classes.dex')
    run([zipalign, '-f', '-p', '4', with_dex, aligned])
    ensure_keystore(keytool)
    run(['java', '-jar', apksigner_jar, 'sign', '--ks', KEYSTORE, '--ks-key-alias', KEY_ALIAS,
         '--ks-pass', 'pass:' + KEY_PASS, '--key-pass', 'pass:' + KEY_PASS,
         '--out', OUT_APK, aligned])

    # 5. Check what was built.
    run([zipalign, '-c', '-p', '4', OUT_APK])
    run(['java', '-jar', apksigner_jar, 'verify', '--verbose', '--print-certs', OUT_APK])
    run([aapt2, 'dump', 'badging', OUT_APK])
    run([aapt2, 'dump', 'xmltree', OUT_APK, '--file', 'AndroidManifest.xml'])
    print(f'\nAPK: {OUT_APK} ({OUT_APK.stat().st_size:,} bytes)')
    return 0


if __name__ == '__main__':
    sys.exit(main())
