import io
import hashlib
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import zipfile
import tarfile
import transfer

class TransferTests(unittest.TestCase):
    def setUp(self):
        import contextlib
        self.enterContext(contextlib.redirect_stdout(io.StringIO()))
    def zip(self,items):
        out=io.BytesIO()
        with zipfile.ZipFile(out,'w') as z:
            for name,value in items:z.writestr(name,value)
        return out.getvalue()
    def extract(self,data,kind='zip'):
        with tempfile.TemporaryDirectory() as tmp:
            transfer.extract(data,kind,Path(tmp))
            return sorted(p.name for p in Path(tmp).iterdir())
    def test_zip_roundtrip(self):self.assertEqual(self.extract(self.zip([('install.py','pass'),('app/module.py','pass')])),['app','install.py'])
    def test_parent_traversal(self):
        with self.assertRaises(ValueError):self.extract(self.zip([('../bad','x')]))
    def test_absolute_path(self):
        with self.assertRaises(ValueError):self.extract(self.zip([('/bad','x')]))
    def test_windows_path_rejected(self):
        with self.assertRaises(ValueError):self.extract(self.zip([('C:\\bad','x')]))
    def test_duplicate_member(self):
        import warnings
        with warnings.catch_warnings():
            warnings.simplefilter('ignore')
            data=self.zip([('install.py','pass'),('install.py','pass')])
        with self.assertRaises(ValueError):self.extract(data)
    def test_zip_symlink_rejected(self):
        out=io.BytesIO()
        with zipfile.ZipFile(out,'w') as z:
            info=zipfile.ZipInfo('install.py');info.external_attr=(0o120777<<16);z.writestr(info,'/etc/passwd')
        with self.assertRaises(ValueError):self.extract(out.getvalue())
    def test_archive_limit(self):
        with patch.object(transfer,'MAX_EXPANDED',2):
            with self.assertRaises(ValueError):self.extract(self.zip([('install.py','pass')]))
    def test_missing_installer(self):
        with self.assertRaises(ValueError):self.extract(self.zip([('other.py','pass')]))
    def test_bad_hash_never_executes(self):
        with patch('subprocess.run') as run:
            with self.assertRaises(ValueError):transfer.main(['zip','0'*64,'4','install'],io.BytesIO(b'data'))
            run.assert_not_called()
    def test_truncated_stream(self):
        with self.assertRaises(ValueError):transfer.main(['zip','0'*64,'40','install'],io.BytesIO(b'data'))
    def test_invalid_action(self):
        with self.assertRaises(ValueError):transfer.main(['zip','0'*64,'4','shell'],io.BytesIO(b'data'))
    def test_safe_tar_roundtrip(self):
        out=io.BytesIO()
        with tarfile.open(fileobj=out,mode='w:gz') as t:
            info=tarfile.TarInfo('./install.py');info.size=4;t.addfile(info,io.BytesIO(b'pass'))
        self.assertEqual(self.extract(out.getvalue(),'tar'),['install.py'])
    def test_tar_symlink_rejected(self):
        out=io.BytesIO()
        with tarfile.open(fileobj=out,mode='w:gz') as t:
            info=tarfile.TarInfo('app');info.type=tarfile.SYMTYPE;info.linkname='/etc';t.addfile(info)
        with self.assertRaises(ValueError):self.extract(out.getvalue(),'tar')
    def test_remote_command_uses_argv_not_shell(self):
        data=self.zip([('install.py','pass')]);sha=hashlib.sha256(data).hexdigest()
        with patch('subprocess.run') as run:
            run.return_value.returncode=0
            self.assertEqual(transfer.main(['zip',sha,str(len(data)),'check'],io.BytesIO(data)),0)
            self.assertIsInstance(run.call_args.args[0],list)
            self.assertNotIn('shell',run.call_args.kwargs)
            self.assertEqual(run.call_args.args[0][-1],'check')
