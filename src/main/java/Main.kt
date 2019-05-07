package main

import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@SpringBootApplication
@RestController
class BaseConfig {

    @GetMapping("/echo/{text}")
    fun echo(@PathVariable(required = false) text: String?) = "echo: ${text ?: "none"}"

    @GetMapping("/param")
    fun param(@RequestParam(required = false) text: String?) = "param: ${text ?: "none"}"

}

fun main(args: Array<String>) {
    SpringApplication.run(BaseConfig::class.java, *args)
}

