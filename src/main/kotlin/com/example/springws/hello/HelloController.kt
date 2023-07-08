package com.example.springws.hello

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.springframework.messaging.handler.annotation.MessageMapping
import org.springframework.messaging.handler.annotation.SendTo
import org.springframework.stereotype.Controller
import org.springframework.web.util.HtmlUtils

@Controller
class HelloController {

    @MessageMapping("/hello")
    @SendTo("/topic/greetings")
    @Throws(Exception::class)
    fun greeting(message: HelloMessage): Greeting? =
        runBlocking(Dispatchers.IO) {
            delay(1000) // simulated delay
            Greeting("Hello, " + HtmlUtils.htmlEscape(message.name) + "!")
        }
}
