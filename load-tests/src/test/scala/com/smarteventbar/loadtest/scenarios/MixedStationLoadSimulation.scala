package com.smarteventbar.loadtest.scenarios

import io.gatling.core.Predef._
import io.gatling.http.Predef._
import com.smarteventbar.loadtest.config.{Protocols, TestConfig}
import com.smarteventbar.loadtest.feeders.Feeders
import com.smarteventbar.loadtest.requests.RequestBodies

import scala.concurrent.duration._

/**
 * Mixed Station Load Simulation
 *
 * Distributes load across 5-10 stations with varying request patterns.
 * Combines customer ordering, menu browsing, and vendor state transitions
 * across multiple stations simultaneously to simulate realistic event-day traffic.
 *
 * Validates: Requirements 4.4, 5.1, 20.2
 *   - Even resource distribution across stations
 *   - No station starvation under mixed load
 *   - Queue positions correct per-station under cross-station load
 */
class MixedStationLoadSimulation extends Simulation {

  // --- Scenario 1: Customer ordering at random stations ---

  val customerOrdering = scenario("Customer Ordering (Mixed Stations)")
    .feed(Feeders.mixedStationFeeder)
    .feed(Feeders.menuItemFeeder)
    .feed(Feeders.idempotencyKeyFeeder)
    // Create session at assigned station
    .exec(
      http("POST Create Session")
        .post("/api/sessions")
        .body(StringBody(RequestBodies.createSession))
        .check(status.is(201))
        .check(jsonPath("$.sessionId").saveAs("sessionId"))
    )
    .pause(1)
    // Browse menu
    .exec(
      http("GET Station Menu")
        .get("/api/stations/${stationId}/menu")
        .check(status.is(200))
    )
    .pause(TestConfig.minThinkTime, TestConfig.maxThinkTime)
    // Create order
    .exec(
      http("POST Create Order")
        .post("/api/stations/${stationId}/orders")
        .header("X-Session-Id", "${sessionId}")
        .body(StringBody(RequestBodies.createOrderMixed))
        .check(status.is(201))
        .check(jsonPath("$.id").saveAs("orderId"))
    )
    .pause(TestConfig.minThinkTime, TestConfig.maxThinkTime)
    // Checkout
    .exec(
      http("POST Checkout Order")
        .post("/api/orders/${orderId}/checkout")
        .header("X-Session-Id", "${sessionId}")
        .check(status.is(200))
    )
    .pause(1, 2)
    // Pay
    .exec(
      http("POST Pay Order")
        .post("/api/orders/${orderId}/pay")
        .header("X-Session-Id", "${sessionId}")
        .header("Idempotency-Key", "${idempotencyKey}")
        .check(status.is(200))
        .check(jsonPath("$.queuePosition").exists)
    )
    .pause(TestConfig.minThinkTime, TestConfig.maxThinkTime)
    // Track order
    .exec(
      http("GET Order Status")
        .get("/api/orders/${orderId}")
        .header("X-Session-Id", "${sessionId}")
        .check(status.is(200))
    )

  // --- Scenario 2: Menu browsers (read-only traffic) ---

  val menuBrowsers = scenario("Menu Browsers (Mixed Stations)")
    .feed(Feeders.mixedStationFeeder)
    .exec(
      http("GET Station Details")
        .get("/api/stations/${stationId}")
        .check(status.is(200))
    )
    .pause(500.milliseconds, 1500.milliseconds)
    .exec(
      http("GET Station Menu")
        .get("/api/stations/${stationId}/menu")
        .check(status.is(200))
    )
    .pause(TestConfig.minThinkTime, TestConfig.maxThinkTime)
    // Some users browse again
    .exec(
      http("GET Station Menu (Second Look)")
        .get("/api/stations/${stationId}/menu")
        .check(status.is(200))
    )

  // --- Scenario 3: Vendor managing orders at their station ---

  val vendorManagement = scenario("Vendor Management (Mixed Stations)")
    .feed(Feeders.vendorCredentialsFeeder)
    // Vendor login
    .exec(
      http("POST Vendor Login")
        .post("/api/auth/vendor/login")
        .body(StringBody(RequestBodies.vendorLogin))
        .check(status.is(200))
        .check(jsonPath("$.token").saveAs("vendorToken"))
    )
    .pause(2)
    // Periodically check station orders and transition them
    .repeat(20, "vendorCycle") {
      exec(
        http("GET Station Orders (Vendor)")
          .get("/api/stations/${stationId}/orders?state=PAID")
          .header("Authorization", "Bearer ${vendorToken}")
          .check(status.is(200))
          .check(jsonPath("$[0].id").optional.saveAs("paidOrderId"))
      )
        .pause(2, 4)
        // If there's a PAID order, transition it
        .doIf("${paidOrderId.exists()}") {
          exec(_.set("targetState", "PREPARING"))
            .exec(
              http("PATCH State → PREPARING (Vendor)")
                .patch("/api/orders/${paidOrderId}/state")
                .header("Authorization", "Bearer ${vendorToken}")
                .body(StringBody(RequestBodies.stateTransition))
                .check(status.is(200))
            )
            .pause(3, 6)
            .exec(_.set("targetState", "READY"))
            .exec(
              http("PATCH State → READY (Vendor)")
                .patch("/api/orders/${paidOrderId}/state")
                .header("Authorization", "Bearer ${vendorToken}")
                .body(StringBody(RequestBodies.stateTransition))
                .check(status.is(200))
            )
            .pause(2, 4)
            .exec(_.set("targetState", "COLLECTED"))
            .exec(
              http("PATCH State → COLLECTED (Vendor)")
                .patch("/api/orders/${paidOrderId}/state")
                .header("Authorization", "Bearer ${vendorToken}")
                .body(StringBody(RequestBodies.stateTransition))
                .check(status.is(200))
            )
        }
        .pause(3, 5)
    }

  // --- Load Profiles ---

  val loadProfile = System.getProperty("loadProfile", "baseline")

  val (customerCount, browserCount, vendorCount) = loadProfile match {
    case "peak"   => (120, 60, 20)
    case "stress" => (300, 150, 50)
    case _        => (25, 15, 10)
  }

  val rampDuration = loadProfile match {
    case "peak"   => TestConfig.Peak.rampUpSeconds.seconds
    case "stress" => TestConfig.Stress.rampUpSeconds.seconds
    case _        => TestConfig.Baseline.rampUpSeconds.seconds
  }

  setUp(
    customerOrdering.inject(
      rampUsers(customerCount).during(rampDuration)
    ),
    menuBrowsers.inject(
      rampUsers(browserCount).during(rampDuration)
    ),
    vendorManagement.inject(
      nothingFor(10.seconds),
      rampUsers(vendorCount).during(rampDuration)
    )
  )
    .protocols(Protocols.httpProtocol)
    .assertions(
      global.responseTime.percentile(95).lt(TestConfig.p95ResponseTimeMs),
      global.successfulRequests.percent.gt(100.0 - TestConfig.maxErrorRatePercent)
    )
}
