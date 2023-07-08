package com.example.springws

import com.example.springws.hello.Greeting
import com.example.springws.hello.HelloMessage
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinFeature
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.SingletonSupport
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.messaging.converter.MappingJackson2MessageConverter
import org.springframework.messaging.simp.stomp.*
import org.springframework.web.socket.WebSocketHttpHeaders
import org.springframework.web.socket.client.standard.StandardWebSocketClient
import org.springframework.web.socket.messaging.WebSocketStompClient
import org.springframework.web.socket.sockjs.client.SockJsClient
import org.springframework.web.socket.sockjs.client.Transport
import org.springframework.web.socket.sockjs.client.WebSocketTransport
import java.lang.reflect.Type
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference


@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GreetingIntegrationTests {
    @Value(value = "\${local.server.port}")
    private val port = 0
    private var sockJsClient: SockJsClient? = null
    private var stompClient: WebSocketStompClient? = null
    private val headers = WebSocketHttpHeaders()

    @BeforeEach
    fun setup() {
        val transports: MutableList<Transport> = ArrayList()
        transports.add(WebSocketTransport(StandardWebSocketClient()))
        sockJsClient = SockJsClient(transports)
        stompClient = WebSocketStompClient(sockJsClient!!)
        val objectMapper = ObjectMapper()
        objectMapper.registerModule(KotlinModule.Builder()
                .withReflectionCacheSize(512)
                .configure(KotlinFeature.NullToEmptyCollection, false)
                .configure(KotlinFeature.NullToEmptyMap, false)
                .configure(KotlinFeature.NullIsSameAsDefault, false)
                .configure(KotlinFeature.SingletonSupport, false)
                .configure(KotlinFeature.StrictNullChecks, false)
                .build())
        val converter = MappingJackson2MessageConverter()
        converter.objectMapper = objectMapper
        stompClient!!.messageConverter = converter
    }

    @Test
    fun greeting(): Unit {
        val latch = CountDownLatch(1)
        val failure = AtomicReference<Throwable?>()
        val handler: StompSessionHandler = object : TestSessionHandler(failure) {
            override fun afterConnected(session: StompSession, connectedHeaders: StompHeaders) {
                session.subscribe("/topic/greetings", object : StompFrameHandler {
                    override fun getPayloadType(headers: StompHeaders): Type {
                        return Greeting::class.java
                    }

                    override fun handleFrame(headers: StompHeaders, payload: Any?) {
                        val greeting = payload as Greeting?
                        try {
                            Assertions.assertEquals("Hello, Spring!", greeting!!.content)
                        } catch (t: Throwable) {
                            failure.set(t)
                        } finally {
                            session.disconnect()
                            latch.countDown()
                        }
                    }
                })
                try {
                    session.send("/app/hello", HelloMessage("Spring"))
                } catch (t: Throwable) {
                    failure.set(t)
                    latch.countDown()
                }
            }
        }
        stompClient!!.connect("ws://localhost:{port}/gs-guide-websocket", headers, handler, port)
        if (latch.await(3, TimeUnit.SECONDS)) {
            if (failure.get() != null) {
                throw AssertionError("", failure.get())
            }
        } else {
            Assertions.fail<Any>("Greeting not received")
        }
    }

    private open inner class TestSessionHandler(private val failure: AtomicReference<Throwable?>) : StompSessionHandlerAdapter() {
        override fun handleFrame(headers: StompHeaders, payload: Any?) {
            failure.set(Exception(headers.toString()))
        }

        override fun handleException(s: StompSession, c: StompCommand?, h: StompHeaders, p: ByteArray, ex: Throwable) {
            failure.set(ex)
        }

        override fun handleTransportError(session: StompSession, ex: Throwable) {
            failure.set(ex)
        }
    }
}
