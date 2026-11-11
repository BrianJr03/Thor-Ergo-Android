"""Receive an authenticated SSH stream and safely unpack one temporary bundle.

No credentials are handled. The caller provides archive format, SHA-256,
byte count and an allowlisted action. Never extract links or special files.
"""
from __future__ import annotations
import hashlib
import io
import os
from pathlib import Path,PurePosixPath
import shutil
import subprocess
import sys
import tarfile
import tempfile
import zipfile

MAX_ARCHIVE=64*1024*1024
MAX_EXPANDED=128*1024*1024
MAX_FILES=4096
ACTIONS={'install','check','undo','uninstall','status','diagnostics','start','stop','wake'}

def checked_name(name):
    # POSIX tar commonly prefixes all members with './'.
    while name.startswith('./'):name=name[2:]
    p=PurePosixPath(name)
    if not name or name=='.':return None
    if p.is_absolute() or '..' in p.parts or '\\' in name or '\0' in name or ':' in name:
        raise ValueError('Unsafe archive member name')
    return p

def extract(data,kind,root):
    root=Path(root);seen=set();count=0;total=0
    def write(name,size,reader,directory=False,mode=0):
        nonlocal count,total
        p=checked_name(name)
        if p is None:return
        key=str(p)
        if key in seen:raise ValueError('Duplicate archive member')
        seen.add(key);count+=1;total+=size
        if count>MAX_FILES or total>MAX_EXPANDED or size<0:raise ValueError('Archive limit exceeded')
        destination=root.joinpath(*p.parts)
        if directory:destination.mkdir(parents=True,exist_ok=True);return
        destination.parent.mkdir(parents=True,exist_ok=True)
        with destination.open('xb') as f:
            remaining=size
            while remaining:
                block=reader.read(min(65536,remaining))
                if not block:raise ValueError('Truncated archive member')
                f.write(block);remaining-=len(block)
        os.chmod(destination,0o600)
    if kind=='zip':
        with zipfile.ZipFile(io.BytesIO(data)) as z:
            for item in z.infolist():
                if (item.external_attr>>16)&0o170000==0o120000:raise ValueError('Links are not allowed')
                if item.is_dir():write(item.filename,0,None,True);continue
                with z.open(item) as f:write(item.filename,item.file_size,f)
    elif kind=='tar':
        with tarfile.open(fileobj=io.BytesIO(data),mode='r:gz') as t:
            for item in t:
                if item.isdir():write(item.name,0,None,True)
                elif item.isreg():
                    with t.extractfile(item) as f:write(item.name,item.size,f)
                else:raise ValueError('Links and special files are not allowed')
    else:raise ValueError('Unsupported archive format')
    if not (root/'install.py').is_file():raise ValueError('Missing standalone installer')

def main(argv=None,stream=None):
    args=sys.argv[1:] if argv is None else argv
    if len(args)!=4:raise ValueError('Expected format, digest, byte count and operation')
    kind,sha,length,action=args
    n=int(length)
    if not 0<n<=MAX_ARCHIVE or action not in ACTIONS or len(sha)!=64:raise ValueError('Invalid request')
    stream=sys.stdin.buffer if stream is None else stream
    data=bytearray()
    while len(data)<n:
        block=stream.read(min(65536,n-len(data)))
        if not block:raise ValueError('SSH archive transfer was truncated')
        data.extend(block)
    if hashlib.sha256(data).hexdigest()!=sha:raise ValueError('SSH payload checksum mismatch')
    with tempfile.TemporaryDirectory(prefix='thor-ergo-public-') as tmp:
        extract(bytes(data),kind,Path(tmp))
        # A new process gets no pipe containing credentials or typed input.
        env=dict(os.environ);env['PYTHONDONTWRITEBYTECODE']='1';env['PYTHONUTF8']='1'
        env['PYTHONIOENCODING']='utf-8';env.pop('PYTHONPATH',None);env.pop('LD_PRELOAD',None)
        print('[Thor] Complete archive received and verified.',flush=True)
        return subprocess.run([sys.executable,'-B',str(Path(tmp)/'install.py'),action],
                              cwd=tmp,env=env,stdin=subprocess.DEVNULL).returncode

if __name__=='__main__':
    try:raise SystemExit(main())
    except (OSError,ValueError,tarfile.TarError,zipfile.BadZipFile) as exc:
        print('ERROR:',exc,file=sys.stderr);raise SystemExit(1)
