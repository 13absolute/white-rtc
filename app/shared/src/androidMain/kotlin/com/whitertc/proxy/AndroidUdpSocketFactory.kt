 package com.whitertc.proxy

import com.whitertc.core.buffer.BufferView
import com.whitertc.core.transport.JvmNioUdpSocketChannel
import com.whitertc.core.transport.MultipathSocketMultiplexer
import com.whitertc.core.transport.SocketEndpoint
import com.whitertc.core.transport.UdpSocketChannel
import com.whitertc.core.transport.UdpSocketFactory
import com.whitertc.core.transport.defaultUdpSocketFactory
import com.whitertc.vpn.NativeVectorIo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.channels.DatagramChannel

class AndroidNioUdpSocketChannel(
    channel: DatagramChannel,
    remoteEndpoint: SocketEndpoint
) : JvmNioUdpSocketChannel(channel, remoteEndpoint) {

    private var cachedFd: Int = -1
    private var fdChecked: Boolean = false

    private fun getOrInitFd(): Int {
        if (!fdChecked) {
            fdChecked = true
            if (NativeVectorIo.isAvailable() && channel.isOpen) {
                cachedFd = NativeVectorIo.getSocketFd(channel)
            }
        }
        return cachedFd
    }

    override fun receiveBatch(
        directBuffer: BufferView,
        outOffsets: IntArray,
        outLengths: IntArray,
        maxPackets: Int,
        slotSize: Int
    ): Int {
        val fd = getOrInitFd()
        if (fd < 0) {
            return super.receiveBatch(directBuffer, outOffsets, outLengths, maxPackets, slotSize)
        }
        val nio = directBuffer.nioBuffer
        if (!nio.isDirect) {
            return super.receiveBatch(directBuffer, outOffsets, outLengths, maxPackets, slotSize)
        }
        val count = NativeVectorIo.nativeRecvMmsg(fd, nio, outOffsets, outLengths, maxPackets, slotSize)
        if (count < 0) {
            return super.receiveBatch(directBuffer, outOffsets, outLengths, maxPackets, slotSize)
        }
        return count
    }

    override fun sendBatch(
        directBuffer: BufferView,
        inOffsets: IntArray,
        inLengths: IntArray,
        count: Int
    ): Int {
        val fd = getOrInitFd()
        if (fd < 0) {
            return super.sendBatch(directBuffer, inOffsets, inLengths, count)
        }
        val nio = directBuffer.nioBuffer
        if (!nio.isDirect) {
            return super.sendBatch(directBuffer, inOffsets, inLengths, count)
        }
        val sent = NativeVectorIo.nativeSendMmsg(fd, nio, inOffsets, inLengths, count)
        if (sent < 0) {
            return super.sendBatch(directBuffer, inOffsets, inLengths, count)
        }
        return sent
    }

    override fun close() {
        if (cachedFd > 0) {
            val fdToClose = cachedFd
            cachedFd = -1
            runCatching { android.os.ParcelFileDescriptor.adoptFd(fdToClose).close() }
        }
        super.close()
    }
}

class AndroidUdpSocketFactory(
    private val socketProtector: ((DatagramChannel) -> Boolean)? = null
) : UdpSocketFactory {
    private val delegate = defaultUdpSocketFactory()

    override suspend fun createConnectedChannel(
        target: SocketEndpoint,
        preConnect: ((UdpSocketChannel) -> Unit)?
    ): UdpSocketChannel = withContext(Dispatchers.IO) {
        val channel = DatagramChannel.open().apply {
            configureBlocking(false)
            socket().reuseAddress = true
        }
        socketProtector?.invoke(channel)
        val wrapper = AndroidNioUdpSocketChannel(channel, target)
        preConnect?.invoke(wrapper)
        val inetAddr = runCatching { InetAddress.getByName(target.host) }.getOrNull()
        val targetAddr = if (inetAddr != null) {
            InetSocketAddress(inetAddr, target.port)
        } else {
            InetSocketAddress(target.host, target.port)
        }
        channel.connect(targetAddr)
        wrapper
    }

    override fun createMultiplexer(): MultipathSocketMultiplexer {
        return delegate.createMultiplexer()
    }
}
