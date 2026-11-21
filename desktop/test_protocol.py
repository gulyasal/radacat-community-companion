import re
import unittest
from pathlib import Path
from protocol import crc, encode, describe, named_message, map_links, coordinate

class ProtocolTests(unittest.TestCase):
    def test_fixed_location(self):
        self.assertEqual(coordinate('52.52','13.405'),'geo:52.52000,13.40500')
        self.assertEqual(coordinate('-90','-180'),'geo:-90.00000,-180.00000')
        self.assertEqual(len(named_message('abcdefghijklmnop',coordinate(-90,-180)).encode()),42)
        for lat,lon in [('',0),('nan',0),(0,'inf'),(91,0),(0,-181)]:
            with self.assertRaises(ValueError):coordinate(lat,lon)
    def test_location_links(self):
        links=list(map_links('Test: geo:52.12345,-13.12345'))
        self.assertEqual(len(links),1)
        self.assertTrue(links[0][2].endswith('52.12345,-13.12345'))
        self.assertEqual(list(map_links('geo:99.00000,181.00000')),[])
    def test_display_names(self):
        self.assertEqual(named_message(' Test ', 'Hello'), 'Test: Hello')
        self.assertEqual(len(named_message('Test','x'*36).encode()),42)
        for name, message in [('', 'hello'), ('A:B','hello'), ('A\nB','hello'), ('é'*9,'hi'), ('Test','x'*37), ('Test',' ')]:
            with self.assertRaises(ValueError):
                named_message(name,message)
        packet=encode(named_message('PC','Hello Test'),1,2222,0,0,1)
        self.assertIn('PC: Hello Test',describe(packet,2222))
    def test_known_crc_vector(self):
        self.assertEqual(crc(b'123456789'), 0xA1)

    def test_short_message_frame(self):
        packet = encode('AAAA', 1234567, 2222, 0, 6, 1)
        self.assertEqual(len(packet), 29)
        self.assertEqual(packet[:8], bytes.fromhex('02003c1900600030'))
        self.assertEqual(packet[8:16], bytes.fromhex('000008ae0012d687'))
        self.assertEqual(packet[17:], bytes.fromhex('0c0012d68700010041414141'))
        self.assertIn('AAAA', describe(packet, 2222))
        broken = bytearray(packet)
        broken[8] ^= 1
        self.assertIn('CRC failed', describe(broken, 2222))

    def test_limits_and_unicode(self):
        for text in ('', 'x' * 43, 'é' * 22):
            with self.assertRaises(ValueError):
                encode(text, 1, 2222, 0, 0, 0)
        for channel in (-1, 60):
            with self.assertRaises(ValueError):
                encode('hello', 1, 2222, channel, 0, 0)
        packet = encode('é' * 21, 1, 2222, 59, 31, 65535)
        self.assertEqual(len(packet), 67)

if __name__ == '__main__':
    unittest.main()
