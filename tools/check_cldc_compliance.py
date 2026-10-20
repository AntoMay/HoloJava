#!/usr/bin/env python3
"""Checks compiled classes against the CLDC/MIDP stubs the phone actually ships.

Why a bytecode check rather than a compile-time one
---------------------------------------------------
Compiling with the stubs as the boot classpath looks like the obvious guard, and it does catch
hand-written violations. It cannot be used here: javac has compiled string concatenation
("a" + b) into java.lang.StringBuilder since Java 5, and CLDC has no StringBuilder. A boot-class
compile therefore rejects every concatenation in the sources and cannot succeed at all.

The class files can still be checked, and checking them is strictly stronger: it covers what the
programmer wrote *and* what the compiler emitted. tools/stringbuilder_to_stringbuffer.py runs
first to rewrite the generated StringBuilder to StringBuffer; this then verifies that nothing
reaching the phone names a class or a member the stubs do not have.

What is checked, and why each form matters
------------------------------------------
Three kinds of reference are resolved, because each catches different bugs:

  1. CONSTANT_Class entries          -- `new ArrayList()`, `checkcast`, `instanceof`, and every
                                        array type. This is the shape WikiCatalogData shipped in.
  2. Field/method references         -- `s.matches(...)`, `Math.atan2(...)`, `list.size()`.
     (resolved through NameAndType)
  3. Class names inside Utf8         -- a field or parameter typed ArrayList that is only ever
     descriptors                       passed around never appears as CONSTANT_Class, yet the
                                        descriptor still names a class the handset must load.
                                        Array types ([Ljava/util/ArrayList;) and generic
                                        Signature attributes only show up here too.

Inherited members are resolved through the superclass chain: javac records the *receiver's static
type* as the reference owner, so `vec.hashCode()` is a reference to java/util/Vector.hashCode
even though Vector inherits it from Object. Checking only the declaring class would reject every
such call.

Only java.* and javax.* are checked: everything under me.* is the game's own code, and a name
that is neither (a string literal that happens to look like a descriptor, say) is ignored.

The allowed set is read from the stub JARs themselves rather than a hand-written list, so it
cannot drift out of step with the pinned dependencies.

Usage: check_cldc_compliance.py <stub.jar> [...] -- <class-dir | class-file | artifact.jar> [...]

The `--` is required: it separates the stub JARs from the checked input, which may itself be a
.jar (a packaged artifact), and without it a checked archive would be mistaken for a stub.
"""

import os
import struct
import sys
import zipfile

# Constant pool tags, by the numbers used in the class-file format.
UTF8, CLASS, FIELDREF, METHODREF = 1, 7, 9, 10
INTERFACEMETHODREF, NAMEANDTYPE = 11, 12


def read_constant_pool(data):
    """Parses a class file's constant pool.

    Returns (entries, after_pool_offset). entries maps a pool index to a decoded string (Utf8),
    a ("class", name_index) tuple, or a ("ref", class_index, nat_index) tuple; every other kind
    maps to None but keeps its index in step. Long and Double occupy two slots.
    """
    if data[:4] != b"\xca\xfe\xba\xbe":
        raise ValueError("not a class file")
    count = struct.unpack(">H", data[8:10])[0]
    entries = [None] * count
    i = 10
    index = 1
    while index < count:
        tag = data[i]
        if tag == UTF8:
            length = struct.unpack(">H", data[i + 1:i + 3])[0]
            entries[index] = data[i + 3:i + 3 + length].decode("utf-8", "replace")
            i += 3 + length
        elif tag in (5, 6):  # Long and Double occupy two slots
            i += 9
            index += 1
        elif tag == CLASS:
            entries[index] = ("class", struct.unpack(">H", data[i + 1:i + 3])[0])
            i += 3
        elif tag in (FIELDREF, METHODREF, INTERFACEMETHODREF):
            c, n = struct.unpack(">HH", data[i + 1:i + 5])
            entries[index] = ("ref", c, n)
            i += 5
        elif tag == NAMEANDTYPE:
            n, d = struct.unpack(">HH", data[i + 1:i + 5])
            entries[index] = ("nat", n, d)
            i += 5
        elif tag in (3, 4):  # Integer, Float
            i += 5
        elif tag in (8, 16, 19, 20):  # String, MethodType, Module, Package
            i += 3
        elif tag == 15:  # MethodHandle
            i += 4
        elif tag in (17, 18):  # Dynamic, InvokeDynamic
            i += 5
        else:
            raise ValueError("unknown constant pool tag %d" % tag)
        index += 1
    return entries, i


