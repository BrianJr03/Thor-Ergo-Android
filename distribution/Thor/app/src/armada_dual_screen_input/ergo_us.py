"""Standard US evdev character map; all 95 printable ASCII characters.

These are physical key events, as in the original English input path. The
receiving desktop/game must use a US keyboard map. We do not change its layout.
"""
from __future__ import annotations

LETTER_CODES = dict(zip('qwertyuiop', range(16, 26)))
LETTER_CODES.update(zip('asdfghjkl', range(30, 39)))
LETTER_CODES.update(zip('zxcvbnm', range(44, 51)))
US_MAP = {}
for char, code in LETTER_CODES.items():
    US_MAP[char] = (code, ())
    US_MAP[char.upper()] = (code, (42,))
for low, high, code in zip('1234567890-=', '!@#$%^&*()_+', range(2, 14)):
    US_MAP[low] = (code, ())
    US_MAP[high] = (code, (42,))
for low, high, code in [('`', '~', 41), ('[', '{', 26), (']', '}', 27), ('\\', '|', 43),
                       (';', ':', 39), ("'", '"', 40), (',', '<', 51), ('.', '>', 52),
                       ('/', '?', 53), (' ', ' ', 57)]:
    US_MAP[low] = (code, ())
    if high != low:
        US_MAP[high] = (code, (42,))

# Caps Lock changes letters only. This map is used for momentary Shift only.
US_SHIFT = dict(zip('`1234567890-=[]\\;\',./', '~!@#$%^&*()_+{}|:"<>?'))
