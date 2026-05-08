package com.smarteventbar.loadtest.config

/**
 * Central configuration for all load test scenarios.
 * Override via system properties: -DbaseUrl=http://localhost:8080
 */
object TestConfig {

  val baseUrl: String = System.getProperty("baseUrl", "http://localhost:8080")

  // WebSocket endpoint (STOMP over SockJS)
  val wsUrl: String = System.getProperty("wsUrl", baseUrl.replace("http", "ws") + "/ws")

  // Station IDs used across scenarios (assumes test data is seeded)
  val stationIds: Seq[Long] = (1L to 10L)

  // Vendor access codes per station (assumes test data is seeded)
  // In a real setup, these would be loaded from a feeder file
  val defaultAccessCode: String = System.getProperty("accessCode", "ABC123")

  // Think time ranges (seconds)
  val minThinkTime: Int = 1
  val maxThinkTime: Int = 3

  // Load profiles
  object Baseline {
    val users: Int = 50
    val rampUpSeconds: Int = 30
    val durationSeconds: Int = 300 // 5 minutes
  }

  object Peak {
    val users: Int = 200
    val rampUpSeconds: Int = 60
    val durationSeconds: Int = 300 // 5 minutes
  }

  object Stress {
    val users: Int = 500
    val rampUpSeconds: Int = 120
    val durationSeconds: Int = 300 // 5 minutes
  }

  // Assertions
  val p95ResponseTimeMs: Int = 500
  val maxErrorRatePercent: Double = 1.0
  val wsDeliveryMaxMs: Int = 1000
}
