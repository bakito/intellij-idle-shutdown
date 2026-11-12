package com.github.bakito.idleshutdown

import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.net.URI
import java.time.Duration
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * When running inside a Google Cloud Workstation, this service keeps the workstation
 * session alive by periodically reporting activity to the local gateway.
 *
 * Cloud Workstations have an auto-shutdown policy for idle instances. This service
 * detects the workstation environment via the [GOOGLE_CLOUD_WORKSTATIONS_ENV] environment
 * variable and sends HTTP GET requests to the gateway at [WORKSTATIONS_GATEWAY_URL] to
 * signal that the IDE is still in use.
 *
 * Reuses [IdleWatchdog] for activity tracking instead of maintaining a separate listener.
 */
class CloudWorkstationKeepalive : StartupActivity {

    private val log = logger<CloudWorkstationKeepalive>()

    override fun runActivity(project: Project) {
        if (!isRunningOnCloudWorkstation()) {
            log.info("Not running on a Cloud Workstation; keepalive is disabled.")
            return
        }

        log.info("Running inside a Cloud Workstation, idle keepalive is enabled.")

        // Start the periodic keepalive scheduler
        val scheduler = Executors.newSingleThreadScheduledExecutor { r ->
            Thread(r, "workstations-keepalive-scheduler").apply { isDaemon = true }
        }

        scheduler.scheduleWithFixedDelay(
            { pollCheckActivity() },
            CHECK_INTERVAL_S,
            CHECK_INTERVAL_S,
            TimeUnit.SECONDS
        )
    }

    private fun pollCheckActivity() {
        val lastActivity = service<IdleWatchdog>().getLastActivityTime()
        val idleMs = System.currentTimeMillis() - lastActivity
        if (idleMs < IDLE_TIMEOUT_THRESHOLD.toMillis()) return

        // Only ping when idle — active users don't need keepalive
        sendKeepalive()
    }

    private fun sendKeepalive() {
        try {
            val request = HttpRequest.newBuilder()
                .uri(URI("${WORKSTATIONS_GATEWAY_URL}/_workstation/reportactivity"))
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build()

            val response = httpClient.send(request, HttpResponse.BodyHandlers.discarding())
            if (response.statusCode() in 200..399) {
                log.debug("Cloud Workstation keepalive ping successful")
            } else {
                log.warn("Cloud Workstation keepalive returned status ${response.statusCode()}")
            }
        } catch (e: Exception) {
            // Network errors are expected if the gateway is temporarily unavailable;
            // the next scheduled tick will retry automatically.
            log.debug("Cloud Workstation keepalive failed (will retry): ${e.message}")
        }
    }

    companion object {
        private const val GOOGLE_CLOUD_WORKSTATIONS_ENV = "GOOGLE_CLOUD_WORKSTATIONS"
        private const val WORKSTATIONS_GATEWAY_URL = "http://localhost:981"
        private val IDLE_TIMEOUT_THRESHOLD = Duration.ofSeconds(30)
        private const val CHECK_INTERVAL_S = 15L

        private val httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build()

        /** Returns true if the IDE is running inside a Google Cloud Workstation. */
        fun isRunningOnCloudWorkstation(): Boolean {
            return System.getenv(GOOGLE_CLOUD_WORKSTATIONS_ENV) != null
        }
    }
}
