import ctypes
from ctypes import wintypes
from PIL import Image

u = ctypes.windll.user32
g = ctypes.windll.gdi32

hwnd = u.FindWindowW("#32770", "打开")
rect = wintypes.RECT()
u.GetWindowRect(hwnd, ctypes.byref(rect))
w = rect.right - rect.left
h = rect.bottom - rect.top

hdc = u.GetWindowDC(hwnd)
memdc = g.CreateCompatibleDC(hdc)
bmp = g.CreateCompatibleBitmap(hdc, w, h)
g.SelectObject(memdc, bmp)
u.PrintWindow(hwnd, memdc, 2)

class BITMAPINFOHEADER(ctypes.Structure):
    _fields_ = [("biSize", wintypes.DWORD), ("biWidth", wintypes.LONG),
                ("biHeight", wintypes.LONG), ("biPlanes", wintypes.WORD),
                ("biBitCount", wintypes.WORD), ("biCompression", wintypes.DWORD),
                ("biSizeImage", wintypes.DWORD), ("biXPelsPerMeter", wintypes.LONG),
                ("biYPelsPerMeter", wintypes.LONG), ("biClrUsed", wintypes.DWORD),
                ("biClrImportant", wintypes.DWORD)]
bi = BITMAPINFOHEADER()
bi.biSize = ctypes.sizeof(BITMAPINFOHEADER)
bi.biWidth = w
bi.biHeight = -h
bi.biPlanes = 1
bi.biBitCount = 32
bi.biCompression = 0

buf = ctypes.create_string_buffer(w * h * 4)
g.GetDIBits(memdc, bmp, 0, h, buf, ctypes.byref(bi), 0)

im = Image.frombuffer("RGB", (w, h), buf.raw, "raw", "BGRX", 0, 1)
im.save(r"E:\projects\ScreenRotator3\_tmp_dialog.png")
print("SAVED", im.size)
