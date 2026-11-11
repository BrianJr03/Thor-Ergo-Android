#!/usr/bin/env python3
"""Publish the exact approved Thor Ergo bundle in this fork, never upstream.

prepare: verify every byte, stage source/docs and retain release files privately.
publish: re-verify, fast-forward main, verify draft assets, then publish v1.0.
No device access, credentials in files, forced Git updates or public-asset clobber.
"""
from __future__ import annotations
import hashlib
import json
import os
from pathlib import Path, PurePosixPath
import re
import shutil
import stat
import subprocess
import sys
import tempfile
import zipfile

ROOT = Path(__file__).resolve().parents[2]
CONFIG = ROOT / '.github/thor-ergo-publication.json'
MAX_TOTAL = 80 * 1024 * 1024


def fail(message):
    raise RuntimeError(message)


def run(*args, check=True, binary=False):
    return subprocess.run(args, cwd=ROOT, check=check, capture_output=True,
                          text=not binary)


def digest(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def settings():
    c = json.loads(CONFIG.read_text(encoding='utf-8'))
    if c.get('repository') != 'seekingalphacrawler/Thor-Ergo':
        fail('Publication is restricted to the approved fork.')
    if os.environ.get('GITHUB_REPOSITORY', c['repository']) != c['repository']:
        fail('Wrong GitHub repository; upstream is never modified.')
    if os.environ.get('GITHUB_REF', 'refs/heads/main') != 'refs/heads/main':
        fail('Run publication on main only.')
    if c.get('archive') != 'Thor-Ergo-1.0-Publish.zip' or c.get('tag') != 'v1.0':
        fail('Unexpected publication name/tag.')
    if not re.fullmatch(r'[0-9a-f]{64}', c.get('sha256', '')):
        fail('Invalid approved checksum.')
    return c


def state_path():
    tmp = Path(os.environ.get('RUNNER_TEMP', tempfile.gettempdir())).resolve()
    return tmp / 'thor-ergo-publication-state.json'


def read_archive(c, temp):
    src = ROOT / c['archive']
    if src.is_file() and not src.is_symlink():
        data = src.read_bytes()
    else:
        # A completed source commit removes the upload file. A rerun may read
        # its exact verified bytes from this repository's own Git history.
        result = run('git', 'log', '-1', '--format=%H', '--diff-filter=AM',
                     '--', c['archive'])
        commit = result.stdout.strip()
        if not re.fullmatch(r'[0-9a-f]{40,64}', commit):
            fail('Upload Thor-Ergo-1.0-Publish.zip to the repository root first.')
        data = run('git', 'show', commit + ':' + c['archive'], binary=True).stdout
    if hashlib.sha256(data).hexdigest() != c['sha256']:
        fail('The uploaded ZIP does not match the approved publication checksum.')
    path = temp / 'verified-upload.zip'
    path.write_bytes(data)
    return path


def extract_checked(archive, dest):
    seen = set()
    with zipfile.ZipFile(archive) as z:
        items = z.infolist()
        if len(items) > 1500 or sum(i.file_size for i in items) > MAX_TOTAL:
            fail('Unexpected archive size.')
        for info in items:
            name = info.filename
            p = PurePosixPath(name)
            mode = info.external_attr >> 16
            if (name in seen or not name or p.is_absolute() or '..' in p.parts
                    or '\\' in name or p.as_posix() != name or '\x00' in name
                    or info.is_dir() or info.file_size > 25 * 1024 * 1024
                    or stat.S_IFMT(mode) not in (0, stat.S_IFREG)
                    or any(part in ('.git', '.github') for part in p.parts)
                    or (name != 'manifest.json' and p.parts[0] not in ('repository', 'release'))):
                fail('Unsafe or unexpected archive entry: ' + name)
            seen.add(name)
            out = dest.joinpath(*p.parts)
            out.parent.mkdir(parents=True, exist_ok=True)
            out.write_bytes(z.read(info))
            out.chmod(0o755 if mode & 0o111 else 0o644)
    m = json.loads((dest / 'manifest.json').read_text(encoding='utf-8'))
    files = m.get('files')
    if m.get('version') != '1.0' or not isinstance(files, dict):
        fail('Unexpected publication manifest.')
    if set(files) != seen - {'manifest.json'}:
        fail('Manifest/archive member mismatch.')
    for name, sha in files.items():
        if not re.fullmatch(r'[0-9a-f]{64}', str(sha)) or digest(dest / name) != sha:
            fail('File checksum mismatch: ' + name)
    return files


def verify_installation(root):
    # The same hash discipline used by the installers, without importing them.
    for folder in (root, root / 'Thor'):
        expected = {}
        for line in (folder / 'SHA256SUMS').read_text().splitlines():
            sha, name = line.split('  ', 1)
            p = PurePosixPath(name)
            if (p.is_absolute() or '..' in p.parts or '\\' in name or
                    name in expected or not re.fullmatch(r'[0-9a-f]{64}', sha)):
                fail('Invalid installation checksum manifest.')
            expected[name] = sha
        actual = {p.relative_to(folder).as_posix() for p in folder.rglob('*')
                  if p.is_file() and p != folder / 'SHA256SUMS'}
        if actual != set(expected):
            fail('Installation contains missing or extra files.')
        for name, sha in expected.items():
            if digest(folder / name) != sha:
                fail('Installation checksum mismatch: ' + name)
    with zipfile.ZipFile(root / 'Payload/Thor.zip') as z:
        expected = {p.relative_to(root / 'Thor').as_posix(): p
                    for p in (root / 'Thor').rglob('*') if p.is_file()}
        if set(z.namelist()) != set(expected):
            fail('Embedded SSH payload member mismatch.')
        for name, p in expected.items():
            if z.read(name) != p.read_bytes():
                fail('Embedded SSH payload differs: ' + name)
    if (root / 'Thor/app/ERGO_VERSION').read_text().strip() != '1.0':
        fail('Wrong application release version.')


def verify_release(temp, c):
    release = temp / 'release' / c['asset']
    checksum = temp / 'release/SHA256SUMS.txt'
    if checksum.read_text() != digest(release) + '  ' + c['asset'] + '\n':
        fail('Release download checksum mismatch.')
    dist = temp / 'repository/distribution'
    verify_installation(dist)
    expected = {'Thor-Ergo-1.0/' + p.relative_to(dist).as_posix(): p
                for p in dist.rglob('*') if p.is_file()}
    with zipfile.ZipFile(release) as z:
        if set(z.namelist()) != set(expected):
            fail('Release/source member mismatch.')
        for name, p in expected.items():
            if z.read(name) != p.read_bytes():
                fail('Release differs from repository source: ' + name)


def verify_staged(files):
    expected = {name[len('repository/'):]: sha for name, sha in files.items()
                if name.startswith('repository/')}
    actual = set()
    for base, dirs, names in os.walk(ROOT):
        dirs[:] = [d for d in dirs if d not in ('.git', '.github')]
        for name in names:
            p = Path(base) / name
            rel = p.relative_to(ROOT).as_posix()
            if p.is_symlink() or rel not in expected or digest(p) != expected[rel]:
                fail('Unexpected or modified staged file: ' + rel)
            actual.add(rel)
    if actual != set(expected):
        fail('Missing staged repository files.')


def prepare():
    c = settings()
    top = Path(run('git', 'rev-parse', '--show-toplevel').stdout.strip()).resolve()
    if top != ROOT:
        fail('Script must run from the repository checkout.')
    head = run('git', 'rev-parse', 'HEAD').stdout.strip()
    tmp = Path(tempfile.mkdtemp(prefix='thor-ergo-publish-',
               dir=os.environ.get('RUNNER_TEMP')))
    archive = read_archive(c, tmp)
    staged = tmp / 'contents'
    staged.mkdir()
    files = extract_checked(archive, staged)
    verify_release(staged, c)
    # A rerun must not overwrite edits made after this public release.
    already_staged = ((ROOT / 'VERSION').is_file() and
                      (ROOT / 'VERSION').read_text().strip() == '1.0' and
                      (ROOT / 'distribution').is_dir())
    if not already_staged:
        tracked = run('git', 'ls-files', '-z').stdout.split('\0')
        remove = [p for p in tracked if p and not p.startswith('.github/')]
        for start in range(0, len(remove), 100):
            run('git', 'rm', '--force', '--quiet', '--', *remove[start:start+100])
        for source in (staged / 'repository').rglob('*'):
            if source.is_file():
                dest = ROOT / source.relative_to(staged / 'repository')
                dest.parent.mkdir(parents=True, exist_ok=True)
                shutil.copy2(source, dest)
    verify_staged(files)
    state = {'input_commit': head, 'contents': str(staged), 'files': files,
             'approved_sha256': c['sha256']}
    state_path().write_text(json.dumps(state), encoding='utf-8')
    state_path().chmod(0o600)
    print('Verified and staged source, documentation, menu previews and complete release.')


def gh(c, *args, check=True):
    return run('gh', 'release', *args, '--repo', c['repository'], check=check)


def verify_uploaded_assets(c, contents):
    dest = Path(tempfile.mkdtemp(prefix='thor-ergo-assets-',
                dir=os.environ.get('RUNNER_TEMP')))
    gh(c, 'download', c['tag'], '--dir', str(dest),
       '--pattern', c['asset'], '--pattern', 'SHA256SUMS.txt')
    for name in (c['asset'], 'SHA256SUMS.txt'):
        if digest(dest / name) != digest(contents / 'release' / name):
            fail('Uploaded asset failed verification: ' + name)


def verify_tag(c, target):
    result = run('git', 'ls-remote', '--tags', 'origin',
                 'refs/tags/' + c['tag'], 'refs/tags/' + c['tag'] + '^{}')
    refs = dict(line.split('\t', 1)[::-1] for line in result.stdout.splitlines() if line)
    actual = refs.get('refs/tags/' + c['tag'] + '^{}', refs.get('refs/tags/' + c['tag']))
    if actual and actual != target:
        fail('The release tag points to a different source commit; it will not be moved.')


def publish():
    c = settings()
    if not os.environ.get('GH_TOKEN') or os.environ.get('GITHUB_ACTIONS') != 'true':
        fail('Publishing is allowed only in the authenticated GitHub Actions job.')
    state = json.loads(state_path().read_text())
    if state['approved_sha256'] != c['sha256']:
        fail('Publication configuration changed during the job.')
    contents = Path(state['contents'])
    verify_staged(state['files'])
    verify_release(contents, c)
    run('git', 'fetch', 'origin', 'main')
    if run('git', 'rev-parse', 'origin/main').stdout.strip() != state['input_commit']:
        fail('main changed during preparation; rerun on the current main branch.')
    run('git', 'config', 'user.name', 'github-actions[bot]')
    run('git', 'config', 'user.email', '41898282+github-actions[bot]@users.noreply.github.com')
    run('git', 'add', '-A')
    if run('git', 'diff', '--cached', '--quiet', check=False).returncode:
        run('git', 'commit', '-m', 'Publish Thor Ergo 1.0')
        run('git', 'push', 'origin', 'HEAD:main')
    target = run('git', 'rev-parse', 'HEAD').stdout.strip()
    verify_tag(c, target)
    view = gh(c, 'view', c['tag'], '--json', 'isDraft,url', check=False)
    if view.returncode == 0:
        data = json.loads(view.stdout)
        if not data['isDraft']:
            verify_uploaded_assets(c, contents)
            print('The identical public release is already available: ' + data['url'])
            return
    else:
        gh(c, 'create', c['tag'], '--target', target, '--draft',
           '--title', 'Thor Ergo 1.0', '--notes-file', str(ROOT / 'RELEASE_NOTES.md'))
    # Clobber is restricted to a verified draft, never a public release.
    draft = json.loads(gh(c, 'view', c['tag'], '--json', 'isDraft').stdout)
    if not draft['isDraft']:
        fail('Release became public during publication; refusing asset replacement.')
    gh(c, 'upload', c['tag'], str(contents / 'release' / c['asset']),
       str(contents / 'release/SHA256SUMS.txt'), '--clobber')
    verify_uploaded_assets(c, contents)
    verify_tag(c, target)
    gh(c, 'edit', c['tag'], '--draft=false', '--prerelease=false', '--latest',
       '--title', 'Thor Ergo 1.0', '--notes-file', str(ROOT / 'RELEASE_NOTES.md'))
    url = 'https://github.com/' + c['repository'] + '/releases/tag/' + c['tag']
    print('Published and verified: ' + url)
    summary = os.environ.get('GITHUB_STEP_SUMMARY')
    if summary:
        with open(summary, 'a', encoding='utf-8') as f:
            f.write('# Thor Ergo 1.0\n\nSource, menu previews and release assets verified.\n\n'
                    '[Open the release](' + url + ')\n')


def main():
    if len(sys.argv) != 2 or sys.argv[1] not in ('prepare', 'publish'):
        fail('Usage: publish_thor_ergo.py prepare|publish')
    {'prepare': prepare, 'publish': publish}[sys.argv[1]]()


if __name__ == '__main__':
    try:
        main()
    except (RuntimeError, OSError, ValueError, zipfile.BadZipFile,
            subprocess.CalledProcessError) as e:
        print('ERROR:', e, file=sys.stderr)
        # Subprocess command text contains no credentials; do not print env vars.
        if isinstance(e, subprocess.CalledProcessError) and e.stderr:
            print(e.stderr, file=sys.stderr)
        raise SystemExit(1)
