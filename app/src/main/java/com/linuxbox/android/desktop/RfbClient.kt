package com.linuxbox.android.desktop

import android.graphics.Bitmap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.zip.Inflater
import kotlin.concurrent.thread

/**
 * Compact pure-Kotlin RFB 3.8 (VNC) client connecting directly to local x11vnc
 * over 127.0.0.1:5901 so graphical Linux desktops and apps render natively inside LinuxBox
 * without requiring any external VNC app.
 */
class RfbClient {

    sealed class ConnectionState {
        data object Disconnected : ConnectionState()
        data object Connecting : ConnectionState()
        data class Connected(val width: Int, val height: Int, val desktopName: String) : ConnectionState()
        data class Error(val message: String) : ConnectionState()
    }

    private val _state = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val state: StateFlow<ConnectionState> = _state.asStateFlow()

    private val _frameRevision = MutableStateFlow(0L)
    val frameRevision: StateFlow<Long> = _frameRevision.asStateFlow()

    private val frameLock = Any()
    private var framebuffer: Bitmap? = null
    private var fbWidth = 0
    private var fbHeight = 0

    private val running = AtomicBoolean(false)
    private var socket: Socket? = null
    private val outgoing = LinkedBlockingQueue<ByteArray>()

    fun copyCurrentBitmap(): Bitmap? {
        synchronized(frameLock) {
            return framebuffer?.copy(Bitmap.Config.ARGB_8888, false)
        }
    }

    fun framebufferSize(): Pair<Int, Int> = synchronized(frameLock) { fbWidth to fbHeight }

