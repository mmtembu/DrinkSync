package com.smarteventbar.loadtest.scenarios

import io.gatling.core.Predef._
import io.gatling.http.Predef._
import com.smarteventbar.loadtest.config.{Protocols, TestConfig}
import com.smarteventbar.loadtest.feeders.Feeders

import scala.concurrent.duration._

/**
 * Menu Browsing Spike Simulation
 *
 * Simulates a sudden burst of GET menu requests, as would happen when an event
 * starts and many customers scan QR codes simultaneously. This is a read-heavy
 * scenario that tests the station and menu endpoints under spike load.
 *
 * Validates: Requirements 20.2
 *   - Operational metrics (response times, error rates) under spike conditions
 *   - Menu endpoint can handle burst traffic without degradation
 */
class MenuBrowsingSpikeSimulation extends Simulation {

  // --- Scenario: Burst of menu browsing requests ---

  val menuBrowsingSpike = scenario("Menu Browsing Spike")
    .feed(Feeders.stationFeeder)
    // Step 1: Get station details (QR scan landing)
    .exec(
      http("GET Station Details")
        .get("/api/stations/${stationId}")
        .check(status.is(200))
        .check(jsonPath("$.id").exists)
        .check(jsonPath("$.name").exists)
    )
    .pause(500.milliseconds, 1500.milliseconds)
    // Step 2: Load the full menu
    .exec(
      http("GET Station Menu")
        .get("/api/stations/${stationId}/menu")
        .check(status.is(200))
        .check(jsonPath("$.spirits").exists)
        .check(jsonPath("$.mixers").exists)
        .check(jsonPath("$.premades").exists)
    )
    .pause(TestConfig.minThinkTime, TestConfig.maxThinkTime)
    // Step 3: Some users browse the menu again (refresh / navigate back)
    .randomSwitch(
      40.0 -> exec(
        http("GET Station Menu (Refresh)")
          .get("/api/stations/${stationId}/menu")
          .check(status.is(200))
      ),
      20.0 -> exec(
        http("GET Station Details (Re-check)")
          .get("/api/stations/${stationId}")
          .check(status.is(200))
      )
    )

  // --- Load Profiles ---
  // Spike pattern: sudden burst followed by sustained load

  val loadProfile = System.getProperty("loadProfile", "baseline")

  val selectedProfile = loadProfile match {
    case "peak" =>
      menuBrowsingSpike.inject(
        // Sudden spike: 100 users in 5 seconds, then sustained 100 more over 2 minutes
        atOnceUsers(100),
        rampUsers(100).during(120.seconds)
      )
    case "stress" =>
      menuBrowsingSpike.inject(
        // Massive spike: 250 users in 5 seconds, then sustained 250 more over 2 minutes
        atOnceUsers(250),
        rampUsers(250).during(120.seconds)
      )
    case _ =>
      menuBrowsingSpike.inject(
        // Moderate spike: 30 users at once, then 20 more over 2 minutes
        atOnceUsers(30),
        rampUsers(20).during(120.seconds)
      )
  }

  setUp(selectedProfile)
    .protocols(Protocols.httpProtocol)
    .assertions(
      // Menu browsing should be fast — p95 < 300ms for reads
      global.responseTime.percentile(95).lt(300),
      global.successfulRequests.percent.gt(99.0),
      // No 5xx errors allowed for read-only endpoints
      details("GET Station Menu").failedRequests.count.lt(1L)
    )
}
