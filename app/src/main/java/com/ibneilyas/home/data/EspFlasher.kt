package com.ibneilyas.home.data

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import androidx.core.content.ContextCompat
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import com.ibneilyas.home.core.ControllerCert
import com.ibneilyas.home.core.LicenseKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import kotlin.coroutines.resume

private const val PERM = "com.ibneilyas.home.USB_PERMISSION"
private const val FLASH_BLOCK = 0x400
private val SYNC = byteArrayOf(0x07, 0x07, 0x12, 0x20) + ByteArray(32) { 0x55 }

/** USB-OTG flasher for a classic ESP32 (ROM bootloader protocol, no stub). */
object EspFlasher {

    suspend fun detect(ctx: Context, log: (String) -> Unit): String =
        withSession(ctx, log) { it.deviceId() }

    suspend fun flash(ctx: Context, ticket: String, log: (String) -> Unit, progress: (Int) -> Unit) {
        val p = ticket.trim().split(":")
        if (p.size != 3 || p[0] != "IBN-TICKET") throw IllegalStateException("Ticket ka format ghalat hai (IBN-TICKET:ID:KEY)")
        val tid = p[1]
        if (!ControllerCert.ok(LicenseKey.PUBLIC_B64, tid, p[2])) throw IllegalStateException("Ticket sahi nahi (signature match nahi hui)")
        val image = ctx.applicationContext.assets.open("firmware-full.bin").use { it.readBytes() }
        if (image.size < 0xE000 + 100000 || image.size > 3000000) throw IllegalStateException("Bundled firmware ka size ghalat hai")
        withSession(ctx, log) { s ->
            val id = s.deviceId()
            if (id != tid) throw IllegalStateException("Yeh ticket $tid ke liye hai, lekin yeh ESP32 $id hai. Flash nahi kiya.")
            log("Ticket theek hai. Flash shuru...")
            s.flash(image, log, progress)
            log("Flash mukammal, MD5 theek. ESP32 restart ho raha hai...")
            s.bootLog(6000, log)
        }
    }

    private suspend fun ensurePermission(app: Context, mgr: UsbManager, dev: UsbDevice): Boolean {
        if (mgr.hasPermission(dev)) return true
        return suspendCancellableCoroutine { cont ->
            val rcv = object : BroadcastReceiver() {
                override fun onReceive(c: Context, i: Intent) {
                    try { app.unregisterReceiver(this) } catch (_: Exception) {}
                    if (cont.isActive) cont.resume(mgr.hasPermission(dev))
                }
            }
            ContextCompat.registerReceiver(app, rcv, IntentFilter(PERM), ContextCompat.RECEIVER_NOT_EXPORTED)
            val pi = PendingIntent.getBroadcast(app, 0, Intent(PERM).setPackage(app.packageName), PendingIntent.FLAG_MUTABLE)
            mgr.requestPermission(dev, pi)
            cont.invokeOnCancellation { try { app.unregisterReceiver(rcv) } catch (_: Exception) {} }
        }
    }

