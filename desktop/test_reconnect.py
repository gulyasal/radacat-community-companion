import asyncio
import json
import queue
import runpy
import tempfile
import unittest
from pathlib import Path
from unittest.mock import AsyncMock, patch

module = runpy.run_path(str(Path(__file__).with_name('radacat_desktop.pyw')), run_name='test_import')
Companion = module['Companion']

class RememberTests(unittest.TestCase):
    def test_name_and_send_settings_preserve_device(self):
        app = Companion.__new__(Companion)
        app.settings = {'lastDeviceAddress':'00:11:22:33:44:55','displayName':'Test-PC'}
        with tempfile.TemporaryDirectory() as folder:
            app.config_path = Path(folder) / 'settings.json'
            app.save_settings(group=2222, channel=0)
            self.assertEqual(json.loads(app.config_path.read_text())['lastDeviceAddress'], '00:11:22:33:44:55')
            app.save_settings(displayName='Test')
            self.assertEqual(app.settings['lastDeviceAddress'], '00:11:22:33:44:55')

    def test_saved_address_only_and_unavailable_device(self):
        app = Companion.__new__(Companion)
        app.events = queue.Queue()
        app.connect = AsyncMock()
        scanner = module['BleakScanner']
        with patch.object(scanner, 'find_device_by_address', new=AsyncMock(return_value=None)) as find:
            asyncio.run(app.reconnect('00:11:22:33:44:55'))
            find.assert_awaited_once_with('00:11:22:33:44:55',timeout=10)
            app.connect.assert_not_awaited()
        device = object()
        with patch.object(scanner, 'find_device_by_address', new=AsyncMock(return_value=device)):
            asyncio.run(app.reconnect('00:11:22:33:44:55'))
            app.connect.assert_awaited_once_with(device)

    def test_manual_disconnect_disables_retry(self):
        app = Companion.__new__(Companion)
        app.want_connection = True
        app.disconnect = AsyncMock()
        app.submit = lambda coroutine: coroutine.close()
        app.manual_disconnect()
        self.assertFalse(app.want_connection)

if __name__ == '__main__':
    unittest.main()
