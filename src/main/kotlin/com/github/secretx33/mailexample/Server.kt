@file:Suppress("RemoveExplicitTypeArguments", "UnstableApiUsage", "RedundantSuspendModifier", "UNCHECKED_CAST")

package com.github.secretx33.mailexample

import jakarta.mail.Session
import jakarta.mail.internet.MimeMessage
import jakarta.mail.internet.MimeMultipart
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableJob
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import org.subethamail.smtp.helper.SimpleMessageListener
import org.subethamail.smtp.server.SMTPServer
import java.io.InputStream
import java.net.InetAddress
import java.util.Properties
import java.util.concurrent.AbstractExecutorService
import java.util.concurrent.CompletableFuture
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.jvm.optionals.getOrNull
import kotlin.time.Duration.Companion.seconds

suspend fun main(args: Array<String>) {
    try {
        val server = SMTPServer.port(25000)
            .bindAddress(InetAddress.getLoopbackAddress())
            .maxConnections(threadAmount.coerceAtLeast(5) * 10)
            .maxRecipients(1)
            .connectionTimeoutMs(Int.MAX_VALUE)
            .executorService(CoroutineExecutorService(Dispatchers.IO))
            .simpleMessageListener(LoggingMessageListener())
            .build()
        server.start()
        println("Starting SMTP server on '${server.bindAddress.getOrNull()}' and port '${server.port}'")
        println("$server")
        delay(Long.MAX_VALUE)
    } catch (e: Throwable) {
        println(e.stackTraceToString().split("\n").take(6).joinToString("\n"))
    }
}

class LoggingMessageListener : SimpleMessageListener {

    override fun accept(from: String, recipient: String): Boolean {
        return true
    }

    override fun deliver(from: String, recipient: String, data: InputStream) {
        val mimeMessage = data.readMimeMessage()
        println("Received email:\n${prettyJackson.writeValueAsString(debugMail(from, recipient, mimeMessage))}")
    }

    private fun InputStream.readMimeMessage(): MimeMessage = use { MimeMessage(SESSION, it) }

    private fun debugMail(from: String, recipient: String, mimeMessage: MimeMessage): Map<String, Any> {
        val mailContent = (mimeMessage.content as MimeMultipart).let { part ->
            (0..<part.count).map { index ->
                part.getBodyPart(index).inputStream.bufferedReader().use { it.readText() }
            }
        }
        val subject = mimeMessage.subject
        return mapOf(
            "from" to from,
            "to" to recipient,
            "subject" to subject,
            "content" to mailContent,
        )
    }

    private companion object {
        val SESSION: Session = Session.getDefaultInstance(Properties())
    }
}

class CoroutineExecutorService(
    coroutineContext: CoroutineContext = EmptyCoroutineContext,
    private val parallelism: Int = threadAmount,
): AbstractExecutorService() {

    private val coroutineScope = CoroutineScope(SupervisorJob() + CoroutineName(this::class.simpleName!!) + coroutineContext)
    private val coroutineJob: CompletableJob get() = coroutineScope.coroutineContext.job as CompletableJob
    private val lock = Semaphore(parallelism)
    private val state = AtomicReference(State.ACTIVE)
    private val shutdownComplete = CompletableFuture<Unit>()

    private fun assertActive() {
        if (state.get() == State.ACTIVE) return
        throw RejectedExecutionException("Coroutine executor service is shutdown")
    }

    private fun setShutdownState() {
        if (!state.compareAndSet(State.STOPPING, State.STOPPED)) return
        shutdownComplete.complete(Unit)
    }

    override fun execute(command: Runnable) {
        assertActive()
        coroutineScope.launch {
            lock.withPermit { command.run() }
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    override fun shutdown() {
        if (!state.compareAndSet(State.ACTIVE, State.STOPPING)) return
        coroutineJob.complete()
        GlobalScope.launch {
            withTimeoutOrNull(30.seconds) { coroutineJob.join() }
            setShutdownState()
        }
    }

    override fun shutdownNow(): List<Runnable> {
        if (!state.compareAndSet(State.ACTIVE, State.STOPPING)) return emptyList()
        coroutineJob.cancel(CancellationException("Closing coroutine executor service"))
        setShutdownState()
        return emptyList()
    }

    override fun isShutdown(): Boolean = state.get() >= State.STOPPING

    override fun isTerminated(): Boolean  = state.get() == State.STOPPED

    override fun awaitTermination(timeout: Long, unit: TimeUnit): Boolean = try {
        shutdownComplete.get(timeout, unit)
        true
    } catch (e: TimeoutException) {
        false
    }

    override fun toString(): String = "${this::class.simpleName}(state=$state, availableTaskSlots=[${lock.availablePermits}/$parallelism], coroutineScope=$coroutineScope)"

    private enum class State {
        // Order of declaration is important
        ACTIVE,
        STOPPING,
        STOPPED,
    }
}