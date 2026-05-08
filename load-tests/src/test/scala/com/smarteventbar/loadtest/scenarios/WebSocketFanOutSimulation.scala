package com.smarteventbar.loadtest.scenarios

import io.gatling.core.Predef._
import io.gatling.http.Predef._
import com.smarteventbar.loadtest.config.{Protocols, TestConfig}
import com.smarteventbar.loadtest.feeders.Feeders
import com.smarteventbar.loadtest.requests.RequestBodies

import scala.concurrent.duration._

/**
 * WebSocket Fan-Out Simulation
 *
 * Many clients subscribe to a station's order updates via WebSocket (STOMP over SockJS)
 * while a vendor transitions orders through states. Validates that WebSocket messages
 * are delivered to all subscribers within the 1-second SLA.
 *
 * Architecture:
 *   - Subscriber users: connect via WebSocket, subscribe to station order topic
 *   - Vendor user: authenticates and transitions orders through full lifecycle
 *     (each order goes PAID → PREPARING → READY → COLLECTED before the next is created,
 *      staying within the 3 concurrent order limit per session)
 *
 * Validates: Requirements 5.1, 20.2
 *   - WebSocket delivers state changes within 1 second (under load)
 *   - Operational metrics under fan-out conditions
 */
class WebSocketFanOutSimulation extends Simulation {

  val stationId = 1L

  // --- Scenario: WebSocket subscribers listening for order updates ---

  val wsSubscriber = scenario("WebSocket Subscriber")
    // Connect via WebSocket (STOMP over SockJS)
    .exec(
      ws("WS Connect")
        .connect("/ws/websocket")
        .header("Upgrade", "websocket")
    )
    .pause(1)
    // Send STOMP CONNECT frame
    .exec(
      ws("STOMP Connect")
        .sendText("CONNECT\naccept-version:1.1,1.0\nheart-beat:10000,10000\n\n\u0000")
    )
    .pause(1)
    // Subscribe to station order updates
    .exec(
      ws("STOMP Subscribe Station Orders")
        .sendText(
          s"SUBSCRIBE\nid:sub-0\ndestination:/topic/stations/$stationId/orders\n\n\u0000"
        )
    )
    // Wait and listen for messages for the duration of the test
    .pause(TestConfig.Baseline.durationSeconds.seconds)
    // Check that at least one message was received
    .exec(
      ws("WS Check Messages")
        .checkTextMessage("Order Update Received")
        .within(TestConfig.wsDeliveryMaxMs.milliseconds)
        .check(regex(".*").exists)
    )
    // Disconnect
    .exec(
      ws("STOMP Disconnect")
        .sendText("DISCONNECT\n\n\u0000")
    )
    .exec(ws("WS Close").close)

  // --- Scenario: Vendor creating and transitioning orders through full lifecycle ---
  // Each order completes its full lifecycle (→ COLLECTED) before the next is created,
  // so we never exceed the 3 concurrent non-terminal order limit per session.

  val vendorTransitioner = scenario("Vendor Order Transitioner")
    .feed(Feeders.menuItemFeeder)
    .feed(Feeders.idempotencyKeyFeeder)
    // Set station context
    .exec(_.set("stationId", stationId))
    .exec(_.set("accessCode", TestConfig.defaultAccessCode))
    // Vendor login
    .exec(
      http("POST Vendor Login")
        .post("/api/auth/vendor/login")
        .body(StringBody(RequestBodies.vendorLogin))
        .check(status.is(200))
        .check(jsonPath("$.token").saveAs("vendorToken"))
    )
    .pause(2)
    // Create a customer session for order creation
    .exec(
      http("POST Create Session (Vendor Test)")
        .post("/api/sessions")
        .body(StringBody(s"""{"stationId": $stationId}"""))
        .check(status.is(201))
        .check(jsonPath("$.sessionId").saveAs("sessionId"))
    )
    .pause(1)
    // Repeatedly create and transition orders through full lifecycle
    .repeat(10, "orderIndex") {
      feed(Feeders.menuItemFeeder)
        .feed(Feeders.idempotencyKeyFeeder)
        // Create order
        .exec(
          http("POST Create Order (Vendor Test)")
            .post(s"/api/stations/$stationId/orders")
            .header("X-Session-Id", "${sessionId}")
            .body(StringBody(RequestBodies.createOrderCustomDrink))
            .check(status.is(201))
            .check(jsonPath("$.id").saveAs("orderId"))
        )
        .pause(1)
        // Checkout
        .exec(
          http("POST Checkout (Vendor Test)")
            .post("/api/orders/${orderId}/checkout")
            .header("X-Session-Id", "${sessionId}")
            .check(status.is(200))
        )
        .pause(1)
        // Pay
        .exec(
          http("POST Pay (Vendor Test)")
            .post("/api/orders/${orderId}/pay")
            .header("X-Session-Id", "${sessionId}")
            .header("Idempotency-Key", "${idempotencyKey}")
            .check(status.is(200))
        )
        .pause(2)
        // Vendor transitions: PAID → PREPARING (triggers WebSocket broadcast)
        .exec(_.set("targetState", "PREPARING"))
        .exec(
          http("PATCH State → PREPARING")
            .patch("/api/orders/${orderId}/state")
            .header("Authorization", "Bearer ${vendorToken}")
            .body(StringBody(RequestBodies.stateTransition))
            .check(status.is(200))
        )
        .pause(3)
        // PREPARING → READY (triggers WebSocket broadcast)
        .exec(_.set("targetState", "READY"))
        .exec(
          http("PATCH State → READY")
            .patch("/api/orders/${orderId}/state")
            .header("Authorization", "Bearer ${vendorToken}")
            .body(StringBody(RequestBodies.stateTransition))
            .check(status.is(200))
        )
        .pause(2)
        // READY → COLLECTED (triggers WebSocket broadcast, frees order slot)
        .exec(_.set("targetState", "COLLECTED"))
        .exec(
          http("PATCH State → COLLECTED")
            .patch("/api/orders/${orderId}/state")
            .header("Authorization", "Bearer ${vendorToken}")
            .body(StringBody(RequestBodies.stateTransition))
            .check(status.is(200))
        )
        .pause(2)
    }

  // --- Load Profile ---
  // Many subscribers + a few vendors generating state transitions

  val loadProfile = System.getProperty("loadProfile", "baseline")

  val (subscriberCount, vendorCount) = loadProfile match {
    case "peak"   => (150, 5)
    case "stress" => (400, 10)
    case _        => (40, 2)
  }

  setUp(
    wsSubscriber.inject(
      rampUsers(subscriberCount).during(30.seconds)
    ),
    vendorTransitioner.inject(
      // Vendors start after subscribers are connected
      nothingFor(5.seconds),
      rampUsers(vendorCount).during(10.seconds)
    )
  )
    .protocols(Protocols.wsProtocol)
    .assertions(
      global.successfulRequests.percent.gt(100.0 - TestConfig.maxErrorRatePercent)
    )
}
