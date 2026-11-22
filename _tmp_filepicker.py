import ctypes, sys
from ctypes import wintypes

user32 = ctypes.windll.user32

WM_SETTEXT = 0x000C
BM_CLICK = 0x00F5
IDOK = 1
FILENAME_CTRL_ID = 1148  # Vista+ common file dialog file name edit

dialog = user32.FindWindowW("#32770", "打开")
if not dialog:
    # try any #32770 with a file chooser look
    print("NO_DIALOG")
    sys.exit(2)

EnumChildProc = ctypes.WINFUNCTYPE(wintypes.BOOL, wintypes.HWND, wintypes.LPARAM)
children = []

def cb(hwnd, lp):
    cls = ctypes.create_unicode_buffer(256)
    user32.GetClassNameW(hwnd, cls, 256)
    length = user32.GetWindowTextLengthW(hwnd)
    buf = ctypes.create_unicode_buffer(length + 1)
    user32.GetWindowTextW(hwnd, buf, length + 1)
    cid = user32.GetDlgCtrlID(hwnd)
    children.append((hwnd, cls.value, buf.value, cid))
    return True

user32.EnumChildWindows(dialog, EnumChildProc(cb), 0)
print(f"DIALOG={dialog} CHILDREN={len(children)}")
for hwnd, cls, text, cid in children:
    print(f"  ctrl={cid} class={cls!r} text={text!r}")

if len(sys.argv) < 2:
    print("MODE=inspect")
    sys.exit(0)

path = sys.argv[1]

# Set the file name edit box
set_ok = False
for hwnd, cls, text, cid in children:
    if cls == "Edit" and cid == FILENAME_CTRL_ID:
        r = user32.SendMessageW(hwnd, WM_SETTEXT, 0, ctypes.c_wchar_p(path))
        set_ok = (r == 1)
        print(f"SETTEXT edit={hwnd} result={r}")
        break

if not set_ok:
    # fallback: first Edit control
    for hwnd, cls, text, cid in children:
        if cls == "Edit":
            r = user32.SendMessageW(hwnd, WM_SETTEXT, 0, ctypes.c_wchar_p(path))
            set_ok = (r == 1)
            print(f"SETTEXT(fallback) edit={hwnd} ctrl={cid} result={r}")
            break

print("SET_OK=", set_ok)

# Click OK / Open button
clicked = False
for hwnd, cls, text, cid in children:
    if cls == "Button" and cid == IDOK:
        user32.SendMessageW(hwnd, BM_CLICK, 0, 0)
        clicked = True
        print(f"CLICK ok button={hwnd} text={text!r}")
        break

if not clicked:
    user32.PostMessageW(dialog, 0x0111, IDOK, 0)  # WM_COMMAND
    print("POST WM_COMMAND IDOK")

print("DONE")
