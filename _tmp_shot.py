import ctypes
from ctypes import wintypes

u = ctypes.windll.user32
g = ctypes.windll.gdi32

hwnd = u.FindWindowW("#32770", "打开")
print("hwnd=", hwnd)
rect = wintypes.RECT()
u.GetWindowRect(hwnd, ctypes.byref(rect))
w = rect.right - rect.left
h = rect.bottom - rect.top
print("rect=", rect.left, rect.top, w, h)

hdc = u.GetWindowDC(hwnd)
memdc = g.CreateCompatibleDC(hdc)
bmp = g.CreateCompatibleBitmap(hdc, w, h)
g.SelectObject(memdc, bmp)
# PrintWindow with PW_RENDERFULLCONTENT = 2
ok = u.PrintWindow(hwnd, memdc, 2)
print("printwindow=", ok)

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
bi.biHeight = -h  # top-down
bi.biPlanes = 1
bi.biBitCount = 32
bi.biCompression = 0

import os
buf = ctypes.create_string_buffer(w * h * 4)
g.GetDIBits(memdc, bmp, 0, h, buf, ctypes.byref(bi), 0)

# write minimal BMP file
class BITMAPFILEHEADER(ctypes.Structure):
    _fields_ = [("bfType", wintypes.WORD), ("bfSize", wintypes.DWORD),
                ("bfReserved1", wintypes.WORD), ("bfReserved2", wintypes.WORD),
                ("bfOffBits", wintypes.DWORD)]
fh = BITMAPFILEHEADER()
fh.bfType = 0x4D42
fh.bfOffBits = ctypes.sizeof(BITMAPFILEHEADER) + ctypes.sizeof(BITMAPINFOHEADER)
fh.bfSize = fh.bfOffBits + w * h * 4
out = r"E:\projects\ScreenRotator3\_tmp_dialog.bmp"
with open(out, "wb") as f:
    f.write(bytes(fh))
    f.write(bytes(bi))
    f.write(buf.raw)
print("SAVED", out)
