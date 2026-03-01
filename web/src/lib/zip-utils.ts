const CRC_TABLE = new Uint32Array(256);
for (let i = 0; i < 256; i++) {
  let c = i;
  for (let j = 0; j < 8; j++) {
    c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
  }
  CRC_TABLE[i] = c;
}

function crc32(data: Uint8Array): number {
  let crc = 0xffffffff;
  for (let i = 0; i < data.length; i++) {
    crc = CRC_TABLE[(crc ^ data[i]) & 0xff] ^ (crc >>> 8);
  }
  return (crc ^ 0xffffffff) >>> 0;
}

const LOCAL_FILE_HEADER_SIG = 0x04034b50;
const CENTRAL_DIR_SIG = 0x02014b50;
const END_OF_CENTRAL_DIR_SIG = 0x06054b50;

const encoder = new TextEncoder();
const decoder = new TextDecoder();

export function extractZipStore(data: ArrayBuffer): Map<string, Uint8Array> {
  const view = new DataView(data);
  const files = new Map<string, Uint8Array>();

  // Find end of central directory record by scanning backwards
  let eocdOffset = -1;
  for (let i = data.byteLength - 22; i >= 0; i--) {
    if (view.getUint32(i, true) === END_OF_CENTRAL_DIR_SIG) {
      eocdOffset = i;
      break;
    }
  }
  if (eocdOffset === -1) {
    throw new Error("Invalid zip: end of central directory not found");
  }

  const totalEntries = view.getUint16(eocdOffset + 10, true);
  const cdOffset = view.getUint32(eocdOffset + 16, true);

  // Parse central directory entries to get local header offsets
  let offset = cdOffset;
  for (let i = 0; i < totalEntries; i++) {
    const sig = view.getUint32(offset, true);
    if (sig !== CENTRAL_DIR_SIG) {
      throw new Error("Invalid zip: bad central directory signature");
    }

    const method = view.getUint16(offset + 10, true);
    if (method !== 0) {
      throw new Error(
        `Unsupported compression method ${method}, only STORE (0) is supported`
      );
    }

    const fileNameLen = view.getUint16(offset + 28, true);
    const extraLen = view.getUint16(offset + 30, true);
    const commentLen = view.getUint16(offset + 32, true);
    const localHeaderOffset = view.getUint32(offset + 42, true);
    const fileName = decoder.decode(
      new Uint8Array(data, offset + 46, fileNameLen)
    );

    // Read data from local file header
    const localSig = view.getUint32(localHeaderOffset, true);
    if (localSig !== LOCAL_FILE_HEADER_SIG) {
      throw new Error("Invalid zip: bad local file header signature");
    }
    const localFileNameLen = view.getUint16(localHeaderOffset + 26, true);
    const localExtraLen = view.getUint16(localHeaderOffset + 28, true);
    const compressedSize = view.getUint32(localHeaderOffset + 18, true);
    const dataStart = localHeaderOffset + 30 + localFileNameLen + localExtraLen;

    const fileData = new Uint8Array(compressedSize);
    fileData.set(new Uint8Array(data, dataStart, compressedSize));
    files.set(fileName, fileData);

    offset += 46 + fileNameLen + extraLen + commentLen;
  }

  return files;
}

export function createZipStore(
  files: Map<string, Uint8Array>
): ArrayBuffer {
  // Calculate total size
  let localHeadersSize = 0;
  let centralDirSize = 0;
  const entries: Array<{
    name: Uint8Array;
    data: Uint8Array;
    crc: number;
  }> = [];

  for (const [name, fileData] of files) {
    const nameBytes = encoder.encode(name);
    entries.push({ name: nameBytes, data: fileData, crc: crc32(fileData) });
    localHeadersSize += 30 + nameBytes.length + fileData.length;
    centralDirSize += 46 + nameBytes.length;
  }

  const eocdSize = 22;
  const totalSize = localHeadersSize + centralDirSize + eocdSize;
  const buffer = new ArrayBuffer(totalSize);
  const view = new DataView(buffer);
  const bytes = new Uint8Array(buffer);

  // Write local file headers + data
  let offset = 0;
  const localOffsets: number[] = [];

  for (const entry of entries) {
    localOffsets.push(offset);

    view.setUint32(offset, LOCAL_FILE_HEADER_SIG, true);
    view.setUint16(offset + 4, 20, true); // version needed
    view.setUint16(offset + 6, 0, true); // flags
    view.setUint16(offset + 8, 0, true); // method STORE
    view.setUint16(offset + 10, 0, true); // mod time
    view.setUint16(offset + 12, 0, true); // mod date
    view.setUint32(offset + 14, entry.crc, true); // crc-32
    view.setUint32(offset + 18, entry.data.length, true); // compressed size
    view.setUint32(offset + 22, entry.data.length, true); // uncompressed size
    view.setUint16(offset + 26, entry.name.length, true); // filename length
    view.setUint16(offset + 28, 0, true); // extra length
    bytes.set(entry.name, offset + 30);
    bytes.set(entry.data, offset + 30 + entry.name.length);

    offset += 30 + entry.name.length + entry.data.length;
  }

  // Write central directory
  const cdStart = offset;
  for (let i = 0; i < entries.length; i++) {
    const entry = entries[i];

    view.setUint32(offset, CENTRAL_DIR_SIG, true);
    view.setUint16(offset + 4, 20, true); // version made by
    view.setUint16(offset + 6, 20, true); // version needed
    view.setUint16(offset + 8, 0, true); // flags
    view.setUint16(offset + 10, 0, true); // method STORE
    view.setUint16(offset + 12, 0, true); // mod time
    view.setUint16(offset + 14, 0, true); // mod date
    view.setUint32(offset + 16, entry.crc, true); // crc-32
    view.setUint32(offset + 20, entry.data.length, true); // compressed size
    view.setUint32(offset + 24, entry.data.length, true); // uncompressed size
    view.setUint16(offset + 28, entry.name.length, true); // filename length
    view.setUint16(offset + 30, 0, true); // extra length
    view.setUint16(offset + 32, 0, true); // comment length
    view.setUint16(offset + 34, 0, true); // disk start
    view.setUint16(offset + 36, 0, true); // internal attrs
    view.setUint32(offset + 38, 0, true); // external attrs
    view.setUint32(offset + 42, localOffsets[i], true); // local header offset
    bytes.set(entry.name, offset + 46);

    offset += 46 + entry.name.length;
  }

  // Write end of central directory
  const cdSize = offset - cdStart;
  view.setUint32(offset, END_OF_CENTRAL_DIR_SIG, true);
  view.setUint16(offset + 4, 0, true); // disk number
  view.setUint16(offset + 6, 0, true); // disk with CD
  view.setUint16(offset + 8, entries.length, true); // entries on disk
  view.setUint16(offset + 10, entries.length, true); // total entries
  view.setUint32(offset + 12, cdSize, true); // CD size
  view.setUint32(offset + 16, cdStart, true); // CD offset
  view.setUint16(offset + 20, 0, true); // comment length

  return buffer;
}
