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

import javax.inject.Inject

import config.MicroserviceAuditConnector
import connectors._
import models._
import play.api.Logger
import play.api.libs.json.{JsObject, Json}
import repositories._
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.control.NoStackTrace


object RegistrationHoldingPenService
  extends RegistrationHoldingPenService {

  override val incorporationCheckAPIConnector = IncorporationCheckAPIConnector
}

private[services] class InvalidSubmission(val message: String) extends NoStackTrace
private[services] case class DesError(message: String) extends NoStackTrace
private[services] class MissingAckRef(val message: String) extends NoStackTrace
private[services] class UnexpectedStatus(val status: String) extends NoStackTrace

private[services] object FailedToUpdateSubmissionWithAcceptedIncorp extends NoStackTrace
private[services] object FailedToUpdateSubmissionWithRejectedIncorp extends NoStackTrace

trait RegistrationHoldingPenService {


  val incorporationCheckAPIConnector: IncorporationCheckAPIConnector


  private[services] case class FailedToRetrieveByTxId(transId: String) extends NoStackTrace
  private[services] class FailedToRetrieveByAckRef extends NoStackTrace
  private[services] class MissingAccountingDates extends NoStackTrace

  // TODO - II-INCORP - needs re-working - don't need the Future Sequence anymore
//  def updateNextSubmissionByTimepoint(implicit hc: HeaderCarrier): Future[String] = {
//    fetchIncorpUpdate flatMap { items =>
//      val results = items map { item =>
//        TODO see SCRS-3766
//        processIncorporationUpdate(item)
//      }
//      Future.sequence(results) flatMap { _ =>
//        TODO For day one, take the first timepoint - see SCRS-3766
//        items.headOption match {
//          case Some(head) => stateDataRepository.updateTimepoint(head.timepoint).map(tp => s"Incorporation ${head.status} - Timepoint updated to $tp")   // TODO - II-INCORP
//          case None => Future.successful("No Incorporation updates were fetched")
//        }
//      }
//    }
//  }

//  private[services] def processIncorporationUpdate(item : IncorpUpdate)(implicit hc: HeaderCarrier): Future[Boolean] = {
//    item.status match {
//      case "accepted" =>
//        for {
//          ctReg <- fetchRegistrationByTxId(item.transactionId)
//          result <- updateSubmissionWithIncorporation(item, ctReg)
//        } yield {
//          if(result) result else throw FailedToUpdateSubmissionWithAcceptedIncorp
//        }
//      case "rejected" =>
//        val reason = item.statusDescription.fold("No reason given")(f => " Reason given:" + f)
//        Logger.info("Incorporation rejected for Transaction: " + item.transactionId + reason)
//        for{
//          ctReg <- fetchRegistrationByTxId(item.transactionId)
//          _ <- auditFailedIncorporation(item, ctReg)
//          heldDeleted <- heldRepo.removeHeldDocument(ctReg.registrationID)
//          ctDeleted <- ctRepository.removeTaxRegistrationById(ctReg.registrationID)
//          metadataDeleted <- brConnector.removeMetadata(ctReg.registrationID)
//        } yield {
//          if(heldDeleted && ctDeleted && metadataDeleted) true else throw FailedToUpdateSubmissionWithRejectedIncorp
//        }
//    }
//  }
//
//  private[services] def updateSubmissionWithIncorporation(item: IncorpUpdate, ctReg: CorporationTaxRegistration)(implicit hc : HeaderCarrier): Future[Boolean] = {
//    import RegistrationStatus.{HELD,SUBMITTED}
//    ctReg.status match {
//      case HELD => updateHeldSubmission(item, ctReg, ctReg.registrationID)
//      case SUBMITTED => updateSubmittedSubmission(ctReg)
//      case unknown => updateOtherSubmission(ctReg.registrationID, item.transactionId, unknown)
//    }
//  }
//
//  private[services] def updateHeldSubmission(item: IncorpUpdate, ctReg: CorporationTaxRegistration, journeyId : String)(implicit hc : HeaderCarrier) : Future[Boolean] = {
//    getAckRef(ctReg) match {
//      case Some(ackRef) =>
//        val fResponse = for {
//          submission <- constructFullSubmission(item, ctReg, ackRef)
//          response <- postSubmissionToDes(ackRef, submission, journeyId)
//          _ <- auditSuccessfulIncorporation(item, ctReg)
//        } yield {
//          (response, submission)
//        }
//        fResponse flatMap {
//          case (SuccessDesResponse(response), auditDetail) => processSuccessDesResponse(item, ctReg, auditDetail)
//          case (InvalidDesRequest(message), _) => processInvalidDesRequest(ackRef, message)
//          case (NotFoundDesResponse, _) => processNotFoundDesResponse(ackRef)
//          case (DesErrorResponse, _) => processDesErrorResponse(ackRef)
//        }
//      case None => processMissingAckRefForTxID(item.transactionId)
//    }
//  }
//
//  private[services] def processSuccessDesResponse(item: IncorpUpdate, ctReg: CorporationTaxRegistration, auditDetail : JsObject)(implicit hc: HeaderCarrier): Future[Boolean] = {
//    for {
//      _ <- auditDesSubmission(ctReg.registrationID, auditDetail)
//      updated <- ctRepository.updateHeldToSubmitted(ctReg.registrationID, item.crn.get, formatTimestamp(now))
//      deleted <- heldRepo.removeHeldDocument(ctReg.registrationID)
//    } yield {
//      updated && deleted
//    }
//  }

//  private[services] def auditDesSubmission(rID: String, jsSubmission: JsObject)(implicit hc: HeaderCarrier) = {
//    val event = new DesSubmissionEvent(DesSubmissionAuditEventDetail(rID, jsSubmission))
//    auditConnector.sendEvent(event)
//  }

//  private[services] def auditSuccessfulIncorporation(item: IncorpUpdate, ctReg: CorporationTaxRegistration)(implicit hc: HeaderCarrier) = {
//    val event = new SuccessfulIncorporationAuditEvent(
//      SuccessfulIncorporationAuditEventDetail(
//        ctReg.registrationID,
//        item.crn.get,
//        item.incorpDate.get
//      ),
//      "successIncorpInformation",
//      "successIncorpInformation"
//    )
//    auditConnector.sendEvent(event)
//  }
//
//  private[services] def auditFailedIncorporation(item: IncorpUpdate, ctReg: CorporationTaxRegistration)(implicit hc: HeaderCarrier) = {
//    val event = new FailedIncorporationAuditEvent(
//      FailedIncorporationAuditEventDetail(
//        ctReg.registrationID,
//        item.statusDescription.getOrElse("No reason provided")
//      ),
//      "failedIncorpInformation",
//      "failedIncorpInformation"
//    )

//    auditConnector.sendEvent(event)
//  }
//
//
//  private def processInvalidDesRequest(ackRef: String, message: String) = {
//    val errMsg = s"""Submission to DES failed for ack ref ${ackRef} - Reason: "${message}"."""
//    Logger.error(errMsg)
//    Future.failed(new InvalidSubmission(errMsg))
//  }
//
//  private def processNotFoundDesResponse(ackRef: String) = {
//    val errMsg = s"""Request sent to DES for ack ref ${ackRef} not found" """
//    Logger.error(errMsg)
//    Future.failed(new InvalidSubmission(errMsg))
//  }
//
//  private def processDesErrorResponse(ackRef: String) = {
//    val errMsg = s"Submission to DES returned an error for ack ref $ackRef"
//    Logger.error(errMsg)
//    Future.failed(new DesError(errMsg))
//  }
//
//  private def processMissingAckRefForTxID(txID: String) = {
//    val errMsg = s"""Held Registration doc is missing the ack ref for tx_id "$txID"."""
//    Logger.error(errMsg)
//    Future.failed(new MissingAckRef(errMsg))
//  }
//
//  private[services] def updateSubmittedSubmission(ctReg: CorporationTaxRegistration): Future[Boolean] = {
//    heldRepo.removeHeldDocument(ctReg.registrationID)
//  }
//
//  private def updateOtherSubmission(regId: String, txId: String, status: String) = {
//    Logger.error(s"""Tried to process a submission (${regId}/${txId}) with an unexpected status of "${status}" """)
//    Future.failed(new UnexpectedStatus(status))
//  }
//
//  private def constructFullSubmission(item: IncorpUpdate, ctReg: CorporationTaxRegistration, ackRef: String): Future[JsObject] = {
//    for {
//      heldData <- fetchHeldData(ackRef)
//      dates <- calculateDates(item, ctReg.accountingDetails, ctReg.accountsPreparation)
//    } yield {
//      appendDataToSubmission(item.crn.get, dates, heldData.submission)
//    }
//  }
//
//  private def getAckRef(reg: CorporationTaxRegistration): Option[String] = {
//    reg.confirmationReferences map (_.acknowledgementReference )
//  }
//
//   TODO - II-INCORP - Probably copy as-is
//  private[services] def fetchIncorpUpdate(): Future[Seq[IncorpUpdate]] = {
//    val hc = new HeaderCarrier()
//    for {
//      timepoint <- stateDataRepository.retrieveTimePoint
//      submission <- incorporationCheckAPIConnector.checkSubmission(timepoint)(hc)
//    } yield {
//      submission.items
//    }
//  }
//
//  private[services] def fetchRegistrationByTxId(transId : String): Future[CorporationTaxRegistration] = {
//    Logger.debug(s"""Got tx_id "${transId}" """)
//    ctRepository.retrieveRegistrationByTransactionID(transId) flatMap {
//      case Some(s) => Future.successful(s)
//      case None => Future.failed(new FailedToRetrieveByTxId(transId))
//    }
//  }
//
//  private[services] def activeDate(date: AccountingDetails) = {
//    import AccountingDetails.WHEN_REGISTERED
//    (date.status, date.activeDate) match {
//      case (_, Some(givenDate))  => ActiveInFuture(asDate(givenDate))
//      case (status, _) if status == WHEN_REGISTERED => ActiveOnIncorporation
//      case _ => DoNotIntendToTrade
//    }
//  }

//  private[services] def fetchHeldData(ackRef: String) = {
//    heldRepo.retrieveSubmissionByAckRef(ackRef) flatMap {
//      case Some(held) => Future.successful(held)
//      case None => Future.failed(new FailedToRetrieveByAckRef)
//    }
//  }

//  private[services] def calculateDates(item: IncorpUpdate,
//                                       accountingDetails: Option[AccountingDetails],
//                                       accountsPreparation: Option[AccountPrepDetails]): Future[SubmissionDates] = {
//
//    accountingDetails map { details =>
//      val prepDate = accountsPreparation flatMap (_.endDate)
//      accountingService.calculateSubmissionDates(item.incorpDate.get, activeDate(details), prepDate)
//    } match {
//      case Some(dates) => Future.successful(dates)
//      case None => Future.failed(new MissingAccountingDates)
//    }
//  }
//
//  private[services] def appendDataToSubmission(crn: String, dates: SubmissionDates, partialSubmission: JsObject) : JsObject = {
//    partialSubmission deepMerge
//      Json.obj("registration" ->
//        Json.obj("corporationTax" ->
//          Json.obj(
//            "crn" -> crn,
//            "companyActiveDate" ->
//              formatDate(
//                dates.companyActiveDate),
//            "startDateOfFirstAccountingPeriod" -> formatDate(dates.startDateOfFirstAccountingPeriod),
//            "intendedAccountsPreparationDate" -> formatDate(dates.intendedAccountsPreparationDate)
//          )
//        )
//      )
//  }
//
//  private[services] def postSubmissionToDes(ackRef: String, submission: JsObject, journeyId : String) = {
//    val hc = new HeaderCarrier()
//    desConnector.ctSubmission(ackRef, submission, journeyId)(hc)
//  }
}
