import ctypes, sys, time
from ctypes import wintypes

user32 = ctypes.windll.user32
VK_RETURN = 0x0D
VK_CONTROL = 0x11
VK_MENU = 0x11  # ALT
VK_A = 0x41
KEYEVENTF_KEYUP = 0x0002
INPUT_KEYBOARD = 1

class KEYBDINPUT(ctypes.Structure):
    _fields_ = [("wVk", wintypes.WORD), ("wScan", wintypes.WORD),
                ("dwFlags", wintypes.DWORD), ("time", wintypes.DWORD),
                ("dwExtraInfo", ctypes.POINTER(ctypes.c_ulong))]
class MOUSEINPUT(ctypes.Structure):
    _fields_ = [("dx", wintypes.LONG), ("dy", wintypes.LONG),
                ("mouseData", wintypes.DWORD), ("dwFlags", wintypes.DWORD),
                ("time", wintypes.DWORD), ("dwExtraInfo", ctypes.POINTER(ctypes.c_ulong))]
class HARDWAREINPUT(ctypes.Structure):
    _fields_ = [("uMsg", wintypes.DWORD), ("wParamL", wintypes.WORD), ("wParamH", wintypes.WORD)]
class _IU(ctypes.Union):
    _fields_ = [("ki", KEYBDINPUT), ("mi", MOUSEINPUT), ("hi", HARDWAREINPUT)]
class INPUT(ctypes.Structure):
    _fields_ = [("type", wintypes.DWORD), ("u", _IU)]

def key(vk, up=False):
    inp = INPUT()
    inp.type = INPUT_KEYBOARD
    inp.u.ki.wVk = vk
    inp.u.ki.dwFlags = KEYEVENTF_KEYUP if up else 0
    user32.SendInput(1, ctypes.byref(inp), ctypes.sizeof(INPUT))

def tap(vk):
    key(vk); time.sleep(0.02); key(vk, True)

dialog = user32.FindWindowW("#32770", "打开")
if not dialog:
    print("NO_DIALOG"); sys.exit(2)

path = sys.argv[1]

# bring dialog to foreground using ALT trick
key(VK_MENU); user32.SetForegroundWindow(dialog); key(VK_MENU, True)
time.sleep(0.3)
fg = user32.GetForegroundWindow()
print("dialog=", dialog, "foreground=", fg, "match=", fg == dialog)

# find the filename edit (ctrl id 1148)
EnumChildProc = ctypes.WINFUNCTYPE(wintypes.BOOL, wintypes.HWND, wintypes.LPARAM)
edits = []
def cb(hwnd, lp):
    c = ctypes.create_unicode_buffer(256)
    user32.GetClassNameW(hwnd, c, 256)
    if c.value == "Edit" and user32.GetDlgCtrlID(hwnd) == 1148:
        edits.append(hwnd)
    return True
user32.EnumChildWindows(dialog, EnumChildProc(cb), 0)
print("edits=", edits)
edit = edits[0]

# click into the edit box (real mouse click at its center)
rect = wintypes.RECT()
user32.GetWindowRect(edit, ctypes.byref(rect))
cx = (rect.left + rect.right) // 2
cy = (rect.top + rect.bottom) // 2
user32.SetCursorPos(cx, cy)
MOUSEEVENTF_LEFTDOWN = 0x0002
MOUSEEVENTF_LEFTUP = 0x0004
mi1 = INPUT(); mi1.type = 0; mi1.u.mi.dwFlags = MOUSEEVENTF_LEFTDOWN
mi2 = INPUT(); mi2.type = 0; mi2.u.mi.dwFlags = MOUSEEVENTF_LEFTUP
user32.SendInput(1, ctypes.byref(mi1), ctypes.sizeof(INPUT))
user32.SendInput(1, ctypes.byref(mi2), ctypes.sizeof(INPUT))
time.sleep(0.2)

# select all and type the path via real keyboard scan codes
key(VK_CONTROL); tap(VK_A); key(VK_CONTROL, True)
time.sleep(0.1)
for ch in path:
    # use SendInput with unicode
    inp = INPUT(); inp.type = INPUT_KEYBOARD
    inp.u.ki.wVk = 0
    inp.u.ki.wScan = ord(ch)
    inp.u.ki.dwFlags = 0x0004  # KEYEVENTF_UNICODE
    user32.SendInput(1, ctypes.byref(inp), ctypes.sizeof(INPUT))
    inp.u.ki.dwFlags = 0x0004 | KEYEVENTF_KEYUP
    user32.SendInput(1, ctypes.byref(inp), ctypes.sizeof(INPUT))
time.sleep(0.2)

# read back
WM_GETTEXT = 0x000D
rb = ctypes.create_unicode_buffer(1024)
user32.SendMessageW(edit, WM_GETTEXT, 1024, rb)
print("EDIT_TEXT=", repr(rb.value))

tap(VK_RETURN)
print("ENTER_SENT")
