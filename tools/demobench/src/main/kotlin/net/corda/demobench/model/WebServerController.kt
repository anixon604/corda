package net.corda.demobench.model

import tornadofx.Controller

class WebServerController : Controller() {

    private val jvm by inject<JVMConfig>()
    private val cordaPath = jvm.applicationDir.resolve("corda").resolve("corda.jar")

    init {
        log.info("Web Server JAR: $cordaPath")
    }

    internal fun process() = jvm.processFor(cordaPath, "--webserver")

    fun webServer() = WebServer(this)

}