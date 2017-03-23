package models

import play.api.libs.json.{Json, Writes}
import uk.gov.hmrc.play.test.UnitSpec

/**
  * Created by jackie on 23/03/17.
  */
class IncorpUpdateSpec extends UnitSpec {


  "writes" should {
    "return json" in {
      Json.toJson(IncorpUpdate)(Writes)
    }
  }
}
