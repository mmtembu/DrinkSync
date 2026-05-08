package com.smarteventbar.loadtest.requests

/**
 * JSON request body templates for API calls.
 * Uses Gatling EL (Expression Language) for dynamic values.
 */
object RequestBodies {

  val createSession: String =
    """{"stationId": ${stationId}}"""

  // Order with a custom drink (spirit + mixer + cup option)
  val createOrderCustomDrink: String =
    """[
      |  {
      |    "itemType": "CUSTOM_DRINK",
      |    "spiritItemId": ${spiritItemId},
      |    "mixerItemId": ${mixerItemId},
      |    "cupOption": "NEW_CUP",
      |    "quantity": 1
      |  }
      |]""".stripMargin

  // Order with a premade item
  val createOrderPremade: String =
    """[
      |  {
      |    "itemType": "PREMADE",
      |    "premadeItemId": ${premadeItemId},
      |    "quantity": 1
      |  }
      |]""".stripMargin

  // Mixed order with both custom drink and premade
  val createOrderMixed: String =
    """[
      |  {
      |    "itemType": "CUSTOM_DRINK",
      |    "spiritItemId": ${spiritItemId},
      |    "mixerItemId": ${mixerItemId},
      |    "cupOption": "REUSE_CUP",
      |    "quantity": 1
      |  },
      |  {
      |    "itemType": "PREMADE",
      |    "premadeItemId": ${premadeItemId},
      |    "quantity": 2
      |  }
      |]""".stripMargin

  val vendorLogin: String =
    """{"stationId": ${stationId}, "accessCode": "${accessCode}"}"""

  val stateTransition: String =
    """{"targetState": "${targetState}"}"""
}
