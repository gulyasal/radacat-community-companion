"""C1 short public text framing, equivalent to Android companion 0.2."""
import struct
import re
import math

def coordinate(latitude, longitude):
    try:
        lat,lon=float(latitude),float(longitude)
    except (TypeError,ValueError):
        raise ValueError('Enter latitude and longitude as decimal degrees, e.g. 52.52000 and 13.40500.')
    if not math.isfinite(lat) or not math.isfinite(lon) or abs(lat)>90 or abs(lon)>180:
        raise ValueError('Latitude must be -90 to 90; longitude must be -180 to 180.')
    return f'geo:{lat:.5f},{lon:.5f}'

def map_links(text):
    for match in re.finditer(r'geo:(-?\d{1,2}\.\d{5}),(-?\d{1,3}\.\d{5})',text):
        latitude,longitude=match.groups()
        if abs(float(latitude))<=90 and abs(float(longitude))<=180:
            yield match.start(),match.end(),'https://www.google.com/maps/search/?api=1&query='+latitude+','+longitude

def named_message(name, message):
    name = name.strip()
    if not name or any(c in name for c in ':\r\n'):
        raise ValueError('Enter a name without colons or line breaks.')
    if len(name.encode('utf-8')) > 16:
        raise ValueError('Keep your name within 16 UTF-8 bytes.')
    if not message.strip():
        raise ValueError('Enter a message.')
    result = name + ': ' + message
    size = len(result.encode('utf-8'))
    if size > 42:
        raise ValueError(f'Name and message use {size} of 42 UTF-8 bytes. Shorten the message.')
    return result

def crc(data):
    value = 0
    for byte in data:
        value ^= byte
        for _ in range(8):
            value = (value >> 1) ^ (0x8C if value & 1 else 0)
    return value

def encode(text, source, group, channel, sequence, message_id):
    value = text.encode('utf-8')
    if not 1 <= len(value) <= 42:
        raise ValueError('Use 1–42 UTF-8 bytes per message.')
    if not 0 <= channel <= 59:
        raise ValueError('Channel must be 0–59.')
    if not 1 <= group <= 0x7fffffff or not 1 <= source <= 0x7fffffff:
        raise ValueError('Group and sender IDs must be positive 32-bit integers.')
    header = bytes([0, 0x60]) + struct.pack('<H', (sequence & 31) << 11) + struct.pack('>II', group, source)
    return bytes([2, channel, channel + 60, len(value) + 21]) + header + bytes([crc(header), len(value) + 8]) + struct.pack('>I', source) + b'\0' + struct.pack('<H', message_id & 65535) + value

def describe(packet, group):
    if len(packet) == 8 and packet[0] == 4:
        return 'Device status response; delivery unconfirmed'
    if len(packet) < 17:
        return 'Unparsed data'
    if packet[3] + 4 != len(packet):
        return 'Invalid packet length; retained for analysis'
    if crc(packet[4:16]) != packet[16]:
        return 'Header CRC failed; retained for analysis'
    kind, subtype = (packet[4] >> 4) & 3, packet[4] & 15
    destination, source = struct.unpack('>II', packet[8:16])
    seq = struct.unpack('<H', packet[6:8])[0] >> 11
    summary = f'Channel {packet[1]} · type {kind}/{subtype} · seq {seq} · {source} → {destination}'
    if kind == 0 and subtype == 0 and len(packet) >= 25 and not packet[5] & 128 and packet[17] == len(packet) - 17 and destination == group:
        try:
            text = packet[25:].decode('utf-8', errors='strict')
            if text and all(c.isprintable() or c in '\r\n\t' for c in text):
                summary += '\nPossible public text (unencrypted mode): ' + text
        except UnicodeDecodeError:
            pass
    return summary
