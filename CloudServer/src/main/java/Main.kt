package dainslef

import javax.ws.rs.core.MediaType
import java.util.concurrent.atomic.AtomicInteger

import com.netflix.discovery.EurekaClient
import com.netflix.zuul.ZuulFilter
import com.netflix.zuul.context.RequestContext
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.cloud.client.discovery.DiscoveryClient
import org.springframework.cloud.config.server.EnableConfigServer
import org.springframework.cloud.netflix.eureka.server.EnableEurekaServer
import org.springframework.cloud.netflix.eureka.server.event.*
import org.springframework.cloud.netflix.zuul.EnableZuulProxy
import org.springframework.cloud.netflix.zuul.filters.support.FilterConstants.PRE_TYPE
import org.springframework.context.annotation.Configuration
import org.springframework.web.bind.annotation.*
import org.springframework.cloud.stream.annotation.EnableBinding
import org.springframework.cloud.stream.messaging.Source
import org.springframework.context.event.EventListener
import org.springframework.messaging.MessageChannel
import org.springframework.messaging.support.GenericMessage

fun main(args: Array<String>) {
    SpringApplication.run(CloudServer::class.java, *args)
}

@SpringBootApplication
@EnableConfigServer
@EnableEurekaServer
@EnableZuulProxy
class CloudServer

@RestController
class ClientInfoController : Logger {

    @Autowired
    private lateinit var client: EurekaClient

    @Autowired
    private lateinit var discoveryClient: DiscoveryClient

    @GetMapping("/eureka-client")
    fun eurekaClient() = client.applications!!

    @GetMapping("/discovery-client")
    fun discoveryClient() = discoveryClient.services!!

}

@RestController
@EnableBinding(Source::class, CustomSinkSource::class)
class MessageController : Logger {

    private val index = AtomicInteger()

    @Autowired
    private lateinit var source: Source

    @Autowired
    private lateinit var customSinkSource: CustomSinkSource

    @GetMapping("/send-message/{message}")
    fun testMessage(@PathVariable message: String): String {
        val successful = source.output().send(GenericMessage(message))
        return "Send message: $message, channel: input, success: $successful".apply { logger.info(this) }
    }

    @GetMapping("/send-custom-message")
    fun testCustomMessage(@RequestParam message: String, @RequestParam channel: String, @RequestParam(required = false) type: String?): String {
        val customMessage = CustomMessage(index.incrementAndGet(), type ?: "", message)
        val send = { chann: MessageChannel -> chann.send(GenericMessage(customMessage)) }
        val successful = when (channel) {
            CustomSinkSource.inChannel1 -> send(customSinkSource.input1())
            CustomSinkSource.inChannel2 -> send(customSinkSource.input2())
            CustomSinkSource.outChannel1 -> send(customSinkSource.output1())
            CustomSinkSource.outChannel2 -> send(customSinkSource.output2())
            else -> false
        }
        return "Send message: $customMessage, channel: $channel, success: $successful".apply { logger.info(this) }
    }

}

// Setup ZuulFilter to change the route rules
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

// Deal the Eureka event
@Configuration
class EurekaEventHandler : Logger {

    @EventListener
    fun listen(event: EurekaInstanceCanceledEvent) {
        logger.info(event.toString())
    }

    @EventListener
    fun listen(event: EurekaInstanceRegisteredEvent) {
        logger.info(event.toString())
    }

    @EventListener
    fun listen(event: EurekaInstanceRenewedEvent) {
        logger.info(event.toString())
    }

    @EventListener
    fun listen(event: EurekaRegistryAvailableEvent) {
        logger.info(event.toString())
    }

    @EventListener
    fun listen(event: EurekaServerStartedEvent) {
        logger.info(event.toString())
    }

}