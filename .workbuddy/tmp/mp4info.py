import struct, sys

path = sys.argv[1]
data = open(path, "rb").read()
print("size", len(data))


def walk(buf, start, end, depth=0, want=("moov", "trak", "mdia", "minf", "stbl", "mvhd", "mdhd", "stsd", "hdlr")):
    i = start
    while i + 8 <= end:
        size = struct.unpack(">I", buf[i:i + 4])[0]
        typ = buf[i + 4:i + 8].decode("latin1")
        if size == 0:
            size = end - i
        if size < 8:
            break
        if typ in want:
            extra = ""
            if typ == "mvhd":
                ver = buf[i + 8]
                off = i + 12 + (16 if ver == 1 else 8)
                if ver == 1:
                    ts, dur = struct.unpack(">IQ", buf[off:off + 12])
                else:
                    ts, dur = struct.unpack(">II", buf[off:off + 8])
                extra = f" timescale={ts} duration={dur} ({dur / ts:.2f}s)"
            if typ == "mdhd":
                ver = buf[i + 8]
                off = i + 12 + (16 if ver == 1 else 8)
                if ver == 1:
                    ts, dur = struct.unpack(">IQ", buf[off:off + 12])
                else:
                    ts, dur = struct.unpack(">II", buf[off:off + 8])
                extra = f" timescale={ts} duration={dur} ({dur / ts:.2f}s)"
            if typ == "stsd":
                n = struct.unpack(">I", buf[i + 12:i + 16])[0]
                extra = f" entries={n}"
            if typ == "hdlr":
                extra = " handler=" + buf[i + 16:i + 20].decode("latin1")
            print("  " * depth + f"{typ} size={size}{extra}")
        if typ in ("moov", "trak", "mdia", "minf", "stbl"):
            walk(buf, i + 8, i + size, depth + 1)
        i += size


walk(data, 0, len(data))
# 采样分辨率：在 stsd 里找 avc1
idx = 0
while True:
    idx = data.find(b"avc1", idx + 1)
    if idx < 0:
        break
    w, h = struct.unpack(">HH", data[idx + 28:idx + 32])
    print("avc1 dims", w, "x", h)