    fun connect(host: String = "127.0.0.1", port: Int = 5901) {
        disconnect()
        running.set(true)
        _state.value = ConnectionState.Connecting

        thread(name = "rfb-client", isDaemon = true) {
            val inflater = Inflater()
            try {
                val sock = Socket()
                sock.tcpNoDelay = true
                sock.connect(InetSocketAddress(host, port), 5000)
                sock.soTimeout = 15_000
                socket = sock

                val input = DataInputStream(BufferedInputStream(sock.getInputStream(), 65536))
                val output = DataOutputStream(BufferedOutputStream(sock.getOutputStream(), 16384))

                // 1. ProtocolVersion handshake
                val versionBytes = ByteArray(12)
                input.readFully(versionBytes)
                val serverVer = String(versionBytes, Charsets.US_ASCII)
                val clientVer = if (serverVer.startsWith("RFB 003.008")) {
                    "RFB 003.008\n"
                } else {
                    "RFB 003.003\n"
                }
                output.write(clientVer.toByteArray(Charsets.US_ASCII))
                output.flush()

                // 2. Security handshake (Security Type 1 = None on localhost)
                if (clientVer.contains("003.008")) {
                    val count = input.readUnsignedByte()
                    if (count == 0) {
                        val reasonLen = input.readInt()
                        val reason = ByteArray(reasonLen.coerceIn(0, 1024))
                        input.readFully(reason)
                        throw IllegalStateException("VNC recusou conexão: ${String(reason)}")
                    }
                    val types = ByteArray(count)
                    input.readFully(types)
                    if (!types.contains(1.toByte())) {
                        throw IllegalStateException("Servidor VNC requer autenticação não suportada")
                    }
                    output.writeByte(1) // Security None
                    output.flush()
                    val secResult = input.readInt()
                    if (secResult != 0) {
                        throw IllegalStateException("Falha no handshake VNC ($secResult)")
                    }
                } else {
                    val secType = input.readInt()
                    if (secType != 1) {
                        throw IllegalStateException("Tipo de segurança VNC não suportado ($secType)")
                    }
                }

                // 3. ClientInit (shared = 1)
                output.writeByte(1)
                output.flush()

                // 4. ServerInit
                val width = input.readUnsignedShort()
                val height = input.readUnsignedShort()
                val serverPixelFormat = ByteArray(16)
                input.readFully(serverPixelFormat)
                val nameLen = input.readInt().coerceIn(0, 1024)
                val nameBytes = ByteArray(nameLen)
                input.readFully(nameBytes)
                val desktopName = String(nameBytes, Charsets.UTF_8)

                synchronized(frameLock) {
                    fbWidth = width
                    fbHeight = height
                    framebuffer?.recycle()
                    framebuffer = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                }

                // 5. SetPixelFormat: 32bpp, depth 24, little-endian, true-color, R@16 G@8 B@0
                output.writeByte(0) // msg type SetPixelFormat
                output.write(byteArrayOf(0, 0, 0)) // padding
                output.writeByte(32) // bpp
                output.writeByte(24) // depth
                output.writeByte(0)  // big-endian = false
                output.writeByte(1)  // true-color = true
                output.writeShort(255) // red-max
                output.writeShort(255) // green-max
                output.writeShort(255) // blue-max
                output.writeByte(16)   // red-shift
                output.writeByte(8)    // green-shift
                output.writeByte(0)    // blue-shift
                output.write(byteArrayOf(0, 0, 0)) // padding

                // 6. SetEncodings: Zlib (6), CopyRect (1), Raw (0)
                output.writeByte(2) // SetEncodings
                output.writeByte(0) // padding
                output.writeShort(3)
                output.writeInt(6) // Zlib
                output.writeInt(1) // CopyRect
                output.writeInt(0) // Raw
                output.flush()

                _state.value = ConnectionState.Connected(width, height, desktopName)

                // Start outgoing writer thread for mouse/keyboard/update requests
                val writerThread = thread(name = "rfb-write", isDaemon = true) {
                    try {
                        while (running.get()) {
                            val packet = outgoing.take()
                            if (packet.isEmpty() && !running.get()) break
                            output.write(packet)
                            output.flush()
                        }
                    } catch (_: Throwable) {
                        // socket closed
                    }
                }

                // Request full initial framebuffer update
                requestUpdate(incremental = false, width = width, height = height)
                sock.soTimeout = 0

                val pixelRowBuffer = IntArray(width)
                var rev = 0L

                // 7. Main server-to-client message loop
                while (running.get()) {
                    val msgType = input.readUnsignedByte()
                    when (msgType) {
                        0 -> { // FramebufferUpdate
                            input.readUnsignedByte() // padding
                            val numRects = input.readUnsignedShort()
                            for (rect in 0 until numRects) {
                                val rx = input.readUnsignedShort()
                                val ry = input.readUnsignedShort()
                                val rw = input.readUnsignedShort()
                                val rh = input.readUnsignedShort()
                                val encoding = input.readInt()

                                when (encoding) {
                                    0 -> { // Raw
                                        val byteCount = rw * rh * 4
                                        val raw = ByteArray(byteCount)
                                        input.readFully(raw)
                                        blitRaw(rx, ry, rw, rh, raw, pixelRowBuffer)
                                    }
                                    1 -> { // CopyRect
                                        val srcX = input.readUnsignedShort()
                                        val srcY = input.readUnsignedShort()
                                        copyRect(srcX, srcY, rx, ry, rw, rh)
                                    }
                                    6 -> { // Zlib
                                        val compLen = input.readInt()
                                        val comp = ByteArray(compLen)
                                        input.readFully(comp)
                                        val raw = ByteArray(rw * rh * 4)
                                        inflater.setInput(comp)
                                        var off = 0
                                        while (off < raw.size && !inflater.needsInput()) {
                                            val n = inflater.inflate(raw, off, raw.size - off)
                                            if (n <= 0) break
                                            off += n
                                        }
                                        blitRaw(rx, ry, rw, rh, raw, pixelRowBuffer)
                                    }
                                    else -> {
                                        throw IllegalStateException("Codificação VNC inesperada: $encoding")
                                    }
                                }
                            }
                            rev++
                            _frameRevision.value = rev
                            requestUpdate(incremental = true, width = width, height = height)
                        }

                        1 -> { // SetColourMapEntries (ignore)
                            input.readUnsignedByte()
                            input.readUnsignedShort()
                            val count = input.readUnsignedShort()
                            input.skipBytes(count * 6)
                        }

                        2 -> Unit // Bell

                        3 -> { // ServerCutText
                            input.readUnsignedByte()
                            input.readUnsignedShort()
                            val len = input.readInt()
                            if (len > 0) input.skipBytes(len)
                        }
                    }
                }
                writerThread.interrupt()
            } catch (t: Throwable) {
                if (running.get()) {
                    _state.value = ConnectionState.Error(t.message ?: "Conexão VNC encerrada")
                }
            } finally {
                inflater.end()
                closeSocket()
                if (!running.get()) {
                    _state.value = ConnectionState.Disconnected
                }
            }
        }
    }

