package dainslef.cloud.server

import com.netflix.discovery.EurekaClient
import com.netflix.zuul.ZuulFilter
import com.netflix.zuul.context.RequestContext
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.cloud.config.server.EnableConfigServer
import org.springframework.cloud.netflix.eureka.EnableEurekaClient
import org.springframework.cloud.netflix.eureka.server.EnableEurekaServer
import org.springframework.cloud.netflix.zuul.EnableZuulProxy
import org.springframework.cloud.netflix.zuul.filters.support.FilterConstants.PRE_TYPE
import org.springframework.context.annotation.Configuration
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import javax.ws.rs.core.MediaType

import dainslef.cloud.base.Logger

@SpringBootApplication
@EnableConfigServer
@EnableEurekaServer
@EnableEurekaClient
@EnableZuulProxy
@RestController
class CloudServer {

    @Autowired
    private lateinit var client: EurekaClient

    @GetMapping("/client-info")
    fun clientInfo() = client.applications

}

@Configuration
class CustomFilter : ZuulFilter(), Logger {

    @Value("\${lj.login-service-id}")
    private lateinit var loginService: String

    override fun shouldFilter() = true
    override fun filterType() = PRE_TYPE
    override fun filterOrder() = 0

    override fun run() {
        RequestContext.getCurrentContext()?.apply {
            val uri = request.requestURI
            val shouldForward = when {
                uri.startsWith("/$loginService/login") || request.method == "OPTIONS" -> true
                else -> runCatching { request.getSession(false).getAttribute("name") }.isSuccess
            }
            logger.info("URL: $uri, Should forward: $shouldForward")
            if (!shouldForward) {
                setSendZuulResponse(shouldForward)
                response.contentType = MediaType.TEXT_PLAIN
                responseBody = "No session..."
            }
        }
    }

}

fun main(args: Array<String>) {
    SpringApplication.run(CloudServer::class.java, *args)
}
