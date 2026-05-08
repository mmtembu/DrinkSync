package com.smarteventbar.loadtest.config

import io.gatling.core.Predef._
import io.gatling.http.Predef._

/**
 * Shared HTTP and WebSocket protocol configurations.
 */
object Protocols {

  val httpProtocol = http
    .baseUrl(TestConfig.baseUrl)
    .acceptHeader("application/json")
    .contentTypeHeader("application/json")
    .userAgentHeader("SmartEventBar-LoadTest/1.0")
    .disableCaching

  val wsProtocol = http
    .baseUrl(TestConfig.baseUrl)
    .acceptHeader("application/json")
    .contentTypeHeader("application/json")
    .wsBaseUrl(TestConfig.wsUrl)
}