    private suspend fun openPort(app: Context, log: (String) -> Unit): UsbSerialPort {
        val mgr = app.getSystemService(Context.USB_SERVICE) as UsbManager
        val drivers = UsbSerialProber.getDefaultProber().findAllDrivers(mgr)
        if (drivers.isEmpty()) throw IllegalStateException("Koi USB serial device nahi mila. Data wali OTG cable check karein.")
        val drv = drivers[0]
        log("USB chip: " + drv.javaClass.simpleName)
        if (!ensurePermission(app, mgr, drv.device)) throw IllegalStateException("USB permission nahi mili")
        val conn = mgr.openDevice(drv.device) ?: throw IllegalStateException("USB device khul nahi saka")
        val port = drv.ports[0]
        port.open(conn)
        port.setParameters(115200, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
        return port
    }

    private suspend fun <T> withSession(ctx: Context, log: (String) -> Unit, block: (Session) -> T): T {
        val app = ctx.applicationContext
        return withContext(Dispatchers.IO) {
            val port = openPort(app, log)
            try {
                val s = Session(port, log)
                s.connect()
                block(s)
            } finally {
                try { port.close() } catch (_: Exception) {}
            }
        }
    }

    private class Session(val port: UsbSerialPort, val log: (String) -> Unit) {
        private val rx = ArrayDeque<Int>()
        private val tmp = ByteArray(4096)

        private fun le32(v: Int) = byteArrayOf(v.toByte(), (v shr 8).toByte(), (v shr 16).toByte(), (v shr 24).toByte())
        private fun rd32(b: ByteArray, o: Int) = (b[o].toInt() and 0xFF) or ((b[o + 1].toInt() and 0xFF) shl 8) or
            ((b[o + 2].toInt() and 0xFF) shl 16) or ((b[o + 3].toInt() and 0xFF) shl 24)

        private fun checksum(d: ByteArray): Int {
            var c = 0xEF
            for (b in d) c = c xor (b.toInt() and 0xFF)
            return c
        }

        private fun slip(d: ByteArray): ByteArray {
            val o = ByteArrayOutputStream()
            o.write(0xC0)
            for (b in d) {
                when (b.toInt() and 0xFF) {
                    0xC0 -> { o.write(0xDB); o.write(0xDC) }
                    0xDB -> { o.write(0xDB); o.write(0xDD) }
                    else -> o.write(b.toInt() and 0xFF)
                }
            }
            o.write(0xC0)
            return o.toByteArray()
        }

        private fun nextByte(deadline: Long): Int {
            while (rx.isEmpty()) {
                val left = deadline - System.currentTimeMillis()
                if (left <= 0) return -1
                val n = port.read(tmp, minOf(left, 200L).toInt().coerceAtLeast(1))
                for (i in 0 until n) rx.addLast(tmp[i].toInt() and 0xFF)
            }
            return rx.removeFirst()
        }

        private fun readFrame(timeoutMs: Long): ByteArray? {
            val deadline = System.currentTimeMillis() + timeoutMs
            var b: Int
            do {
                b = nextByte(deadline)
                if (b < 0) return null
            } while (b != 0xC0)
            val out = ByteArrayOutputStream()
            while (true) {
                b = nextByte(deadline)
                if (b < 0) return null
                if (b == 0xC0) {
                    if (out.size() == 0) continue
                    return out.toByteArray()
                }
                if (b == 0xDB) {
                    val c = nextByte(deadline)
                    if (c < 0) return null
                    out.write(if (c == 0xDD) 0xDB else 0xC0)
                } else {
                    out.write(b)
                }
            }
        }

        private fun cmd(op: Int, data: ByteArray, chk: Int = 0, timeoutMs: Long = 3000): Pair<Int, ByteArray> {
            val pkt = ByteArray(8 + data.size)
            pkt[1] = op.toByte()
            pkt[2] = data.size.toByte()
            pkt[3] = (data.size shr 8).toByte()
            le32(chk).copyInto(pkt, 4)
            data.copyInto(pkt, 8)
            port.write(slip(pkt), 3000)
            val end = System.currentTimeMillis() + timeoutMs
            while (true) {
                val left = end - System.currentTimeMillis()
                if (left <= 0) break
                val f = readFrame(left) ?: break
                if (f.size < 10 || f[0].toInt() != 1 || (f[1].toInt() and 0xFF) != op) continue
                val st = f[f.size - 2].toInt() and 0xFF
                val er = f[f.size - 1].toInt() and 0xFF
                if (st != 0) throw IllegalStateException("ESP32 ne command 0x%02X rad ki (error %d)".format(op, er))
                return Pair(rd32(f, 4), f.copyOfRange(8, f.size - 2))
            }
            throw IllegalStateException("ESP32 ne jawab nahi diya (command 0x%02X)".format(op))
        }

        private fun readReg(addr: Int): Int = cmd(0x0A, le32(addr)).first

        private fun reset(delayMs: Long) {
            port.setDTR(false); port.setRTS(true); Thread.sleep(100)
            port.setDTR(true); port.setRTS(false); Thread.sleep(delayMs)
            port.setDTR(false)
        }

        private fun hardReset() {
            port.setDTR(false); port.setRTS(true); Thread.sleep(100); port.setRTS(false)
        }

        fun connect() {
            var ok = false
            for (attempt in 1..8) {
                reset(if (attempt % 2 == 1) 50L else 550L)
                rx.clear()
                try { port.purgeHwBuffers(true, true) } catch (_: Exception) {}
                for (k in 1..5) {
                    try { cmd(0x08, SYNC, 0, 200); ok = true; break } catch (_: Exception) {}
                }
                if (ok) break
            }
            if (!ok) throw IllegalStateException("ESP32 bootloader mein nahi aaya. BOOT button dabaye rakhein aur dobara try karein.")
            val end = System.currentTimeMillis() + 150
            while (System.currentTimeMillis() < end) port.read(tmp, 50)
            rx.clear()
            val magic = readReg(0x40001000)
            if (magic != 0x00F01D83) throw IllegalStateException("Yeh classic ESP32 nahi hai (chip 0x%08X)".format(magic))
            log("ESP32 mil gaya, bootloader mein hai.")
        }

        fun deviceId(): String {
            val m1 = readReg(0x3FF5A008)
            val m0 = readReg(0x3FF5A004)
            val mac = byteArrayOf((m1 shr 8).toByte(), m1.toByte(), (m0 shr 24).toByte(), (m0 shr 16).toByte(), (m0 shr 8).toByte(), m0.toByte())
            log("MAC: " + mac.joinToString(":") { "%02X".format(it) })
            val id = "ESP32-%02X%02X%02X".format(mac[5], mac[4], mac[3])
            log("Device ID: $id")
            return id
        }

        fun flash(image: ByteArray, log: (String) -> Unit, progress: (Int) -> Unit) {
            cmd(0x0D, ByteArray(8))
            cmd(0x0B, le32(0) + le32(4 * 1024 * 1024) + le32(0x10000) + le32(0x1000) + le32(0x100) + le32(0xFFFF))
            val total = image.size
            val regions = listOf(Pair(0, 0x9000), Pair(0xE000, total - 0xE000))
            val all = regions.sumOf { it.second }
            var done = 0
            for ((off, len) in regions) {
                log("Likh raha hoon: 0x%X se %d bytes (erase mein 30 second tak lag sakte hain)".format(off, len))
                val base = done
                writeRegion(off, image.copyOfRange(off, off + len)) { n -> progress((base + n) * 100 / all) }
                done += len
            }
            progress(100)
            try { cmd(0x04, le32(1), 0, 3000) } catch (_: Exception) {}
            hardReset()
        }

        private fun writeRegion(offset: Int, data: ByteArray, onBytes: (Int) -> Unit) {
            val n = (data.size + FLASH_BLOCK - 1) / FLASH_BLOCK
            cmd(0x02, le32(data.size) + le32(n) + le32(FLASH_BLOCK) + le32(offset), 0, 90000)
            var seq = 0
            var pos = 0
            while (pos < data.size) {
                val take = minOf(FLASH_BLOCK, data.size - pos)
                val blk = ByteArray(FLASH_BLOCK) { 0xFF.toByte() }
                data.copyInto(blk, 0, pos, pos + take)
                cmd(0x03, le32(FLASH_BLOCK) + le32(seq) + le32(0) + le32(0) + blk, checksum(blk), 3000)
                pos += take
                seq++
                if (seq % 16 == 0) onBytes(pos)
            }
            onBytes(data.size)
            val want = MessageDigest.getInstance("MD5").digest(data).joinToString("") { "%02x".format(it) }
            val d = cmd(0x13, le32(offset) + le32(data.size) + le32(0) + le32(0), 0, 60000).second
            val got = if (d.size == 16) d.joinToString("") { "%02x".format(it) } else String(d, Charsets.US_ASCII).trim().take(32)
            if (got != want) throw IllegalStateException("MD5 match nahi hui (flash ghalat likha gaya). Dobara flash karein.")
        }

        fun bootLog(ms: Long, log: (String) -> Unit) {
            rx.clear()
            val sb = StringBuilder()
            val end = System.currentTimeMillis() + ms
            while (System.currentTimeMillis() < end) {
                val n = port.read(tmp, 200)
                if (n > 0) sb.append(String(tmp, 0, n, Charsets.UTF_8))
            }
            val line = sb.lines().firstOrNull { it.contains("TOKEN:") }
            if (line != null) log(line.trim()) else log("Boot log mein TOKEN nahi mila (purana token chal raha hoga).")
        }
    }
}