def tag_of(entry):
    """Returns an entry's kind, or None for anything that is not a tagged tuple.

    Utf8 entries are plain strings -- including the empty string, which does occur in real
    constant pools -- so indexing entry[0] blindly reads the first *character* of a name, or
    raises on "".
    """
    if isinstance(entry, tuple):
        return entry[0]
    return None


def class_name(entries, class_index):
    """Resolves a CONSTANT_Class index to its text name (may be an array descriptor)."""
    entry = entries[class_index]
    if tag_of(entry) != "class":
        return None
    return entries[entry[1]]


def platform_type(name):
    """Maps a class name or descriptor to a java.*/javax.* binary name, else None.

    Array forms are unwrapped first: "[[Ljava/util/ArrayList;" denotes java/util/ArrayList and
    must be checked like any other reference, while "[I" is a primitive array and denotes no
    class at all. Names from the game's own packages, and primitive descriptors such as "I" or
    "V", return None and are skipped.
    """
    if not name:
        return None
    while name.startswith("["):
        name = name[1:]
    if name.startswith("L") and name.endswith(";"):
        name = name[1:-1]
    if name.startswith("java/") or name.startswith("javax/"):
        return name
    return None


def read_class(data, entries):
    """Reads a class file header: (name, super_name, interface_names, members).

    members are "name descriptor" strings for every field and method this class *declares*;
    the superclass and interface names are what make inherited members resolvable later.
    """
    _, i = read_constant_pool(data)
    i += 2  # access_flags
    this_class = struct.unpack(">H", data[i:i + 2])[0]
    owner = class_name(entries, this_class)
    super_index = struct.unpack(">H", data[i + 2:i + 4])[0]
    super_name = class_name(entries, super_index) if super_index != 0 else None
    i += 4  # this_class, super_class
    interface_count = struct.unpack(">H", data[i:i + 2])[0]
    i += 2
    interface_names = []
    for _ in range(interface_count):
        interface_index = struct.unpack(">H", data[i:i + 2])[0]
        i += 2
        name = class_name(entries, interface_index)
        if name:
            interface_names.append(name)

    members = []
    for _ in range(2):  # fields, then methods
        count = struct.unpack(">H", data[i:i + 2])[0]
        i += 2
        for _ in range(count):
            i += 2  # access_flags
            name_index, desc_index = struct.unpack(">HH", data[i:i + 4])
            i += 4
            attributes = struct.unpack(">H", data[i:i + 2])[0]
            i += 2
            for _ in range(attributes):
                i += 2
                length = struct.unpack(">I", data[i:i + 4])[0]
                i += 4 + length
            members.append("%s %s" % (entries[name_index], entries[desc_index]))
    return owner, super_name, interface_names, members


def index_stub(jar_path):
    """Builds (classes, members, parents) across one stub JAR.

    classes  -- every binary name the JAR declares (its own .class entries plus whatever the
                parsed headers name, which are the same thing said twice on purpose).
    members  -- {class: {"name descriptor"}} of *declared* fields and methods.
    parents  -- {class: [superclass, *interfaces]} for walking inheritance.
    """
    classes, members, parents = set(), {}, {}
    with zipfile.ZipFile(jar_path) as jar:
        for info in jar.infolist():
            if not info.filename.endswith(".class"):
                continue
            data = jar.read(info)
            entries, _ = read_constant_pool(data)
            name, super_name, interface_names, own = read_class(data, entries)
            if name:
                classes.add(name)
            classes.add(info.filename[:-len(".class")])
            if name:
                members[name] = set(own)
                chain = []
                if super_name:
                    chain.append(super_name)
                chain.extend(interface_names)
                parents[name] = chain
    return classes, members, parents


def declares_member(members, parents, owner, member):
    """True when owner or any class it inherits from declares the member.

    Stubs are complete for their own hierarchies, but a reference's owner is the receiver's
    static type, not the declaring class -- Vector.hashCode is declared on Object -- so the
    lookup has to climb. Seen-set guards against a malformed cycle.
    """
    seen = set()
    queue = [owner]
    while queue:
        current = queue.pop(0)
        if current in seen:
            continue
        seen.add(current)
        if member in members.get(current, ()):
            return True
        queue.extend(parents.get(current, ()))
    return False


DESCRIPTOR_CLASS = __import__("re").compile(r"L[a-zA-Z0-9_$/]+;")


