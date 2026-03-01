import { describe, it, expect } from "vitest";
import { extractZipStore, createZipStore } from "../zip-utils";

function toBytes(str: string): Uint8Array {
  return Uint8Array.from(new TextEncoder().encode(str));
}

describe("zip-utils", () => {
  describe("round-trip: createZipStore -> extractZipStore", () => {
    it("creates a zip that can be extracted with matching contents", () => {
      const input = new Map<string, Uint8Array>();
      input.set("hello.txt", toBytes("Hello, world!"));
      input.set("data.bin", new Uint8Array([0x00, 0xff, 0x42, 0xab]));

      const zip = createZipStore(input);
      const output = extractZipStore(zip);

      expect(output.size).toBe(2);
      expect(Array.from(output.get("hello.txt")!)).toEqual(
        Array.from(toBytes("Hello, world!"))
      );
      expect(Array.from(output.get("data.bin")!)).toEqual(
        Array.from(new Uint8Array([0x00, 0xff, 0x42, 0xab]))
      );
    });

    it("handles an empty file map (valid empty zip)", () => {
      const input = new Map<string, Uint8Array>();
      const zip = createZipStore(input);
      const output = extractZipStore(zip);

      expect(output.size).toBe(0);
    });

    it("handles files with empty content", () => {
      const input = new Map<string, Uint8Array>();
      input.set("empty.txt", new Uint8Array(0));

      const zip = createZipStore(input);
      const output = extractZipStore(zip);

      expect(output.size).toBe(1);
      expect(output.get("empty.txt")!.length).toBe(0);
    });

    it("handles files with long names", () => {
      const input = new Map<string, Uint8Array>();
      const longName =
        "a/very/deep/nested/directory/structure/" + "x".repeat(200) + ".rom";
      input.set(longName, new Uint8Array([1, 2, 3]));

      const zip = createZipStore(input);
      const output = extractZipStore(zip);

      expect(output.size).toBe(1);
      expect(Array.from(output.get(longName)!)).toEqual([1, 2, 3]);
    });

    it("handles large binary data", () => {
      const input = new Map<string, Uint8Array>();
      const largeData = new Uint8Array(100_000);
      for (let i = 0; i < largeData.length; i++) {
        largeData[i] = i & 0xff;
      }
      input.set("large.bin", largeData);

      const zip = createZipStore(input);
      const output = extractZipStore(zip);

      expect(output.size).toBe(1);
      const extracted = output.get("large.bin")!;
      expect(extracted.length).toBe(largeData.length);
      // Compare a few spots + overall equality
      expect(extracted[0]).toBe(0);
      expect(extracted[255]).toBe(255);
      expect(extracted[256]).toBe(0);
      expect(Array.from(extracted)).toEqual(Array.from(largeData));
    });

    it("preserves multiple files with various sizes", () => {
      const input = new Map<string, Uint8Array>();
      input.set("empty", new Uint8Array(0));
      input.set("one-byte", new Uint8Array([0x42]));
      input.set("text.txt", toBytes("Some text content\nWith newlines\n"));
      input.set("binary.dat", new Uint8Array(1024).fill(0xaa));

      const zip = createZipStore(input);
      const output = extractZipStore(zip);

      expect(output.size).toBe(4);
      expect(output.get("empty")!.length).toBe(0);
      expect(Array.from(output.get("one-byte")!)).toEqual([0x42]);
      expect(new TextDecoder().decode(output.get("text.txt")!)).toBe(
        "Some text content\nWith newlines\n"
      );
      expect(output.get("binary.dat")!.length).toBe(1024);
      expect(output.get("binary.dat")![0]).toBe(0xaa);
      expect(output.get("binary.dat")![1023]).toBe(0xaa);
    });
  });

  describe("extractZipStore", () => {
    it("extracts files from a zip created by createZipStore", () => {
      const input = new Map<string, Uint8Array>();
      input.set("file1.txt", toBytes("content1"));
      input.set("file2.txt", toBytes("content2"));

      const zip = createZipStore(input);
      const result = extractZipStore(zip);

      expect(result.size).toBe(2);
      expect(new TextDecoder().decode(result.get("file1.txt"))).toBe(
        "content1"
      );
      expect(new TextDecoder().decode(result.get("file2.txt"))).toBe(
        "content2"
      );
    });

    it("throws on invalid data (not a zip)", () => {
      const garbage = new ArrayBuffer(100);
      expect(() => extractZipStore(garbage)).toThrow(
        "Invalid zip: end of central directory not found"
      );
    });
  });

  describe("createZipStore", () => {
    it("creates a valid zip ArrayBuffer", () => {
      const input = new Map<string, Uint8Array>();
      input.set("test.txt", toBytes("test"));

      const zip = createZipStore(input);
      expect(zip).toBeInstanceOf(ArrayBuffer);
      expect(zip.byteLength).toBeGreaterThan(0);

      // Verify the zip starts with a local file header signature
      const view = new DataView(zip);
      expect(view.getUint32(0, true)).toBe(0x04034b50);
    });

    it("creates an empty zip with only EOCD record", () => {
      const input = new Map<string, Uint8Array>();
      const zip = createZipStore(input);

      // Empty zip should be exactly 22 bytes (EOCD only)
      expect(zip.byteLength).toBe(22);

      const view = new DataView(zip);
      expect(view.getUint32(0, true)).toBe(0x06054b50);
    });
  });
});
