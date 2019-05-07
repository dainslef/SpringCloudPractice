package main

import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.cloud.context.config.annotation.RefreshScope
import org.springframework.cloud.netflix.eureka.EnableEurekaClient
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@SpringBootApplication
@EnableEurekaClient
@RestController
@RefreshScope
class ClientConfig {

    @Value("\${test.name}")
    lateinit var name: String

    @GetMapping("/show")
    fun showName() = "name is: $name"

}

fun main(args: Array<String>) {
    SpringApplication.run(ClientConfig::class.java, *args)
}
