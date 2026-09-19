package gg.rsmod.game.service.recovery

import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.util.Base64
import javax.net.ssl.SSLSocketFactory

/**
 * Minimal SMTP-over-TLS sender (implicit TLS, usually port 465: Brevo, Gmail app passwords, Mailgun, ...) for the account
 * recovery codes. No third-party library; the password is only ever sent to the configured mail server.
 */
class SmtpMailer(
    private val host: String,
    private val port: Int,
    private val username: String,
    private val password: String,
    private val from: String,
) {
    fun send(
        to: String,
        subject: String,
        body: String,
    ) {
        require(to.none { it == '\r' || it == '\n' } && subject.none { it == '\r' || it == '\n' }) { "header injection" }
        (SSLSocketFactory.getDefault().createSocket(host, port)).use { socket ->
            socket.soTimeout = 15_000
            val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))
            val writer = OutputStreamWriter(socket.getOutputStream(), Charsets.UTF_8)

            fun expect(code: String) {
                var line: String
                do {
                    line = reader.readLine() ?: error("SMTP connection closed")
                } while (line.length > 3 && line[3] == '-')
                check(line.startsWith(code)) { "SMTP expected $code, got ${line.take(3)}" }
            }

            fun command(
                text: String,
                code: String,
            ) {
                writer.write(text + "\r\n")
                writer.flush()
                expect(code)
            }
            val b64 = Base64.getEncoder()
            expect("220")
            command("EHLO 78-server", "250")
            command("AUTH LOGIN", "334")
            command(b64.encodeToString(username.toByteArray()), "334")
            command(b64.encodeToString(password.toByteArray()), "235")
            command("MAIL FROM:<$from>", "250")
            command("RCPT TO:<$to>", "250")
            command("DATA", "354")
            val message =
                buildString {
                    append("From: 78 <").append(from).append(">\r\n")
                    append("To: <").append(to).append(">\r\n")
                    append("Subject: ").append(subject).append("\r\n")
                    append("Content-Type: text/plain; charset=UTF-8\r\n\r\n")
                    body.lines().forEach { line -> append(if (line.startsWith(".")) ".$line" else line).append("\r\n") }
                    append(".")
                }
            command(message, "250")
            writer.write("QUIT\r\n")
            writer.flush()
        }
    }
}
