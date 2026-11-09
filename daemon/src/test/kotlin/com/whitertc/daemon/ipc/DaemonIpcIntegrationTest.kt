package com.whitertc.daemon.ipc

import java.io.File
import java.net.StandardProtocolFamily
import java.net.UnixDomainSocketAddress
import java.nio.channels.SocketChannel
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import com.whitertc.core.ipc.IpcCommand
import com.whitertc.core.ipc.IpcEvent
import com.whitertc.core.ipc.IpcJson
import com.whitertc.daemon.engine.DaemonMultipathEngine

class DaemonIpcIntegrationTest {

    @Test
    fun testDaemonIpcServerReceivesAndAnswersCommands() = runBlocking {
        val testSock = "/tmp/test_whiteproxy_ipc_${System.currentTimeMillis()}.sock"
        val engine = DaemonMultipathEngine()
        val server = DaemonIpcServer(engine, customSocketPath = testSock)

        try {
            server.start()
            assertTrue(File(testSock).exists(), "Socket file must exist after server starts")

            val channel = SocketChannel.open(StandardProtocolFamily.UNIX)
            channel.connect(UnixDomainSocketAddress.of(Path.of(testSock)))
            assertTrue(channel.isConnected, "Client must connect to AF_UNIX socket")

            val reader = java.nio.channels.Channels.newInputStream(channel).bufferedReader()
            val writer = java.nio.channels.Channels.newOutputStream(channel).bufferedWriter()

            // Initial status and stats sent on connect
            val line1 = withTimeout(3000) { reader.readLine() }
            val event1 = IpcJson.instance.decodeFromString<IpcEvent>(line1)
            assertTrue(event1 is IpcEvent.StatusChanged || event1 is IpcEvent.StatsUpdated)

            val line2 = withTimeout(3000) { reader.readLine() }
            val event2 = IpcJson.instance.decodeFromString<IpcEvent>(line2)
            assertTrue(event2 is IpcEvent.StatusChanged || event2 is IpcEvent.StatsUpdated)

            // Send GetStatus command
            val cmd = IpcJson.instance.encodeToString(IpcCommand.serializer(), IpcCommand.GetStatus)
            writer.write(cmd)
            writer.newLine()
            writer.flush()

            val line3 = withTimeout(3000) { reader.readLine() }
            val event3 = IpcJson.instance.decodeFromString<IpcEvent>(line3)
            assertTrue(event3 is IpcEvent.StatusChanged)
            assertEquals("DISCONNECTED", (event3 as IpcEvent.StatusChanged).state)

            channel.close()
        } finally {
            server.close()
            Files.deleteIfExists(Path.of(testSock))
        }
    }
}
