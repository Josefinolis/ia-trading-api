package com.iatrading

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableScheduling
class IaTradingApiApplication

fun main(args: Array<String>) {
    runApplication<IaTradingApiApplication>(*args)
}
