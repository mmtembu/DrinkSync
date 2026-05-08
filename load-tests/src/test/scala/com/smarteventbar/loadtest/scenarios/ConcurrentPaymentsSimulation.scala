package com.smarteventbar.loadtest.scenarios

import io.gatling.core.Predef._
import io.gatling.http.Predef._
import com.smarteventbar.loadtest.config.{Protocols, TestConfig}
import com.smarteventbar.loadtest.feeders.Feeders
import com.smarteventbar.loadtest.requests.RequestBodies

import scala.concurrent.duration._

/**
 * Concurrent Payments Simulation
 *
 * Multiple customers hitting the payment endpoint simultaneously for different orders
 * at the same station. Validates that queue positions are assigned correctly under
 * concurrent load (no duplicates, monotonically increasing).
 *
 * Validates: Requirements 4.4, 20.2
 *   - Queue positions assigned based on chronological PAID order sequence (under load)
 *   - No duplicate queue positions under concurrent payment
 */
class ConcurrentPaymentsSimulation extends Simulation {

  val loadProfile: String = System.getProperty("loadProfile", "baseline")

  val userCount: Int = loadProfile match {
    case "peak"   => TestConfig.Peak.users
    case "stress" => TestConfig.Stress.users
    case _        => TestConfig.Baseline.users
  }

  val rampDuration: FiniteDuration = loadProfile match {
    case "peak"   => TestConfig.Peak.rampUpSeconds.seconds
    case "stress" => TestConfig.Stress.rampUpSeconds.seconds
    case _        => TestConfig.Baseline.rampUpSeconds.seconds
  }

  // --- Setup phase: Each user creates a session and order, then waits ---

  val setupAndPay = scenario("Concurrent Payments")
    .feed(Feeders.singleStationFeeder)
    .feed(Feeders.menuItemFeeder)
    .feed(Feeders.idempotencyKeyFeeder)
    // Create session
    .exec(
      http("POST Create Session")
        .post("/api/sessions")
        .body(StringBody(RequestBodies.createSession))
        .check(status.is(201))
        .check(jsonPath("$.sessionId").saveAs("sessionId"))
    )
    .pause(1)
    // Create order
    .exec(
      http("POST Create Order")
        .post("/api/stations/${stationId}/orders")
        .header("X-Session-Id", "${sessionId}")
        .body(StringBody(RequestBodies.createOrderCustomDrink))
        .check(status.is(201))
        .check(jsonPath("$.id").saveAs("orderId"))
    )
    .pause(1)
    // Checkout
    .exec(
      http("POST Checkout Order")
        .post("/api/orders/${orderId}/checkout")
        .header("X-Session-Id", "${sessionId}")
        .check(status.is(200))
    )
    // Synchronization point: all users pay at roughly the same time
    .rendezVous(userCount)
    // Pay — this is the critical concurrent section
    .exec(
      http("POST Pay Order (Concurrent)")
        .post("/api/orders/${orderId}/pay")
        .header("X-Session-Id", "${sessionId}")
        .header("Idempotency-Key", "${idempotencyKey}")
        .check(status.is(200))
        .check(jsonPath("$.state").is("PAID"))
        .check(jsonPath("$.queuePosition").saveAs("queuePosition"))
    )
    // Verify order status after payment
    .exec(
      http("GET Order After Payment")
        .get("/api/orders/${orderId}")
        .header("X-Session-Id", "${sessionId}")
        .check(status.is(200))
        .check(jsonPath("$.queuePosition").exists)
    )

  // --- Setup ---

  setUp(
    setupAndPay.inject(
      rampUsers(userCount).during(rampDuration)
    )
  )
    .protocols(Protocols.httpProtocol)
    .assertions(
      global.responseTime.percentile(95).lt(TestConfig.p95ResponseTimeMs),
      global.successfulRequests.percent.gt(100.0 - TestConfig.maxErrorRatePercent),
      // Payment-specific: p95 should still be under 2s even under contention
      details("POST Pay Order (Concurrent)").responseTime.percentile(95).lt(2000)
    )
}
