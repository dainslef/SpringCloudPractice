package dainslef

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
import javax.servlet.http.HttpServletRequest

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
            request.getSession(false)?.getAttribute("name")
                    .run { "Session name: $this, Config URL: $configPath, Config Value: ${environment[configPath]}" }
                    .apply { logger.info(this) }

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @GetMapping("/connection")
    fun testConnection() = (jdbcTemplate.dataSource as? HikariDataSource)?.run {
        "Connection url: $jdbcUrl"
    }.apply { logger.info(this) } ?: "Unkown data source..."

}

@RestController
class LoginController : Logger {

    @GetMapping("/login")
    fun login(@RequestParam user: String, request: HttpServletRequest) = request.session?.run {
        // create session
        setAttribute("name", user)
        maxInactiveInterval = 99999999
        "Login success, user: ${getAttribute("name")}"
    }.apply { logger.info(this) } ?: "Login failed"

    @GetMapping("/logout")
    fun logout(request: HttpServletRequest) = request.getSession(false)?.run {
        val user = getAttribute("name")
        invalidate() // invalidate session
        "Logout success, user: $user"
    }.apply { logger.info(this) } ?: "Logout failed"

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
