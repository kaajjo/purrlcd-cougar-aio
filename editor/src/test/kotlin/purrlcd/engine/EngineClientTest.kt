package purrlcd.engine

import java.io.File
import java.net.SocketTimeoutException
import java.util.Base64
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import purrlcd.model.Scene

class EngineClientTest {
    private class MockPipe(stall: Boolean) : AutoCloseable {
        val name = "PurrLCD-test-${UUID.randomUUID()}"
        private val script = """
            ${'$'}pipe = [IO.Pipes.NamedPipeServerStream]::new('$name',[IO.Pipes.PipeDirection]::InOut,1,[IO.Pipes.PipeTransmissionMode]::Byte,[IO.Pipes.PipeOptions]::Asynchronous)
            [Console]::WriteLine('READY')
            ${'$'}pipe.WaitForConnection()
            function ReadExactly([int]${'$'}count) {
                ${'$'}bytes = New-Object byte[] ${'$'}count
                ${'$'}offset = 0
                while (${'$'}offset -lt ${'$'}count) {
                    ${'$'}n = ${'$'}pipe.Read(${'$'}bytes,${'$'}offset,${'$'}count-${'$'}offset)
                    if (${'$'}n -le 0) { throw 'Unexpected EOF' }
                    ${'$'}offset += ${'$'}n
                }
                return ,${'$'}bytes
            }
            ${'$'}header = ReadExactly 4
            ${'$'}count = [BitConverter]::ToInt32(${'$'}header,0)
            ${'$'}body = ReadExactly ${'$'}count
            ${'$'}request = [Text.Encoding]::UTF8.GetString(${'$'}body) | ConvertFrom-Json
            if (${'$'}request.command -ne 'preview') { throw 'Wrong command' }
            ${if (stall) "[Threading.Thread]::Sleep(12000)" else """
                if (${'$'}request.scene.cpu.label -ne 'CPU') { throw 'Wrong scene' }
                ${'$'}response = [Text.Encoding]::UTF8.GetBytes('{"ok":true,"status":{"connected":false,"cpuTemp":42.5,"gpuTemp":null,"sensorStatus":"Ready"},"previewPath":"preview.png"}')
                ${'$'}header = [BitConverter]::GetBytes(${'$'}response.Length)
                ${'$'}pipe.Write(${'$'}header,0,4)
                ${'$'}pipe.Write(${'$'}response,0,${'$'}response.Length)
                ${'$'}pipe.Flush()
            """}
            ${'$'}pipe.Dispose()
        """.trimIndent()
        private val process = ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-EncodedCommand",
            Base64.getEncoder().encodeToString(script.toByteArray(Charsets.UTF_16LE)))
            .redirectErrorStream(true).start()
        init { check(process.inputStream.bufferedReader().readLine() == "READY") { "Mock pipe did not start" } }
        override fun close() { process.destroyForcibly(); process.waitFor() }
    }

    @Test
    fun roundTripUsesLengthPrefixedUtf8Json() = runBlocking {
        MockPipe(stall = false).use { mock ->
            EngineClient(null, File("unused"), mock.name).use { client ->
                val result = client.request("preview", Scene())
                assertTrue(result.ok)
                assertEquals(42.5, result.status?.cpuTemp)
                assertEquals(null, result.status?.gpuTemp)
                assertEquals("Ready", result.status?.sensorStatus)
                assertEquals("preview.png", result.previewPath)
            }
        }
    }

    @Test
    fun stalledWindowsPipeTimesOutWithoutBlockingEditorShutdown() = runBlocking {
        MockPipe(stall = true).use { mock ->
            val client = EngineClient(null, File("unused"), mock.name)
            val start = System.nanoTime()
            assertFailsWith<SocketTimeoutException> { client.request("preview", Scene()) }
            val elapsed = (System.nanoTime() - start) / 1_000_000
            assertTrue(elapsed in 2500..5000, "Timeout returned in ${elapsed}ms")
            val closeStart = System.nanoTime()
            client.close()
            assertTrue((System.nanoTime() - closeStart) / 1_000_000 < 500, "Closing the editor blocked")
        }
    }
}
