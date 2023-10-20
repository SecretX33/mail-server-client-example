@file:Suppress("RedundantSuspendModifier")

package com.github.secretx33.mailexample

import jakarta.mail.Message
import jakarta.mail.Session
import jakarta.mail.Transport
import jakarta.mail.internet.InternetAddress
import jakarta.mail.internet.MimeBodyPart
import jakarta.mail.internet.MimeMessage
import jakarta.mail.internet.MimeMultipart
import org.slf4j.LoggerFactory
import java.lang.invoke.MethodHandles
import java.net.InetAddress
import java.util.Properties

private val log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass())

suspend fun main(args: Array<String>) {
    try {
        val prop = Properties().apply {
            put("mail.smtp.auth", false)
            put("mail.smtp.starttls.enable", "false")
            put("mail.smtp.host", InetAddress.getLoopbackAddress().hostAddress)
            put("mail.smtp.port", "25000")
        }
        val session = Session.getInstance(prop)
        val message = MimeMessage(session).apply {
            setFrom(InternetAddress("from@mail.com"))
            setRecipients(Message.RecipientType.TO, InternetAddress.parse("to@mail.com"))
            subject = "Mail Subject"

            val multipart = run {
                val msg = "This is my first email using JavaMailer"
                val mimeBodyPart = MimeBodyPart().apply { setContent(msg, "text/html; charset=utf-8") }
                MimeMultipart().apply { addBodyPart(mimeBodyPart) }
            }
            setContent(multipart)
        }

        Transport.send(message)
        log.info("Successfully sent email!")
    } catch (e: Throwable) {
        log.error("Client error", e)
    }
}
