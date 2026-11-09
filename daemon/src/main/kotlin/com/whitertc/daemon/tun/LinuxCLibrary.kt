package com.whitertc.daemon.tun

import java.nio.ByteBuffer
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Structure
import com.sun.jna.Structure.FieldOrder

interface LinuxCLibrary : Library {
    companion object {
        val INSTANCE: LinuxCLibrary by lazy {
            Native.load("c", LinuxCLibrary::class.java)
        }

        const val O_RDWR = 2
        const val O_CLOEXEC = 0x80000

        const val IFF_TUN: Short = 0x0001
        const val IFF_NO_PI: Short = 0x1000
        const val TUNSETIFF: Long = 0x400454caL // _IOW('T', 202, int)

        const val SOL_SOCKET = 1
        const val SO_MARK = 36
        const val SO_BINDTODEVICE = 25
        const val WHITEPROXY_FWMARK = 0xca6c // 51820

        const val POLLIN: Short = 0x0001
        const val POLLERR: Short = 0x0008
        const val POLLHUP: Short = 0x0010
    }

    @FieldOrder("ifr_name", "ifr_flags", "pad")
    class IfReq : Structure() {
        @JvmField var ifr_name = ByteArray(16)
        @JvmField var ifr_flags: Short = 0
        @JvmField var pad = ByteArray(22) // total 40 bytes on x86_64 / arm64
    }

    @FieldOrder("fd", "events", "revents")
    class PollFd : Structure() {
        @JvmField var fd: Int = 0
        @JvmField var events: Short = 0
        @JvmField var revents: Short = 0
    }

    fun open(path: String, flags: Int): Int
    fun close(fd: Int): Int
    fun ioctl(fd: Int, request: Long, ifreq: IfReq): Int
    fun setsockopt(fd: Int, level: Int, optname: Int, optval: IntArray, optlen: Int): Int
    fun setsockopt(fd: Int, level: Int, optname: Int, optval: ByteArray, optlen: Int): Int
    fun poll(fds: Array<PollFd>, nfds: Int, timeout: Int): Int
    fun read(fd: Int, buf: ByteBuffer, count: Int): Int
    fun write(fd: Int, buf: ByteBuffer, count: Int): Int
    fun read(fd: Int, buf: ByteArray, count: Int): Int
    fun write(fd: Int, buf: ByteArray, count: Int): Int
    fun strerror(errnum: Int): String
}
