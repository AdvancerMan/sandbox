package ru.scoltech.openran.speedtest

import java.io.FileReader

class IperfRunner(writableDir: String) {
    private var inputHandlerThreads: List<Thread>? = null

    private val stdoutPipePath = "$writableDir/iperfStdout"
    private val stderrPipePath = "$writableDir/iperfStderr"

    var stdoutHandler: (String) -> Unit = {}
    var stderrHandler: (String) -> Unit = {}

    fun start(args: String) {
        mkfifo(stdoutPipePath)
        mkfifo(stderrPipePath)

        val argsArray = parseIperfArgs(args)
        startJni(stdoutPipePath, stderrPipePath, argsArray)

        inputHandlerThreads = listOf(
            Triple(stdoutPipePath, "Stdout Handler", stdoutHandler),
            Triple(stderrPipePath, "Stderr Handler", stderrHandler)
        ).map { (pipePath, name, handler) ->
            Thread({
                FileReader(pipePath).buffered().useLines { lines ->
                    lines.forEach {
                        handler(it + System.lineSeparator())
                    }
                }
            }, name).also { it.start() }
        }
    }

    private fun parseIperfArgs(args: String): Array<String> {
        return args.split(Regex("\\s+")).filter { it.isNotBlank() }.toTypedArray()
    }

    fun stop() {
        exitJni()

        inputHandlerThreads!!.forEach { it.interrupt() }
        inputHandlerThreads!!.forEach { it.join() }
        inputHandlerThreads = null
    }

    private external fun mkfifo(pipePath: String)

    private external fun startJni(stdoutPipePath: String, stderrPipePath: String, args: Array<String>): Int

    private external fun exitJni()

    private external fun sendForceExitJni()

    companion object {
        init {
            System.loadLibrary("iperf2")
        }
    }
}
