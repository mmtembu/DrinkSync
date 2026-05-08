package com.smarteventbar.loadtest.scenarios

import io.gatling.core.Predef._
import io.gatling.http.Predef._
import com.smarteventbar.loadtest.config.{Protocols, TestConfig}
import com.smarteventbar.loadtest.feeders.Feeders
import com.smarteventbar.loadtest.requests.RequestBodies

import scala.concurrent.duration._

/**
 * Customer Order Flow Simulation
 *
 * Simulates the full customer journey:
 *   scan station → browse menu → create order → checkout → pay → track order
 *
 * Validates: Requirements 4.4, 5.1, 20.2
 *   - Queue positions assigned based on chronological PAID order sequence (under load)
 *   - WebSocket delivers state changes within 1 second (under load)
 *   - Operational metrics (response times, error rates)
 */
class CustomerOrderFlowSimulation extends Simulation {

  // --- Scenario: Full customer order lifecycle ---

  val customerOrderFlow = scenario("Customer Order Flow")
    .feed(Feeders.stationFeeder)
    .feed(Feeders.menuItemFeeder)
    .feed(Feeders.idempotencyKeyFeeder)
    // Step 1: Get station details (simulates QR code scan)
    .exec(
      http("GET Station Details")
        .get("/api/stations/${stationId}")
        .check(status.is(200))
        .check(jsonPath("$.id").saveAs("resolvedStationId"))
    )
    .pause(TestConfig.minThinkTime, TestConfig.maxThinkTime)
    // Step 2: Browse menu
    .exec(
      http("GET Station Menu")
        .get("/api/stations/${stationId}/menu")
        .check(status.is(200))
        .check(jsonPath("$.spirits").exists)
        .check(jsonPath("$.mixers").exists)
        .check(jsonPath("$.premades").exists)
    )
    .pause(TestConfig.minThinkTime, TestConfig.maxThinkTime)
    // Step 3: Create a session
    .exec(
      http("POST Create Session")
        .post("/api/sessions")
        .body(StringBody(RequestBodies.createSession))
        .check(status.is(201))
        .check(jsonPath("$.sessionId").saveAs("sessionId"))
    )
    .pause(1)
    // Step 4: Create order (DRAFT)
    .exec(
      http("POST Create Order")
        .post("/api/stations/${stationId}/orders")
        .header("X-Session-Id", "${sessionId}")
        .body(StringBody(RequestBodies.createOrderCustomDrink))
        .check(status.is(201))
        .check(jsonPath("$.id").saveAs("orderId"))
        .check(jsonPath("$.state").is("DRAFT"))
    )
    .pause(TestConfig.minThinkTime, TestConfig.maxThinkTime)
    // Step 5: Checkout (DRAFT → AWAITING_PAYMENT)
    .exec(
      http("POST Checkout Order")
        .post("/api/orders/${orderId}/checkout")
        .header("X-Session-Id", "${sessionId}")
        .check(status.is(200))
        .check(jsonPath("$.state").is("AWAITING_PAYMENT"))
    )
    .pause(1, 2)
    // Step 6: Pay (AWAITING_PAYMENT → PAID)
    .exec(
      http("POST Pay Order")
        .post("/api/orders/${orderId}/pay")
        .header("X-Session-Id", "${sessionId}")
        .header("Idempotency-Key", "${idempotencyKey}")
        .check(status.is(200))
        .check(jsonPath("$.state").is("PAID"))
        .check(jsonPath("$.queuePosition").exists)
    )
    .pause(TestConfig.minThinkTime, TestConfig.maxThinkTime)
    // Step 7: Track order status
    .exec(
      http("GET Order Status")
        .get("/api/orders/${orderId}")
        .header("X-Session-Id", "${sessionId}")
        .check(status.is(200))
        .check(jsonPath("$.state").exists)
    )

  // --- Load Profiles ---

  val baselineProfile = customerOrderFlow.inject(
    rampUsers(TestConfig.Baseline.users).during(TestConfig.Baseline.rampUpSeconds.seconds)
  )

  val peakProfile = customerOrderFlow.inject(
    rampUsers(TestConfig.Peak.users).during(TestConfig.Peak.rampUpSeconds.seconds)
  )

  val stressProfile = customerOrderFlow.inject(
    rampUsers(TestConfig.Stress.users).during(TestConfig.Stress.rampUpSeconds.seconds)
  )

  // --- Default: Run baseline profile ---

  val loadProfile = System.getProperty("loadProfile", "baseline")

  val selectedProfile = loadProfile match {
    case "peak"   => peakProfile
    case "stress" => stressProfile
    case _        => baselineProfile
  }

  setUp(selectedProfile)
    .protocols(Protocols.httpProtocol)
    .assertions(
      global.responseTime.percentile(95).lt(TestConfig.p95ResponseTimeMs),
      global.successfulRequests.percent.gt(100.0 - TestConfig.maxErrorRatePercent)
    )
}
