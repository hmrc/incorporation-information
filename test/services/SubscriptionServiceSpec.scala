package services

import Helpers.SCRSSpec
import repositories.{IncorpUpdateRepository, SubscriptionsRepository}

/**
  * Created by jackie on 29/03/17.
  */
class SubscriptionServiceSpec extends SCRSSpec {

  val mockSubRepo = mock[SubscriptionsRepository]
  val mockIncorpRepo = mock[IncorpUpdateRepository]

  trait Setup {
    val service = new SubscriptionService {
      override val subRepo = mockSubRepo
      override val incorpRepo = mockIncorpRepo
    }
  }

  "checkForSubscription" should {
    "return an incorp update for a subscription that exists" in new Setup {
      //when(service.checkForIncorpUpdate())
    }
  }

}
