package org.drinkless.tdlib

import dev.g000sha256.tdl.loadSystemLibrary

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
internal class JsonClient {

    init {
        loadSystemLibrary()
    }

    external fun createClientId(): Int

    external fun send(clientId: Int, request: String)

    external fun receive(timeoutInSeconds: Double): String?

    external fun execute(request: String): String?

    external fun setLogMessageHandler(maxVerbosityLevel: Int, logMessageHandler: LogMessageHandler?)

    fun interface LogMessageHandler {

        fun onLogMessage(verbosityLevel: Int, message: String)

    }

}
