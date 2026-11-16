#!/usr/bin/env python3
"""Check refusal paths without network access or executing any downloaded binary."""
from pathlib import Path
import os
import shutil
import subprocess
import tempfile

root = Path(__file__).resolve().parents[1]
with tempfile.TemporaryDirectory() as temporary:
    fixture = Path(temporary)
    (fixture / 'tools').mkdir()
    (fixture / 'gradle/wrapper').mkdir(parents=True)
    script = fixture / 'tools/bootstrap-wrapper.sh'
    shutil.copy2(root / 'tools/bootstrap-wrapper.sh', script)
    env = dict(os.environ, GRAY_WAKE_NO_DOWNLOAD='1')
    result = subprocess.run(['sh', str(script)], env=env, capture_output=True, text=True)
    assert result.returncode != 0 and 'not cached' in result.stderr, result
    (fixture / 'gradle/wrapper/gradle-wrapper.jar').write_bytes(b'not-a-trusted-wrapper')
    result = subprocess.run(['sh', str(script)], env=env, capture_output=True, text=True)
    assert result.returncode != 0 and 'Refusing an unverified wrapper' in result.stderr, result
print('PASS: bootstrap refuses missing offline wrapper and checksum-mismatched binary')
