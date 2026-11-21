import os
import sys
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch
from app_paths import app_paths

class PathsTest(unittest.TestCase):
    def test_source_assets_are_separate(self):
        with patch.object(sys,'frozen',False,create=True):
            data,assets=app_paths()
        self.assertEqual(data.name,'desktop')
        self.assertEqual(assets,data.parent/'assets')

    def test_frozen_data_survives_temporary_bundle(self):
        with tempfile.TemporaryDirectory() as directory:
            with patch.dict(os.environ,{'LOCALAPPDATA':directory}),patch.object(sys,'frozen',True,create=True),patch.object(sys,'_MEIPASS',directory+'/temp-bundle',create=True):
                data,assets=app_paths()
            self.assertEqual(data,Path(directory)/'RadacatCommunityCompanion')
            self.assertEqual(assets,Path(directory)/'temp-bundle/assets')
            self.assertNotEqual(data.parent,assets.parent)

if __name__=='__main__':unittest.main()
