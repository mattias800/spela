#!/usr/bin/env python3
"""
Build the minimal CC0 placeholder ROMs that ship in `testdata-public/`
for platforms where no community PD ROM is readily available with a
clean license.

Outputs:
  testdata-public/tic80/spela-hello.tic        — minimal TIC-80 cart
  testdata-public/zxspectrum/spela-hello.tap   — minimal ZX Spectrum tape
  testdata-public/amstradcpc/spela-hello.dsk   — minimal Amstrad CPC disk

All generated files are authored by Spela contributors and dedicated to the
public domain (CC0); see testdata-public/ATTRIBUTION.md.

Re-run this script after editing if you change the BASIC text or the
cart-format details — the binary output is short enough to commit
verbatim, but keeping the recipe in version control makes the bytes
auditable.

Usage:
    python3 scripts/build-testdata-roms.py
"""
from __future__ import annotations
import struct
import sys
from pathlib import Path


REPO_ROOT = Path(__file__).resolve().parent.parent
TESTDATA = REPO_ROOT / "testdata-public"


# ── TIC-80 ────────────────────────────────────────────────────────────
#
# .tic format: a sequence of chunks. Each chunk has a 4-byte header
# [bank<<5|type, size_lo, size_hi, reserved] followed by `size` bytes
# of data. We emit a single CODE chunk (type 5) with uncompressed Lua.
# Reference: https://github.com/nesbox/TIC-80/wiki/.tic-File-Format
TIC80_CODE = (
    b"-- title: Spela CI Hello\n"
    b"-- author: Spela contributors\n"
    b"-- license: CC0\n"
    b"-- script: lua\n"
    b"t=0\n"
    b"function TIC()\n"
    b" cls(0)\n"
    b" print(\"SPELA TIC-80 OK\",16,60,12)\n"
    b" print(\"frames \"..t,16,72,11)\n"
    b" t=t+1\n"
    b"end\n"
)


def build_tic80() -> bytes:
    size = len(TIC80_CODE)
    if size >= 1 << 16:
        raise ValueError("TIC-80 CODE chunk would exceed 64 KiB")
    header = bytes([0x05, size & 0xFF, (size >> 8) & 0xFF, 0])
    return header + TIC80_CODE


# ── ZX Spectrum ────────────────────────────────────────────────────────
#
# .tap format: a sequence of blocks. Each block is
# [length:LE16] [flag:u8] [data:N] [xor_checksum:u8]
# where the XOR covers the flag byte plus the data bytes.
# Reference: https://faqwiki.zxnet.co.uk/wiki/TAP_format
#
# A BASIC program ships as two blocks:
#   1. Header block (flag=0, 17 bytes of metadata)
#   2. Data block  (flag=255, the tokenised BASIC line)
#
# Our line:  10 BORDER 2 : PAPER 2 : CLS : PRINT "SPELA OK"
# Tokens (Sinclair Spectrum):
#   PRINT  0xF5   BORDER 0xE7   PAPER 0xDA   CLS 0xFB
#   ":"    0x3A   "\""   0x22   end-of-line  0x0D
#
# Numeric literals embed both the ASCII digits and a 5-byte float
# (marker 0x0E) so the interpreter can read either form.

def _spectrum_int(n: int) -> bytes:
    """Sinclair BASIC's 5-byte 'small integer' representation, as used
    in the on-tape encoding immediately following the visible ASCII
    digits of the literal."""
    return bytes([0x0E, 0x00, 0x00, n & 0xFF, (n >> 8) & 0xFF, 0x00])


def _zx_basic_line() -> bytes:
    body = (
        bytes([0xE7]) + b"2" + _spectrum_int(2)         # BORDER 2
        + bytes([0x3A, 0xDA]) + b"2" + _spectrum_int(2) # : PAPER 2
        + bytes([0x3A, 0xFB])                           # : CLS
        + bytes([0x3A, 0xF5, 0x22]) + b"SPELA OK" + bytes([0x22])  # : PRINT "SPELA OK"
        + bytes([0x0D])                                 # end-of-line
    )
    line_no = 10
    return struct.pack(">H", line_no) + struct.pack("<H", len(body)) + body


def _tap_block(flag: int, data: bytes) -> bytes:
    body = bytes([flag]) + data
    xor = 0
    for b in body:
        xor ^= b
    body += bytes([xor])
    return struct.pack("<H", len(body)) + body


def build_zxs_tap() -> bytes:
    program = _zx_basic_line()
    prog_len = len(program)

    filename = b"SPELA OK  "  # 10 bytes, space-padded
    header = (
        bytes([0x00])                # type 0 = BASIC program
        + filename
        + struct.pack("<H", prog_len)
        + struct.pack("<H", 10)      # autostart line 10
        + struct.pack("<H", prog_len)  # variables area offset = end of program
    )
    assert len(header) == 17

    return _tap_block(0x00, header) + _tap_block(0xFF, program)


