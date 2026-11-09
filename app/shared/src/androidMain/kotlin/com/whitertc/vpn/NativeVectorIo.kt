package com.whitertc.vpn

import android.os.ParcelFileDescriptor
import com.whitertc.core.logging.WhiteLogger
import java.io.FileDescriptor
import java.nio.ByteBuffer
import java.nio.channels.DatagramChannel

object NativeVectorIo {
    private const val TAG = "NativeVectorIo"
    private var isLoaded = false

    init {
        try {
            System.loadLibrary("whitertc_io")
            isLoaded = true
            WhiteLogger.i(TAG) { "libwhitertc_io.so loaded successfully (recvmmsg/sendmmsg active)" }
        } catch (e: Throwable) {
            isLoaded = false
            WhiteLogger.w(TAG) { "Failed to load libwhitertc_io.so: ${e.message}. Falling back to Java NIO." }
        }
    }

    fun isAvailable(): Boolean = isLoaded

    fun getSocketFd(channel: DatagramChannel): Int {
        if (!isLoaded || !channel.isOpen) return -1
        return try {
            val socket = channel.socket() ?: return -1
            if (socket.isClosed) return -1
            // Safe, public API to get FD without hidden API reflection!
            val pfd = ParcelFileDescriptor.fromDatagramSocket(socket) ?: return -1
            val fd = pfd.fd
            pfd.detachFd() // Detach so the channel doesn't close when pfd is GC'd
            fd
        } catch (e: Throwable) {
            WhiteLogger.w(TAG) { "Failed to get FD via ParcelFileDescriptor: ${e.message}" }
            -1
        }
    }

    fun getTunFd(pfd: ParcelFileDescriptor): Int {
        return pfd.fd
    }

    external fun nativeRecvMmsg(
        fd: Int,
        directBuf: ByteBuffer,
        outOffsets: IntArray,
        outLengths: IntArray,
        maxPackets: Int,
        slotSize: Int
    ): Int

    external fun nativeSendMmsg(
        fd: Int,
        directBuf: ByteBuffer,
        inOffsets: IntArray,
        inLengths: IntArray,
        count: Int
    ): Int

    external fun nativeWriteTunBatch(
        tunFd: Int,
        directBuf: ByteBuffer,
        inOffsets: IntArray,
        inLengths: IntArray,
        count: Int
    ): Int

    external fun nativeGetFd(fileDescriptor: FileDescriptor): Int
    external fun nativeGetChannelFd(channel: Any): Int
}
