"""Isolated provenance/fixture/source-error proof. No Gradle or production baseline."""
import importlib.util
import json
import os
from pathlib import Path
import subprocess
import tempfile

script = Path(__file__).with_name('precheck.py').resolve()
spec = importlib.util.spec_from_file_location('precheck', script)
pc = importlib.util.module_from_spec(spec)
spec.loader.exec_module(pc)
cache = Path.home() / '.gradle/caches/modules-2/files-2.1'
compiler = []
for folder in ['org.jetbrains.kotlin', 'org.jetbrains.intellij.deps', 'org.jetbrains/annotations', 'net.java.dev.jna']:
    compiler += [p for p in (cache / folder).rglob('*.jar')
                 if not p.name.endswith(('-sources.jar', '-javadoc.jar'))
                 and (('/1.8.0/' in p.as_posix()) or 'trove4j' in p.name or 'annotations-13.0.jar' == p.name or 'jna-5.8.0.jar' == p.name)]
assert any('kotlin-compiler-embeddable' in p.name for p in compiler)
with tempfile.TemporaryDirectory(prefix='rsps-precheck-proof-') as tmp:
    root = Path(tmp)
    subprocess.run(['git', 'init', '-q', str(root)], check=True)
    package = root / 'build/package'
    package.mkdir(parents=True)
    # Copy the actual compiler jars, so corruption of the proof cannot touch real caches.
    import shutil
    for jar in compiler:
        shutil.copyfile(jar, package / jar.name)
    names = sorted(p.name for p in package.glob('*.jar'))
    stdlibs = [n for n in names if n.startswith('kotlin-stdlib')]
    manifest = dict(version=1, snapshot='isolated-successful-compiler-fixture', sources={},
                    artifacts={n: pc.digest(package / n) for n in names}, jars=stdlibs, compiler=names,
                    tests={':game:plugins': 'test-classes'},
                    modules={':game:plugins': dict(jvmTarget='11', args=[])})
    manifest_file = package / 'manifest.json'
    manifest_file.write_text(json.dumps(manifest))
    env = dict(os.environ, PRECHECK_BASELINE=str(package))
    def check(expected):
        p = subprocess.run(['python', str(script)], cwd=root, env=env, capture_output=True, text=True)
        print(p.stdout)
        assert p.returncode == expected, (p.returncode, p.stderr)
    check(0)
    src = root / 'game/plugins/src/main/kotlin/Proof.kt'
    src.parent.mkdir(parents=True)
    src.write_text('val invalid: String = 7\n')
    check(1)  # Actual Kotlin type error: must block before Gradle.
    src.write_text('val valid: String = "ok"\n')
    check(0)
    test = root / 'game/plugins/src/test/kotlin/fixture'
    test.mkdir(parents=True)
    fixture = test / 'N11Fixture.kt'
    fixture.write_text('package fixture\ninternal class N11Fixture { fun answer() = 42 }\n')
    # Compile the fixture successfully, then record it as part of this baseline.
    out = package / 'test-classes'
    command = ['java', '-cp', os.pathsep.join(str(package / n) for n in names),
               'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler', '-no-stdlib', '-no-reflect',
               '-jvm-target', '11', '-cp', os.pathsep.join(str(package / n) for n in stdlibs),
               '-d', str(out), str(fixture)]
    subprocess.run(command, check=True, capture_output=True)
    manifest['sources'] = pc.source_files(root)
    manifest['artifacts'].update({p.relative_to(package).as_posix(): pc.digest(p) for p in out.rglob('*') if p.is_file()})
    manifest_file.write_text(json.dumps(manifest))
    (test / 'Consumer.kt').write_text('package fixture\nfun answer() = N11Fixture().answer()\n')
    check(0)  # Current internal fixture is compiled together with its consumer.
    target = package / stdlibs[0]
    with target.open('ab') as stream:
        stream.write(b'changed')
    check(2)  # Corrupt or foreign jars are ambiguous, never false source errors.
print('PRECHECK PROOF PASS: clean, actual type error, internal fixture, tampered artifact')