    private fun blitRaw(rx: Int, ry: Int, rw: Int, rh: Int, raw: ByteArray, rowBuf: IntArray) {
        synchronized(frameLock) {
            val bmp = framebuffer ?: return
            val clampedW = rw.coerceAtMost(fbWidth - rx)
            val clampedH = rh.coerceAtMost(fbHeight - ry)
            if (clampedW <= 0 || clampedH <= 0) return

            val linePixels = if (rowBuf.size >= clampedW) rowBuf else IntArray(clampedW)
            for (y in 0 until clampedH) {
                val srcRowOffset = y * rw * 4
                for (x in 0 until clampedW) {
                    val i = srcRowOffset + x * 4
                    val b = raw[i].toInt() and 0xFF
                    val g = raw[i + 1].toInt() and 0xFF
                    val r = raw[i + 2].toInt() and 0xFF
                    linePixels[x] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
                }
                bmp.setPixels(linePixels, 0, clampedW, rx, ry + y, clampedW, 1)
            }
        }
    }

    private fun copyRect(srcX: Int, srcY: Int, dstX: Int, dstY: Int, w: Int, h: Int) {
        synchronized(frameLock) {
            val bmp = framebuffer ?: return
            val safeW = w.coerceAtMost(fbWidth - maxOf(srcX, dstX))
            val safeH = h.coerceAtMost(fbHeight - maxOf(srcY, dstY))
            if (safeW <= 0 || safeH <= 0) return
            val pixels = IntArray(safeW * safeH)
            bmp.getPixels(pixels, 0, safeW, srcX, srcY, safeW, safeH)
            bmp.setPixels(pixels, 0, safeW, dstX, dstY, safeW, safeH)
        }
    }

    private fun requestUpdate(incremental: Boolean, width: Int, height: Int) {
        val pkt = ByteArray(10)
        pkt[0] = 3 // FramebufferUpdateRequest
        pkt[1] = if (incremental) 1 else 0
        pkt[2] = 0; pkt[3] = 0 // x
        pkt[4] = 0; pkt[5] = 0 // y
        pkt[6] = ((width shr 8) and 0xFF).toByte()
        pkt[7] = (width and 0xFF).toByte()
        pkt[8] = ((height shr 8) and 0xFF).toByte()
        pkt[9] = (height and 0xFF).toByte()
        outgoing.offer(pkt)
    }

    fun sendPointerEvent(x: Int, y: Int, buttonMask: Int) {
        if (!running.get()) return
        val cx = x.coerceIn(0, (fbWidth - 1).coerceAtLeast(0))
        val cy = y.coerceIn(0, (fbHeight - 1).coerceAtLeast(0))
        val pkt = ByteArray(6)
        pkt[0] = 5 // PointerEvent
        pkt[1] = (buttonMask and 0xFF).toByte()
        pkt[2] = ((cx shr 8) and 0xFF).toByte()
        pkt[3] = (cx and 0xFF).toByte()
        pkt[4] = ((cy shr 8) and 0xFF).toByte()
        pkt[5] = (cy and 0xFF).toByte()
        outgoing.offer(pkt)
    }

    fun sendKeyEvent(keysym: Int, down: Boolean) {
        if (!running.get()) return
        val pkt = ByteArray(8)
        pkt[0] = 4 // KeyEvent
        pkt[1] = if (down) 1 else 0
        pkt[2] = 0; pkt[3] = 0
        pkt[4] = ((keysym shr 24) and 0xFF).toByte()
        pkt[5] = ((keysym shr 16) and 0xFF).toByte()
        pkt[6] = ((keysym shr 8) and 0xFF).toByte()
        pkt[7] = (keysym and 0xFF).toByte()
        outgoing.offer(pkt)
    }

    fun sendUnicodeText(text: String) {
        for (ch in text) {
            val keysym = when (ch) {
                '\n', '\r' -> 0xFF0D
                '\t' -> 0xFF09
                '\b' -> 0xFF08
                else -> ch.code
            }
            sendKeyEvent(keysym, down = true)
            sendKeyEvent(keysym, down = false)
        }
    }

    fun disconnect() {
        running.set(false)
        outgoing.offer(ByteArray(0))
        closeSocket()
        _state.value = ConnectionState.Disconnected
    }

    private fun closeSocket() {
        try {
            socket?.close()
        } catch (_: Throwable) {
            // ignore
        }
        socket = null
    }
}
