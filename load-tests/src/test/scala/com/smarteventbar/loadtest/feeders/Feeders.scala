package com.smarteventbar.loadtest.feeders

import io.gatling.core.Predef._
import com.smarteventbar.loadtest.config.TestConfig

import java.util.UUID
import scala.util.Random

/**
 * Data feeders for generating dynamic test data.
 */
object Feeders {

  private val random = new Random()

  /**
   * Generates a random station ID from the configured range.
   */
  val stationFeeder = Iterator.continually {
    val stationId = TestConfig.stationIds(random.nextInt(TestConfig.stationIds.length))
    Map("stationId" -> stationId)
  }

  /**
   * Generates a single station ID (station 1) for focused single-station tests.
   */
  val singleStationFeeder = Iterator.continually {
    Map("stationId" -> 1L)
  }

  /**
   * Generates station IDs distributed across 5-10 stations for mixed load.
   */
  val mixedStationFeeder = Iterator.continually {
    val numStations = 5 + random.nextInt(6) // 5 to 10
    val stationId = 1L + random.nextInt(numStations)
    Map("stationId" -> stationId)
  }

  /**
   * Generates unique session IDs (UUIDs).
   */
  val sessionIdFeeder = Iterator.continually {
    Map("generatedSessionId" -> UUID.randomUUID().toString)
  }

  /**
   * Generates unique idempotency keys (UUIDs).
   */
  val idempotencyKeyFeeder = Iterator.continually {
    Map("idempotencyKey" -> UUID.randomUUID().toString)
  }

  /**
   * Generates random menu item IDs for order creation.
   * Assumes test data has items with IDs 1-5 for each category.
   */
  val menuItemFeeder = Iterator.continually {
    Map(
      "spiritItemId" -> (1L + random.nextInt(5)),
      "mixerItemId" -> (1L + random.nextInt(5)),
      "premadeItemId" -> (1L + random.nextInt(5))
    )
  }

  /**
   * Generates vendor credentials for a given station.
   */
  val vendorCredentialsFeeder = Iterator.continually {
    val stationId = TestConfig.stationIds(random.nextInt(TestConfig.stationIds.length))
    Map(
      "stationId" -> stationId,
      "accessCode" -> TestConfig.defaultAccessCode
    )
  }
}
