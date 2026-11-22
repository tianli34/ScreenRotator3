#!/usr/bin/env python3
"""通过 adb 驱动真机 UI：dump 结构、按文案/资源 id/坐标点击。

用法：
  python uidriver.py dump
  python uidriver.py tap-text "English"
  python uidriver.py tap-rid btnGrant
  python uidriver.py tap-desc "通知栏控制条"
  python uidriver.py tap-xy 540,1200
"""
import argparse
import re
import subprocess
import sys
import xml.etree.ElementTree as ET

ADB = r"C:\android-sdk\platform-tools\adb.exe"


def adb(*args, check=True):
    p = subprocess.run([ADB, *args], capture_output=True, text=True, encoding="utf-8", errors="replace")
    if check and p.returncode != 0:
        sys.stderr.write(p.stderr)
    return p.stdout


def dump_xml():
    adb("shell", "uiautomator", "dump", "/sdcard/ui.xml")
    return adb("shell", "cat", "/sdcard/ui.xml")


def parse(xml):
    xml = xml.split("?>", 1)[-1].strip()
    root = ET.fromstring(xml)
    return list(root.iter("node"))


def bounds(node):
    m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", node.get("bounds", ""))
    if not m:
        return None
    x1, y1, x2, y2 = map(int, m.groups())
    return x1, y1, x2, y2


def center(node):
    b = bounds(node)
    if not b:
        return None
    x1, y1, x2, y2 = b
    return (x1 + x2) // 2, (y1 + y2) // 2


def visible(node):
    b = bounds(node)
    return bool(b) and b[2] - b[0] > 0 and b[3] - b[1] > 0


def tap(node):
    pt = center(node)
    adb("shell", "input", "tap", str(pt[0]), str(pt[1]))
    print(f"tap {pt} <- text={node.get('text')!r} rid={node.get('resource-id')!r}")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("cmd", choices=["dump", "tap-text", "tap-rid", "tap-desc", "tap-xy", "bounds"])
    ap.add_argument("value", nargs="?")
    args = ap.parse_args()

    if args.cmd == "tap-xy":
        x, y = args.value.split(",")
        adb("shell", "input", "tap", x, y)
        print("tap", x, y)
        return

    nodes = parse(dump_xml())
    if args.cmd == "dump":
        for n in nodes:
            if not visible(n):
                continue
            cls = n.get("class", "").split(".")[-1]
            flags = "C" if n.get("clickable") == "true" else " "
            print(f"{flags} {cls:<12} {str(n.get('resource-id')).split('/')[-1]:<20} "
                  f"{n.get('bounds'):<24} text={n.get('text')!r} desc={n.get('content-desc')!r}")
        return

    key = {"tap-text": "text", "tap-rid": "resource-id", "tap-desc": "content-desc"}[args.cmd]
    pool = [n for n in nodes if visible(n)]
    if key == "resource-id":
        hits = [n for n in pool if n.get("resource-id", "").split("/")[-1] == args.value]
    else:
        hits = [n for n in pool if n.get(key) == args.value]
        if not hits:  # 退化为包含匹配
            hits = [n for n in pool if args.value in (n.get(key) or "")]
    clickable = [n for n in hits if n.get("clickable") == "true"]
    hits = clickable or hits
    if not hits:
        print(f"[not found] {key}={args.value}", file=sys.stderr)
        sys.exit(1)
    tap(hits[0])


if __name__ == "__main__":
    main()
