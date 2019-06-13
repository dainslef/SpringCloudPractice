package dainslef

import java.net.ServerSocket
import javax.ws.rs.core.MediaType
import java.util.concurrent.atomic.AtomicInteger

import com.netflix.appinfo.InstanceInfo
import com.netflix.discovery.EurekaClient
import com.netflix.zuul.ZuulFilter
import com.netflix.zuul.context.RequestContext
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.cloud.client.discovery.DiscoveryClient
import org.springframework.cloud.config.server.EnableConfigServer
import org.springframework.cloud.context.config.annotation.RefreshScope
import org.springframework.cloud.netflix.eureka.server.EnableEurekaServer
import org.springframework.cloud.netflix.eureka.server.event.*
import org.springframework.cloud.netflix.zuul.EnableZuulProxy
import org.springframework.cloud.netflix.zuul.filters.support.FilterConstants.PRE_TYPE
import org.springframework.context.annotation.Configuration
import org.springframework.web.bind.annotation.*
import org.springframework.cloud.stream.annotation.EnableBinding
import org.springframework.cloud.stream.messaging.Source
import org.springframework.context.ApplicationEvent
import org.springframework.context.annotation.Profile
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
    fun testMessage(@PathVariable message: String) =
            "Send message: $message, channel: input, success: ${source.output().send(GenericMessage(message))}"
                    .apply(logger::info)

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
        return "Send message: $customMessage, channel: $channel, success: $successful".apply(logger::info)
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
@RefreshScope
@Configuration
@Profile("!single")
@ConfigurationProperties("lj")
class EurekaEventHandler : Logger {

    @Autowired
    private lateinit var client: EurekaClient

    @Value("\${server.port}")
    private var port = 0

    lateinit var jarPath: String
    lateinit var targetInstances: Map<String, String>

    private var portsInRestart = mutableListOf<Int>()

    // all process exit or offline will send the EurekaInstanceCanceledEvent
    @EventListener
    fun listen(event: EurekaInstanceCanceledEvent) {
        wirteLogs(event, client.applications.registeredApplications.toString())
    }

    // sometime receive the EurekaInstanceRegisteredEvent but the instance info don't update
    @EventListener
    fun listen(event: EurekaInstanceRegisteredEvent) {
        wirteLogs(event, client.applications.registeredApplications.toString())
        if (portsInRestart.contains(event.instanceInfo.port)) {
            Thread.sleep(5000) // instance info refresh is a little bit laster than
            portsInRestart.remove(event.instanceInfo.port)
        }
    }

    // check the instance status when receive the EurekaInstanceRenewedEvent
    @EventListener
    fun listen(event: EurekaInstanceRenewedEvent) {
        logger.info("EurekaInstanceRenewedEvent >>>>>>>>>> ${event.serverId}, all ${event.appName} count: ${client.getApplication(event.appName).size()}")
        if (event.isReplication) { // check if the event is replicate, only do the deal operation when the event is replicate
            client.getApplication(event.appName).instances
                    ?.takeIf {
                        it.size != targetInstances.size && // check the size of targetInstances
                                it.run {
                                    sortBy(InstanceInfo::getPort)
                                    first().port == port // check if the current instance has the most small port num
                                }
                    }?.run {
                        // check and run instance which doesn't startup
                        for ((targetPortStr, profile) in targetInstances) {
                            val targetPort = targetPortStr.replace('/', ' ').trim().toInt()
                            // checked port isn't in config
                            if (targetPort != port &&
                                    any { it.port != targetPort } &&
                                    !portsInRestart.contains(targetPort) &&
                                    checkPortCanUse(targetPort)) {
                                val command = "java -Dspring.profiles.active=native,$profile -jar $jarPath > %TEMP%\\$profile-${System.nanoTime()}.log"
                                Runtime.getRuntime().exec("cmd /c $command")
                                portsInRestart.add(targetPort) // a child started and regist will cost lots of time, use flag to avoid the duplicate restart
                                logger.info(command)
                            }
                        }
                    }
        }
    }

    @EventListener
    fun listen(event: EurekaRegistryAvailableEvent) {
        wirteLogs(event, client.applications.registeredApplications.toString())
    }

    @EventListener
    fun listen(event: EurekaServerStartedEvent) {
        wirteLogs(event, client.applications.registeredApplications.toString())
    }

    private inline fun <reified T : ApplicationEvent> wirteLogs(event: T, log: String = "...") =
            listOf("${T::class.simpleName} begin >>>>>>>>>>", event.toString(),
                    log, "${T::class.simpleName} end <<<<<<<<<<<<"
            ).forEach(logger::info)

    private fun checkPortCanUse(port: Int) = runCatching { ServerSocket(port).close() }.isSuccess

}