package main

import com.netflix.discovery.EurekaClient
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.cloud.config.server.EnableConfigServer
import org.springframework.cloud.netflix.eureka.EnableEurekaClient
import org.springframework.cloud.netflix.eureka.server.EnableEurekaServer
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController

@SpringBootApplication
@EnableConfigServer
@EnableEurekaServer
@EnableEurekaClient
@RestController
class CloudServer {

    @Autowired
    lateinit var client: EurekaClient

    @GetMapping("/client-info/{serviceId}")
    fun clientInfo(@PathVariable serviceId: String) = client.getApplications(serviceId)

}

fun main(args: Array<String>) {
    SpringApplication.run(CloudServer::class.java, *args)
}
