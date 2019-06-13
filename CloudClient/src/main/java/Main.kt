package dainslef

import java.util.concurrent.atomic.AtomicInteger
import javax.servlet.http.HttpServletRequest

import com.zaxxer.hikari.HikariDataSource
import org.springframework.beans.factory.annotation.*
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.cloud.context.config.annotation.RefreshScope
import org.springframework.cloud.stream.annotation.EnableBinding
import org.springframework.cloud.stream.annotation.StreamListener
import org.springframework.cloud.stream.messaging.Sink
import org.springframework.core.env.Environment
import org.springframework.core.env.get
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.web.bind.annotation.*

fun main(args: Array<String>) {
    SpringApplication.run(CloudClient::class.java, *args)
}

@SpringBootApplication
class CloudClient

@RestController
@RefreshScope
class ConfigRefreshController : Logger {

    @Value("\${test.config}")
    private lateinit var config: String

    @Autowired
    private lateinit var environment: Environment

    @GetMapping("/config")
    fun testValue() = config

    @GetMapping("/get-config/{configPath}")
    fun testConfig(@PathVariable configPath: String, request: HttpServletRequest) =
            request.getSession(false)
                    ?.getAttribute("name")
                    .run { "Session name: $this, Config URL: $configPath, Config Value: ${environment[configPath]}" }
                    .apply(logger::info)

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @GetMapping("/connection")
    fun testConnection() = (jdbcTemplate.dataSource as? HikariDataSource)
            ?.run { "Connection url: $jdbcUrl" }
            .apply(logger::info) ?: "Unkown data source..."

}

@RestController
class LoginController : Logger {

    private val index = AtomicInteger()

    @GetMapping("/count")
    fun count(@RequestParam(required = false) value: Int?) =
            value?.apply { index.set(this) } ?: index.get()

    @GetMapping("/login")
    fun login(@RequestParam user: String, request: HttpServletRequest) = request.session
            ?.run {
                setAttribute("name", user) // create session and set attribute
                maxInactiveInterval = 99999999
                "Login success, user: ${getAttribute("name")}"
            }.apply(logger::info) ?: "Login failed"

    @GetMapping("/logout")
    fun logout(request: HttpServletRequest) = request.getSession(false)
            ?.run {
                val user = getAttribute("name")
                invalidate() // invalidate session
                "Logout success, user: $user"
            }.apply(logger::info) ?: "Logout failed"

}

@EnableBinding(Sink::class, CustomSinkSource::class)
class MessageHandlers : Logger {

    @StreamListener(Sink.INPUT)
    fun receiveMessage(message: String) = logger.info("Receive message from ${Sink.INPUT}: $message")

    @StreamListener(CustomSinkSource.inChannel1)
    fun receiveMessage1(message: CustomMessage) = logger.info("Receive message from ${CustomSinkSource.inChannel1}: $message")

    @StreamListener(CustomSinkSource.inChannel2)
    fun receiveMessage2(message: CustomMessage) = logger.info("Receive message from ${CustomSinkSource.inChannel2}: $message")

}
