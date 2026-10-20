#!/usr/bin/env python3
"""Rewrites java.lang.StringBuilder references in compiled classes to java.lang.StringBuffer.

Why this exists
---------------
CLDC 1.1 has StringBuffer but no StringBuilder; StringBuilder arrived with Java 5. javac has
compiled string concatenation ("a" + b) into StringBuilder since then, and there is no flag to
make it emit StringBuffer again -- the oldest -source/-target a current javac accepts is 8.

The consequence is that every concatenation in the sources produces bytecode a real handset
cannot load, even though the sources themselves contain no StringBuilder of their own. It was
invisible before: --release 8 resolves java.* from the desktop JDK, which has both classes, so
the build passed and the emulator (desktop Java) ran it fine.

StringBuffer is a drop-in for the StringBuilder that javac generates. javac only ever emits
new StringBuilder(), the append overloads, append(Object) and toString(), and every one of those
exists on StringBuffer with the same signature apart from the return type naming the class
itself. Rewriting the name in the constant pool fixes the class references and the method
descriptors together, because a descriptor is just another UTF8 constant holding the name as a
substring.

Class files carry no absolute offsets into the constant pool -- entries refer to each other by
index -- so a UTF8 constant may safely shrink or grow here.
"""

import struct
import sys

OLD = b"java/lang/StringBuilder"
NEW = b"java/lang/StringBuffer"


def rewrite(data):
    """Returns class-file bytes with every StringBuilder name swapped for StringBuffer."""
    if data[:4] != b"\xca\xfe\xba\xbe":
        raise ValueError("not a class file")

    # Constant pool starts at byte 8; entry 0 is reserved, so the count is one past the last index.
    count = struct.unpack(">H", data[8:10])[0]
    out = bytearray(data[:10])
    i = 10
    index = 1
    while index < count:
        tag = data[i]
        if tag == 1:  # CONSTANT_Utf8: the only place the name appears as text
            length = struct.unpack(">H", data[i + 1:i + 3])[0]
            text = bytes(data[i + 3:i + 3 + length])
            if OLD in text:
                text = text.replace(OLD, NEW)
            out += struct.pack(">BH", tag, len(text))
            out += text
            i += 3 + length
        elif tag in (5, 6):  # Long and Double take two pool slots
            out += data[i:i + 9]
            i += 9
            index += 1
        elif tag in (7, 8, 16, 19, 20):  # Class, String, MethodType, Module, Package
            out += data[i:i + 3]
            i += 3
        elif tag == 15:  # MethodHandle
            out += data[i:i + 4]
            i += 4
        elif tag in (3, 4, 9, 10, 11, 12, 17, 18):  # numbers, refs, NameAndType, dynamic
            out += data[i:i + 5]
            i += 5
        else:
            raise ValueError("unknown constant pool tag %d at offset %d" % (tag, i))
        index += 1

    out += data[i:]
    return bytes(out)


def main(argv):
    if len(argv) < 2:
        print("usage: stringbuilder_to_stringbuffer.py <class-or-dir>...", file=sys.stderr)
        return 2

    import os
    changed = 0
    seen = 0
    for target in argv[1:]:
        paths = []
        if os.path.isdir(target):
            for root, _, names in os.walk(target):
                paths += [os.path.join(root, n) for n in names if n.endswith(".class")]
        elif target.endswith(".class"):
            paths.append(target)

        for path in paths:
            seen += 1
            with open(path, "rb") as fh:
                original = fh.read()
            if OLD not in original:
                continue
            rewritten = rewrite(original)
            with open(path, "wb") as fh:
                fh.write(rewritten)
            changed += 1

    print("stringbuilder->stringbuffer: %d of %d class files rewritten" % (changed, seen))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
