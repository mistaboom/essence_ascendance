"""Atomic owner-file preservation under transient Windows sharing failures."""
import json
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

import storage


class StorageRetryTests(unittest.TestCase):
    def test_transient_sharing_failure_retries_then_atomically_publishes(self):
        with tempfile.TemporaryDirectory() as root:
            owner=Path(root)/'manifest.json'
            original={'owner':'old', 'revision':1}
            replacement={'owner':'new', 'revision':2, 'nested':{'unicode':'chime \u266a'}}
            storage.write(owner,original)
            old_bytes=owner.read_bytes()
            replace=storage.os.replace
            attempts=[]

            def transient_then_replace(source,destination):
                self.assertEqual(owner.read_bytes(),old_bytes)
                self.assertEqual(storage.read(source),replacement)
                attempts.append((Path(source),Path(destination)))
                if len(attempts)==1:
                    raise PermissionError('simulated reader denies delete sharing')
                replace(source,destination)

            with patch.object(storage.os,'replace',side_effect=transient_then_replace), \
                    patch.object(storage.time,'sleep') as sleep:
                storage.write(owner,replacement)
            self.assertEqual(len(attempts),2)
            sleep.assert_called_once_with(.1)
            self.assertEqual(storage.read(owner),replacement)
            self.assertEqual(owner.read_text(encoding='utf-8'),json.dumps(replacement,indent=2,allow_nan=False)+'\n')
            self.assertFalse(owner.with_suffix('.json.tmp').exists())

    def test_persistent_sharing_failure_preserves_owner_and_recoverable_temp(self):
        with tempfile.TemporaryDirectory() as root:
            owner=Path(root)/'manifest.json'
            storage.write(owner,{'revision':1})
            old_bytes=owner.read_bytes()
            replacement={'revision':2,'review':{'status':'pending'}}
            failure=PermissionError('simulated persistent sharing violation')
            with patch.object(storage.os,'replace',side_effect=failure) as replace, \
                    patch.object(storage.time,'sleep') as sleep:
                with self.assertRaises(PermissionError) as caught:
                    storage.write(owner,replacement)
            self.assertIs(caught.exception,failure)
            self.assertEqual(replace.call_count,10)
            self.assertEqual(sleep.call_count,9)
            self.assertTrue(all(call.args==(.1,) for call in sleep.call_args_list))
            self.assertEqual(owner.read_bytes(),old_bytes)
            self.assertEqual(storage.read(owner.with_suffix('.json.tmp')),replacement)


if __name__=='__main__': unittest.main()
