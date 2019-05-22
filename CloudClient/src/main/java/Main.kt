package dainslef.cloud.client

import javax.servlet.http.HttpServletRequest

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.cloud.context.config.annotation.RefreshScope
import org.springframework.cloud.netflix.eureka.EnableEurekaClient
import org.springframework.core.env.Environment
import org.springframework.core.env.get
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

import dainslef.cloud.base.Logger

@SpringBootApplication
@EnableEurekaClient
@RestController
@RefreshScope
class CloudClient : Logger {

    @Value("\${test.config}")
    lateinit var config: String

    @Autowired
    lateinit var environment: Environment

    @GetMapping("/config")
    fun testAtValue() = config

    @GetMapping("/get-config/{configPath}")
    fun testConfig(@PathVariable configPath: String, request: HttpServletRequest) =
        ("Session name: ${request.getSession(false).getAttribute("name")}, " +
                "Config URL: $configPath, Config Value: ${environment[configPath]}").apply {
            logger.info(this)
        }

    @GetMapping("/login")
    fun login(@RequestParam user: String, request: HttpServletRequest) =
        request.session?.run {
            setAttribute("name", user)
            logger.info("User login: ${getAttribute("name")}")
            "Login success, user: $user"
        }

    @GetMapping("/logout")
    fun logout(request: HttpServletRequest) = request.getSession(false)?.run {
        val user = getAttribute("name")
        logger.info("User logout: $user")
        invalidate()
        "Logout success, user: $user"
    } ?: "Logout failed"

}

fun main(args: Array<String>) {
    SpringApplication.run(CloudClient::class.java, *args)
}
