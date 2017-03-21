/*
 * Copyright 2017 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package services

import java.util.UUID

import connectors.{InvalidDesRequest, NotFoundDesResponse, _}
import models.AccountingDetails._
import models.RegistrationStatus._
import models.{AccountingDetails, SubmissionDates, _}
import org.joda.time.DateTime
import org.mockito.{ArgumentCaptor, Matchers}
import org.scalatest.mock.MockitoSugar
import play.api.libs.json.{JsObject, Json}
import repositories.{HeldSubmission, HeldSubmissionRepository, _}
import uk.gov.hmrc.play.http.HeaderCarrier
import uk.gov.hmrc.play.test.UnitSpec
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
//import services.RegistrationHoldingPenService.MissingAccountingDates
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.audit.http.connector.AuditResult.Success

import scala.concurrent.{ExecutionContext, Future}

class RegistrationHoldingPenServiceSpec extends UnitSpec with MockitoSugar with CorporationTaxRegistrationFixture with BeforeAndAfterEach {

  val mockStateDataRepository = mock[StateDataRepository]
  val mockIncorporationCheckAPIConnector = mock[IncorporationCheckAPIConnector]
  val mockCTRepository = mock[CorporationTaxRegistrationRepository]
  val mockHeldRepo = mock[HeldSubmissionRepository]
  val mockAccountService = mock[AccountingDetailsService]
  val mockDesConnector = mock[DesConnector]
  val mockBRConnector = mock[BusinessRegistrationConnector]
  val mockAuthConnector = mock[AuthConnector]
  val mockAuditConnector = mock[AuditConnector]

  override def beforeEach() {
    resetMocks()
  }

  def resetMocks() = {
    reset(mockAuthConnector)
    reset(mockAuditConnector)
    reset(mockStateDataRepository)
    reset(mockIncorporationCheckAPIConnector)
    reset(mockCTRepository)
    reset(mockHeldRepo)
    reset(mockDesConnector)
    reset(mockBRConnector)
  }

  trait mockService extends RegistrationHoldingPenService {
    val stateDataRepository = mockStateDataRepository
    val incorporationCheckAPIConnector = mockIncorporationCheckAPIConnector
    val ctRepository = mockCTRepository
    val heldRepo = mockHeldRepo
    val accountingService = mockAccountService
    val desConnector = mockDesConnector
    val brConnector = mockBRConnector
    val auditConnector = mockAuditConnector
    val microserviceAuthConnector = mockAuthConnector
  }

  trait Setup {
    val service = new mockService {}
  }

  trait SetupMockedAudit {
    val service = new mockService {
      override def processSuccessDesResponse(item: IncorpUpdate, ctReg: CorporationTaxRegistration, auditDetail: JsObject)(implicit hc: HeaderCarrier) = Future.successful(true)
    }
  }

  def date(year: Int, month: Int, day: Int) = new DateTime(year,month,day,0,0)
  def date(yyyyMMdd:String) = DateTime.parse(yyyyMMdd)

  implicit val hc = HeaderCarrier()

  val timepoint = "123456789"
  val testAckRef = UUID.randomUUID.toString
  val testRegId = UUID.randomUUID.toString
  val transId = UUID.randomUUID().toString
  val validCR = validHeldCTRegWithData(ackRef=Some(testAckRef)).copy(
    accountsPreparation = Some(AccountPrepDetails(AccountPrepDetails.COMPANY_DEFINED,Some(date("2017-01-01"))))
  )
  import RegistrationStatus._
  val submittedCR = validCR.copy(status = SUBMITTED)
  val failCaseCR = validCR.copy(status = DRAFT)
  val incorpSuccess = IncorpUpdate(transId, "accepted", Some("012345"), Some(new DateTime(2016, 8, 10, 0, 0)), timepoint)
  val incorpRejected = IncorpUpdate(transId, "rejected", None, None, timepoint, Some("testReason"))
  val submissionCheckResponseSingle = SubmissionCheckResponse(Seq(incorpSuccess), "testNextLink")
  val submissionCheckResponseDouble = SubmissionCheckResponse(Seq(incorpSuccess,incorpSuccess), "testNextLink")
  val submissionCheckResponseNone = SubmissionCheckResponse(Seq(), "testNextLink")

  def sub(a:String, others:Option[(String, String, String, String)] = None) = {
    val extra = others match {
      case None => ""
      case Some((crn, active, firstPrep, intended)) =>
        s"""
           |  ,
           |  "crn" : "${crn}",
           |  "companyActiveDate": "${active}",
           |  "startDateOfFirstAccountingPeriod": "${firstPrep}",
           |  "intendedAccountsPreparationDate": "${intended}"
           |""".stripMargin
    }

    s"""{  "acknowledgementReference" : "${a}",
        |  "registration" : {
        |  "metadata" : {
        |  "businessType" : "Limited company",
        |  "sessionId" : "session-123",
        |  "credentialId" : "cred-123",
        |  "formCreationTimestamp": "1970-01-01T00:00:00.000Z",
        |  "submissionFromAgent": false,
        |  "language" : "ENG",
        |  "completionCapacity" : "Director",
        |  "declareAccurateAndComplete": true
        |  },
        |  "corporationTax" : {
        |  "companyOfficeNumber" : "001",
        |  "hasCompanyTakenOverBusiness" : false,
        |  "companyMemberOfGroup" : false,
        |  "companiesHouseCompanyName" : "FooBar",
        |  "returnsOnCT61" : false,
        |  "companyACharity" : false,
        |  "businessAddress" : {"line1" : "1 FooBar Avenue", "line2" : "Bar", "line3" : "Foo Town",
        |  "line4" : "Fooshire", "postcode" : "ZZ1 1ZZ", "country" : "United Kingdom"},
        |  "businessContactName" : {"firstName" : "Foo","middleNames" : "Wibble","lastName" : "Bar"},
        |  "businessContactDetails" : {"phoneNumber" : "0123457889","mobileNumber" : "07654321000","email" : "foo@bar.com"}
        |  ${extra}
        |  }
        |  }
        |}""".stripMargin
  }

  val crn = "012345"
  val exampleDate = "2012-12-12"
  val exampleDate1 = "2020-05-10"
  val exampleDate2 = "2025-06-06"
  val dates = SubmissionDates(date(exampleDate), date(exampleDate1), date(exampleDate2))

  val interimSubmission = Json.parse(sub(testAckRef)).as[JsObject]
  val validDesSubmission = Json.parse(sub(testAckRef,Some((crn,exampleDate,exampleDate1,exampleDate2)))).as[JsObject]

  val validHeld = HeldSubmission(testRegId, testAckRef, interimSubmission)

  "formatDate" should {
    "format a DateTime timestamp into the format yyyy-mm-dd" in new Setup {
      val date = DateTime.parse("1970-01-01T00:00:00.000Z")
      service.formatDate(date) shouldBe "1970-01-01"
    }
  }

  "appendDataToSubmission" should {
    "be able to add final Json additions to the PartialSubmission" in new Setup {
      val result = service.appendDataToSubmission(crn, dates, interimSubmission)

      result shouldBe validDesSubmission
    }
  }

  "checkSubmission" should {
    "return a submission if a timepoint was retrieved successfully" in new Setup {
      when(mockStateDataRepository.retrieveTimePoint).thenReturn(Future.successful(Some(timepoint)))
      when(mockIncorporationCheckAPIConnector.checkSubmission(Matchers.eq(Some(timepoint)))(Matchers.any()))
        .thenReturn(Future.successful(submissionCheckResponseSingle))

      await(service.fetchIncorpUpdate) shouldBe submissionCheckResponseSingle.items
    }

    "return a submission if a timepoint was not retrieved" in new Setup {
      when(mockStateDataRepository.retrieveTimePoint).thenReturn(Future.successful(None))
      when(mockIncorporationCheckAPIConnector.checkSubmission(Matchers.eq(None))(Matchers.any()))
        .thenReturn(Future.successful(submissionCheckResponseSingle))

      await(service.fetchIncorpUpdate) shouldBe submissionCheckResponseSingle.items
    }
  }

  "fetchHeldData" should {
    //import RegistrationHoldingPenService.FailedToRetrieveByAckRef

    "return a valid Held record found" in new Setup {
      when(mockHeldRepo.retrieveSubmissionByAckRef(Matchers.eq(testAckRef)))
        .thenReturn(Future.successful(Some(validHeld)))

      val result = service.fetchHeldData(testAckRef)
      await(result) shouldBe validHeld
    }
    //    "return a FailedToRetrieveByTxId when a record cannot be retrieved" in new Setup {
    //      when(mockHeldRepo.retrieveSubmissionByAckRef(Matchers.eq(testAckRef)))
    //        .thenReturn(Future.successful(None))
    //
    //      val result = service.fetchHeldData(testAckRef)
    //      intercept[FailedToRetrieveByAckRef](await(result))
    //    }
  }

  "activeDates" should {
    "return DoNotIntendToTrade if that was selected" in new Setup {
      import AccountingDetails.NOT_PLANNING_TO_YET
      service.activeDate(AccountingDetails(NOT_PLANNING_TO_YET, None)) shouldBe DoNotIntendToTrade
    }
    "return ActiveOnIncorporation if that was selected" in new Setup {
      import AccountingDetails.WHEN_REGISTERED
      service.activeDate(AccountingDetails(WHEN_REGISTERED, None)) shouldBe ActiveOnIncorporation
    }
    "return ActiveInFuture with a date if that was selected" in new Setup {
      import AccountingDetails.FUTURE_DATE
      val tradeDate = "2017-01-01"
      service.activeDate(AccountingDetails(FUTURE_DATE, Some(tradeDate))) shouldBe ActiveInFuture(date(tradeDate))
    }
  }

  "calculateDates" should {

    "return valid dates if correct detail is passed" in new Setup {
      when(mockAccountService.calculateSubmissionDates(Matchers.any(), Matchers.any(), Matchers.any()))
        .thenReturn(dates)

      val result = service.calculateDates(incorpSuccess, validCR.accountingDetails, validCR.accountsPreparation)

      await(result) shouldBe dates
    }
    //    "return MissingAccountingDates if no accounting dates are passed" in new Setup {
    //      when(mockAccountService.calculateSubmissionDates(Matchers.any(), Matchers.any(), Matchers.any()))
    //        .thenReturn(dates)
    //
    //      val result = service.calculateDates(incorpSuccess, None, validCR.accountsPreparation)
    //
    //      intercept[MissingAccountingDates](await(result))
    //    }
  }


  "fetchRegistrationByTxIds" should {
    // import RegistrationHoldingPenService.FailedToRetrieveByTxId

    "return a CorporationTaxRegistration document when one is found by Transaction ID" in new Setup{
      when(mockCTRepository.retrieveRegistrationByTransactionID(Matchers.eq(transId)))
        .thenReturn(Future.successful(Some(validCR)))

      await(service.fetchRegistrationByTxId(transId)) shouldBe validCR

    }
    //    "return a FailedToRetrieveByTxId when one is not found" in new Setup {
    //      when(mockCTRepository.retrieveRegistrationByTransactionID(Matchers.eq(transId)))
    //        .thenReturn(Future.successful(None))
    //
    //      intercept[FailedToRetrieveByTxId](await(service.fetchRegistrationByTxId(transId)))
    //    }
  }

  "updateHeldSubmission" should {

    val testUserDetails = UserDetailsModel("bob", "a@b.c", "organisation", Some("description"), Some("lastName"), Some("1/1/1990"), Some("PO1 1ST"), "123", "456")


    "return a true for a DES ready submission" in new Setup {

      when(mockAuthConnector.getUserDetails(Matchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(Some(testUserDetails)))

      when(mockAuditConnector.sendEvent(Matchers.any[RegistrationAuditEvent]())(Matchers.any[HeaderCarrier](), Matchers.any[ExecutionContext]()))
        .thenReturn(Future.successful(Success))

      when(mockHeldRepo.retrieveSubmissionByAckRef(Matchers.eq(testAckRef)))
        .thenReturn(Future.successful(Some(validHeld)))

      when(mockCTRepository.updateHeldToSubmitted(Matchers.eq(validCR.registrationID), Matchers.eq(incorpSuccess.crn.get), Matchers.any()))
        .thenReturn(Future.successful(true))

      when(mockHeldRepo.removeHeldDocument(Matchers.eq(validCR.registrationID)))
        .thenReturn(Future.successful(true))

      when(mockAccountService.calculateSubmissionDates(Matchers.any(), Matchers.any(), Matchers.any()))
        .thenReturn(dates)

      when(mockDesConnector.ctSubmission(Matchers.any(), Matchers.any(), Matchers.any())(Matchers.any()))
        .thenReturn(Future.successful(SuccessDesResponse(Json.obj("x"->"y"))))

      await(service.updateHeldSubmission(incorpSuccess, validCR, validCR.registrationID)) shouldBe true

      val captor = ArgumentCaptor.forClass(classOf[RegistrationAuditEvent])

      verify(mockAuditConnector, times(2)).sendEvent(captor.capture())(Matchers.any[HeaderCarrier](), Matchers.any[ExecutionContext]())

      val audit = captor.getAllValues

      audit.get(0).auditType shouldBe "successIncorpInformation"
      (audit.get(0).detail \ "incorporationDate").as[String] shouldBe "2016-08-10"

      audit.get(1).auditType shouldBe "ctRegistrationSubmission"

    }

    "fail if DES states invalid" in new Setup {
      when(mockHeldRepo.retrieveSubmissionByAckRef(Matchers.eq(testAckRef)))
        .thenReturn(Future.successful(Some(validHeld)))

      when(mockAccountService.calculateSubmissionDates(Matchers.any(), Matchers.any(), Matchers.any()))
        .thenReturn(dates)

      when(mockDesConnector.ctSubmission(Matchers.any(), Matchers.any(), Matchers.any())(Matchers.any()))
        .thenReturn(Future.successful(InvalidDesRequest("wibble")))

      when(mockAuditConnector.sendEvent(Matchers.any[RegistrationAuditEvent]())(Matchers.any[HeaderCarrier](), Matchers.any[ExecutionContext]()))
        .thenReturn(Future.successful(Success))

      val captor = ArgumentCaptor.forClass(classOf[RegistrationAuditEvent])

      intercept[InvalidSubmission] {
        await(service.updateHeldSubmission(incorpSuccess, validCR, validCR.registrationID))
      }.message should endWith ("""- Reason: "wibble".""")

      verify(mockAuditConnector, times(1)).sendEvent(captor.capture())(Matchers.any[HeaderCarrier](), Matchers.any[ExecutionContext]())

      val audit = captor.getValue
      audit.auditType shouldBe "successIncorpInformation"
    }

    "fail if DES states not found" in new Setup {
      when(mockHeldRepo.retrieveSubmissionByAckRef(Matchers.eq(testAckRef)))
        .thenReturn(Future.successful(Some(validHeld)))

      when(mockAccountService.calculateSubmissionDates(Matchers.any(), Matchers.any(), Matchers.any()))
        .thenReturn(dates)

      when(mockDesConnector.ctSubmission(Matchers.any(), Matchers.any(), Matchers.any())(Matchers.any()))
        .thenReturn(Future.successful(NotFoundDesResponse))

      when(mockAuditConnector.sendEvent(Matchers.any[RegistrationAuditEvent]())(Matchers.any[HeaderCarrier](), Matchers.any[ExecutionContext]()))
        .thenReturn(Future.successful(Success))

      val captor = ArgumentCaptor.forClass(classOf[RegistrationAuditEvent])

      intercept[InvalidSubmission] {
        await(service.updateHeldSubmission(incorpSuccess, validCR, validCR.registrationID))
      }

      verify(mockAuditConnector, times(1)).sendEvent(captor.capture())(Matchers.any[HeaderCarrier](), Matchers.any[ExecutionContext]())

      val audit = captor.getValue
      audit.auditType shouldBe "successIncorpInformation"
    }

    "fail if missing ackref" in new Setup {
      intercept[MissingAckRef] {
        await(service.updateHeldSubmission(incorpSuccess, validCR.copy(confirmationReferences = None), validCR.registrationID))
      }.message should endWith (s"""tx_id "${transId}".""")
    }
  }

  "updateSubmission" should {
    trait SetupNoProcess {
      val service = new mockService {
        implicit val hc = new HeaderCarrier()

        override def updateHeldSubmission(item: IncorpUpdate, ctReg: CorporationTaxRegistration, journeyId : String)(implicit hc : HeaderCarrier) = Future.successful(true)
        override def updateSubmittedSubmission(ctReg: CorporationTaxRegistration) = Future.successful(true)
      }
    }
    "return true for a DES ready submission" in new SetupNoProcess {
      when(mockCTRepository.retrieveRegistrationByTransactionID(Matchers.eq(transId)))
        .thenReturn(Future.successful(Some(validCR)))

      await(service.updateSubmissionWithIncorporation(incorpSuccess, validCR)) shouldBe true
    }
    "return true for a submission that is already 'Submitted" in new SetupNoProcess {
      when(mockCTRepository.retrieveRegistrationByTransactionID(Matchers.eq(transId)))
        .thenReturn(Future.successful(Some(submittedCR)))

      await(service.updateSubmissionWithIncorporation(incorpSuccess, submittedCR)) shouldBe true
    }
    "return false for a submission that is neither 'Held' nor 'Submitted'" in new SetupNoProcess {
      when(mockCTRepository.retrieveRegistrationByTransactionID(Matchers.eq(transId)))
        .thenReturn(Future.successful(Some(failCaseCR)))

      intercept[UnexpectedStatus]{await(service.updateSubmissionWithIncorporation(incorpSuccess, failCaseCR))}
    }

  }

  // TODO - II-INCORP - need the equivalent for the new service (Also an IT covering the whole lot)
  "updateNextSubmissionByTimepoint" should {
    val expected = Json.obj("key" -> timepoint)
    trait SetupNoProcess {
      val service = new mockService {
        override def processIncorporationUpdate(item: IncorpUpdate)(implicit hc: HeaderCarrier) = Future.successful(true)
      }
    }

    "return the first Timepoint for a single incorp update" in new SetupNoProcess {

      when(mockStateDataRepository.retrieveTimePoint).thenReturn(Future.successful(Some(timepoint)))

      when(mockIncorporationCheckAPIConnector.checkSubmission(Matchers.eq(Some(timepoint)))(Matchers.any()))
        .thenReturn(Future.successful(submissionCheckResponseSingle))

      when(mockStateDataRepository.updateTimepoint(Matchers.eq(timepoint)))
        .thenReturn(Future.successful(timepoint))

      val result = await(service.updateNextSubmissionByTimepoint)

      val tp = result.split(" ").reverse.head
      tp.length shouldBe 9
      tp shouldBe timepoint
    }

    "return the first Timepoint for a response with two incorp updates" in new SetupNoProcess {

      when(mockStateDataRepository.retrieveTimePoint).thenReturn(Future.successful(Some(timepoint)))

      when(mockIncorporationCheckAPIConnector.checkSubmission(Matchers.eq(Some(timepoint)))(Matchers.any()))
        .thenReturn(Future.successful(submissionCheckResponseDouble))

      when(mockStateDataRepository.updateTimepoint(Matchers.eq(timepoint)))
        .thenReturn(Future.successful(timepoint))

      val result = await(service.updateNextSubmissionByTimepoint)

      val tp = result.split(" ").reverse.head
      tp.length shouldBe 9
      tp shouldBe timepoint
    }

    "return a Json.object when there's no incorp updates" in new SetupNoProcess {

      when(mockStateDataRepository.retrieveTimePoint).thenReturn(Future.successful(Some(timepoint)))

      when(mockIncorporationCheckAPIConnector.checkSubmission(Matchers.eq(Some(timepoint)))(Matchers.any()))
        .thenReturn(Future.successful(submissionCheckResponseNone))

      val result = await(service.updateNextSubmissionByTimepoint)

      result shouldBe "No Incorporation updates were fetched"
    }

  }

  "processIncorporationUpdate" should {

    trait SetupBoolean {
      val serviceTrue = new mockService {
        override def updateSubmissionWithIncorporation(item: IncorpUpdate, ctReg: CorporationTaxRegistration)(implicit hc : HeaderCarrier) = Future.successful(true)
      }

      val serviceFalse = new mockService {
        override def updateSubmissionWithIncorporation(item: IncorpUpdate, ctReg: CorporationTaxRegistration)(implicit hc : HeaderCarrier) = Future.successful(false)
      }
    }

    "return a future true when processing an accepted incorporation" in new SetupBoolean {
      //      when(mockAuditConnector.sendEvent(Matchers.any[RegistrationAuditEvent]())(Matchers.any[HeaderCarrier](), Matchers.any[ExecutionContext]()))
      //        .thenReturn(Future.successful(Success))
      //
      //      val captor = ArgumentCaptor.forClass(classOf[RegistrationAuditEvent])

      when(mockCTRepository.retrieveRegistrationByTransactionID(Matchers.eq(transId)))
        .thenReturn(Future.successful(Some(validCR)))

      await(serviceTrue.processIncorporationUpdate(incorpSuccess)) shouldBe true
      //
      //      verify(mockAuditConnector, times(1)).sendEvent(captor.capture())(Matchers.any[HeaderCarrier](), Matchers.any[ExecutionContext]())
      //
      //      val audit = captor.getValue
      //      audit.auditType shouldBe "successIncorpInformation"

    }

    "return a FailedToUpdateSubmissionWithAcceptedIncorp when processing an accepted incorporation returns a false" in new SetupBoolean {
      //      when(mockAuditConnector.sendEvent(Matchers.any[RegistrationAuditEvent]())(Matchers.any[HeaderCarrier](), Matchers.any[ExecutionContext]()))
      //        .thenReturn(Future.successful(Success))

      //      val captor = ArgumentCaptor.forClass(classOf[RegistrationAuditEvent])
      when(mockCTRepository.retrieveRegistrationByTransactionID(Matchers.eq(transId)))
        .thenReturn(Future.successful(Some(validCR)))

      intercept[FailedToUpdateSubmissionWithAcceptedIncorp.type](await(serviceFalse.processIncorporationUpdate(incorpSuccess)) shouldBe false)

      //      verify(mockAuditConnector, times(1)).sendEvent(captor.capture())(Matchers.any[HeaderCarrier](), Matchers.any[ExecutionContext]())
      //
      //      val audit = captor.getValue
      //      audit.auditType shouldBe "successIncorpInformation"


    }
    "return a future true when processing a rejected incorporation" in new Setup{
      when(mockCTRepository.retrieveRegistrationByTransactionID(Matchers.eq(transId)))
        .thenReturn(Future.successful(Some(validCR)))

      when(mockAuditConnector.sendEvent(Matchers.any())(Matchers.any(), Matchers.any()))
        .thenReturn(Future.successful(Success))

      when(mockHeldRepo.removeHeldDocument(Matchers.eq(validCR.registrationID)))
        .thenReturn(Future.successful(true))

      when(mockCTRepository.removeTaxRegistrationById(Matchers.eq(validCR.registrationID)))
        .thenReturn(Future.successful(true))

      when(mockBRConnector.removeMetadata(Matchers.eq(validCR.registrationID))(Matchers.any()))
        .thenReturn(Future.successful(true))

      await(service.processIncorporationUpdate(incorpRejected)) shouldBe true
    }
  }
}