def descriptor_classes(text):
    """Yields class names named by L...; forms inside a Utf8 constant.

    Covers field/method descriptors, generic Signature attributes, and string constants that
    hold descriptors. platform_type() drops everything that is not a platform class, so an
    ordinary string literal cannot produce a finding.
    """
    for match in DESCRIPTOR_CLASS.finditer(text):
        yield match.group(0)[1:-1]


def referenced(entries):
    """Collects (classes, member_refs, descriptor_names) a class file uses.

    classes        -- binary names from CONSTANT_Class, arrays included.
    member_refs    -- (owner, "name descriptor") from the reference kinds, owner unresolved.
    descriptor_names-- platform class names seen only inside Utf8 descriptor text.
    """
    classes = set()
    member_refs = set()
    descriptors = set()
    for entry in entries:
        kind = tag_of(entry)
        if kind == "class":
            name = entries[entry[1]]
            if name:
                classes.add(name)
        elif kind == "ref":
            _, class_index, nat_index = entry
            nat = entries[nat_index]
            if tag_of(nat) != "nat":
                continue
            owner = class_name(entries, class_index)
            name, desc = entries[nat[1]], entries[nat[2]]
            if owner and name and desc:
                member_refs.add((owner, "%s %s" % (name, desc)))
        elif isinstance(entry, str):
            for name in descriptor_classes(entry):
                resolved = platform_type(name)
                if resolved:
                    descriptors.add(resolved)
    return classes, member_refs, descriptors


def collect_class_files(paths):
    """Expands directories, .class files, and packaged .jar/.zip artifacts to entries.

    Yields (label, loader) pairs; the loader returns the bytes lazily so a failing parse names
    the exact entry rather than dying during collection.
    """
    entries = []
    for path in paths:
        if os.path.isdir(path):
            for root, _, names in os.walk(path):
                for name in sorted(names):
                    if name.endswith(".class"):
                        full = os.path.join(root, name)
                        entries.append((full, lambda p=full: open(p, "rb").read()))
        elif path.endswith(".class"):
            entries.append((path, lambda p=path: open(p, "rb").read()))
        elif path.endswith((".jar", ".zip")):
            with zipfile.ZipFile(path) as archive:
                for info in sorted(archive.infolist(), key=lambda i: i.filename):
                    if info.filename.endswith(".class"):
                        entries.append(
                            ("%s!%s" % (path, info.filename),
                             lambda a=path, n=info.filename: zipfile.ZipFile(a).read(n)))
        else:
            raise ValueError("not a class file, directory, or archive: %s" % path)
    return entries


def main(argv):
    args = list(argv[1:])
    if "--" not in args:
        print("usage: check_cldc_compliance.py <stub.jar> [...] -- "
              "<class-dir | class-file | artifact.jar> [...]", file=sys.stderr)
        return 2
    split = args.index("--")
    stub_jars, targets = args[:split], args[split + 1:]
    if not stub_jars or not targets:
        print("usage: check_cldc_compliance.py <stub.jar> [...] -- "
              "<class-dir | class-file | artifact.jar> [...]", file=sys.stderr)
        return 2

    stub_classes = set()
    stub_members = {}
    stub_parents = {}
    for jar in stub_jars:
        classes, members, parents = index_stub(jar)
        stub_classes |= classes
        stub_members.update(members)
        stub_parents.update(parents)

    problems = []
    checked = 0
    for label, load in collect_class_files(targets):
        data = load()
        entries, _ = read_constant_pool(data)
        classes, member_refs, descriptors = referenced(entries)
        checked += 1

        # Class references: new/checkcast/instanceof/arrays, plus names that appear only in
        # descriptor text. A missing owner already reports as a missing class, so the member
        # check below is skipped for it rather than reported twice.
        for name in sorted(classes | descriptors):
            resolved = platform_type(name)
            if resolved and resolved not in stub_classes:
                problems.append("%s: class %s is not in the CLDC/MIDP stubs"
                                % (label, resolved.replace("/", ".")))

        for owner, member in sorted(member_refs):
            resolved_owner = platform_type(owner)
            if not resolved_owner or resolved_owner not in stub_members:
                continue  # not a platform class, or already flagged as a missing class
            if not declares_member(stub_members, stub_parents, resolved_owner, member):
                problems.append("%s: %s.%s is not in the CLDC/MIDP stubs"
                                % (label, resolved_owner.replace("/", "."), member))

    if problems:
        print("CLDC compliance FAILED (%d problem(s)):" % len(problems), file=sys.stderr)
        for problem in problems:
            print("  " + problem, file=sys.stderr)
        return 1

    print("cldc compliance: %d class file(s) checked, all references present in the stubs"
          % checked)
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