# ── Amstrad CPC ────────────────────────────────────────────────────────
#
# We emit an extended CPC DSK with a single materialized Data-format
# track. The extended header marks the remaining tracks as absent, keeping
# the fixture tiny while still advertising the standard CPC geometry that
# AMSDOS format detection expects.
#
# DSK reference: https://cpctech.cpcwiki.de/docs/dsk.html
# CPC Data format: first sector 0xC1, 40 tracks, 9 sectors/track,
# 512-byte sectors, no reserved tracks, 1 KiB blocks, 2 directory blocks.
CPC_TRACK_SIZE = 0x1300
CPC_SECTOR_SIZE = 512
CPC_SECTORS_PER_TRACK = 9
CPC_TRACKS = 40


def _cpc_basic_line(line_no: int, body: bytes) -> bytes:
    line = struct.pack("<H", line_no) + body + b"\x00"
    return struct.pack("<H", len(line) + 2) + line


def _cpc_basic_program() -> bytes:
    return (
        _cpc_basic_line(10, bytes([0xBF]) + b' "SPELA CPC OK"')
        + b"\x00\x00"
    )


def _amsdos_basic_file(program: bytes) -> bytes:
    header = bytearray(128)
    header[0] = 0
    header[1:9] = b"SPELA   "
    header[9:12] = b"BAS"
    header[18] = 0x00  # unprotected BASIC
    header[21:23] = struct.pack("<H", 0x0170)
    header[24:26] = struct.pack("<H", len(program))
    header[26:28] = struct.pack("<H", 0x0000)
    header[64:67] = bytes([len(program) & 0xFF, (len(program) >> 8) & 0xFF, 0])
    checksum = sum(header[0:67]) & 0xFFFF
    header[67:69] = struct.pack("<H", checksum)
    return bytes(header) + program


def _cpc_directory_entry(file_bytes: bytes) -> bytes:
    entry = bytearray(32)
    entry[0] = 0x00
    entry[1:9] = b"SPELA   "
    entry[9:12] = b"BAS"
    entry[15] = (len(file_bytes) + 127) // 128
    entry[16] = 0x02  # allocation block 2; blocks 0-1 hold the directory
    return bytes(entry)


def _build_cpc_track(file_bytes: bytes) -> bytes:
    track = bytearray([0xE5] * CPC_TRACK_SIZE)
    track[0:0x100] = bytes(0x100)
    track[0:12] = b"Track-Info\r\n"
    track[0x10] = 0
    track[0x11] = 0
    track[0x14] = 2  # 512-byte sectors
    track[0x15] = CPC_SECTORS_PER_TRACK
    track[0x16] = 0x2A
    track[0x17] = 0xE5

    for index in range(CPC_SECTORS_PER_TRACK):
        offset = 0x18 + index * 8
        track[offset + 0] = 0
        track[offset + 1] = 0
        track[offset + 2] = 0xC1 + index
        track[offset + 3] = 2
        track[offset + 4] = 0
        track[offset + 5] = 0
        track[offset + 6:offset + 8] = struct.pack("<H", CPC_SECTOR_SIZE)

    sector_data = 0x100
    directory_start = sector_data
    track[directory_start:directory_start + 32] = _cpc_directory_entry(file_bytes)

    file_start = sector_data + 4 * CPC_SECTOR_SIZE
    if len(file_bytes) > 2 * CPC_SECTOR_SIZE:
        raise ValueError("CPC BASIC fixture no longer fits in one allocation block")
    track[file_start:file_start + len(file_bytes)] = file_bytes
    return bytes(track)


def build_cpc_dsk() -> bytes:
    file_bytes = _amsdos_basic_file(_cpc_basic_program())
    header = bytearray(0x100)
    header[0:34] = b"EXTENDED CPC DSK File\r\nDisk-Info\r\n"
    header[0x22:0x30] = b"Spela CI      "
    header[0x30] = CPC_TRACKS
    header[0x31] = 1
    header[0x34] = CPC_TRACK_SIZE >> 8
    return bytes(header) + _build_cpc_track(file_bytes)


def write(path: Path, data: bytes) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(data)
    print(f"  wrote {path.relative_to(REPO_ROOT)} ({len(data)} bytes)")


def main() -> int:
    print("Building testdata-public ROMs:")
    write(TESTDATA / "tic80" / "spela-hello.tic", build_tic80())
    write(TESTDATA / "zxspectrum" / "spela-hello.tap", build_zxs_tap())
    write(TESTDATA / "amstradcpc" / "spela-hello.dsk", build_cpc_dsk())
    return 0


if __name__ == "__main__":
    sys.exit(main())
