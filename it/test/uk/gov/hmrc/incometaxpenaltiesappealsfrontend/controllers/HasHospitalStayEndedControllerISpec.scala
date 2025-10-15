/*
 * Copyright 2024 HM Revenue & Customs
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

package uk.gov.hmrc.incometaxpenaltiesappealsfrontend.controllers

import fixtures.messages.HasHospitalStayEndedMessages
import fixtures.messages.HonestyDeclarationMessages.fakeRequestForBereavementJourney.is2ndStageAppeal
import org.jsoup.select.Elements
import org.jsoup.{Jsoup, nodes}
import org.mongodb.scala.Document
import org.scalatest.concurrent.ScalaFutures.convertScalaFuture
import play.api.http.Status.{BAD_REQUEST, OK, SEE_OTHER}
import play.api.i18n.{Lang, Messages, MessagesApi}
import play.api.libs.ws.WSResponse
import uk.gov.hmrc.hmrcfrontend.views.viewmodels.language.En
import uk.gov.hmrc.incometaxpenaltiesappealsfrontend.config.AppConfig
import uk.gov.hmrc.incometaxpenaltiesappealsfrontend.forms.HasHospitalStayEndedForm
import uk.gov.hmrc.incometaxpenaltiesappealsfrontend.models.ReasonableExcuse.{Other, UnexpectedHospital}
import uk.gov.hmrc.incometaxpenaltiesappealsfrontend.models.session.UserAnswers
import uk.gov.hmrc.incometaxpenaltiesappealsfrontend.models._
import uk.gov.hmrc.incometaxpenaltiesappealsfrontend.pages.{HasHospitalStayEndedPage, ReasonableExcusePage, WhenDidEventEndPage}
import uk.gov.hmrc.incometaxpenaltiesappealsfrontend.repositories.UserAnswersRepository
import uk.gov.hmrc.incometaxpenaltiesappealsfrontend.utils.DateFormatter.dateToString
import uk.gov.hmrc.incometaxpenaltiesappealsfrontend.utils._

import java.time.LocalDate


class HasHospitalStayEndedControllerISpec extends ControllerISpecHelper {

  lazy val userAnswersRepo: UserAnswersRepository = app.injector.instanceOf[UserAnswersRepository]
  lazy val timeMachine: TimeMachine = app.injector.instanceOf[TimeMachine]

  override val appConfig: AppConfig = app.injector.instanceOf[AppConfig]
  implicit val messagesApi: MessagesApi = app.injector.instanceOf[MessagesApi]
  implicit lazy val messages: Messages = messagesApi.preferred(Seq(Lang(En.code)))

  class Setup(isLate: Boolean = false, wasPreviouslyYes: Boolean = false) {

    userAnswersRepo.collection.deleteMany(Document()).toFuture().futureValue

    val hospitalAnswers: UserAnswers = emptyUserAnswers
      .setAnswerForKey[PenaltyData](IncomeTaxSessionKeys.penaltyData, penaltyDataLSP.copy(
        appealData = lateSubmissionAppealData.copy(
          dateCommunicationSent =
            if (isLate) timeMachine.getCurrentDate.minusDays(appConfig.lateDays + 1)
            else        timeMachine.getCurrentDate.minusDays(1)
        )
      ))
      .setAnswer(ReasonableExcusePage, UnexpectedHospital)

    if(wasPreviouslyYes) {
      userAnswersRepo.upsertUserAnswer(hospitalAnswers.setAnswer(WhenDidEventEndPage, LocalDate.now())).futureValue
    } else {
      userAnswersRepo.upsertUserAnswer(hospitalAnswers).futureValue
    }

    val reasonableExcuse: Option[ReasonableExcuse] = userAnswersRepo.getUserAnswer(testJourneyId).futureValue.flatMap(_.getAnswer(ReasonableExcusePage))
  }

  def getUrl(isAgent: Boolean, mode: Mode): String = {
    val pathStart = if (isAgent) "/agent-" else "/"
    val pathMiddle = "has-hospital-stay-ended"
    mode match {
      case CheckMode => s"${pathStart}${pathMiddle}/check"
      case NormalMode => s"${pathStart}${pathMiddle}"
    }
  }

  Seq(NormalMode, CheckMode).foreach { mode =>
    Seq(true, false).foreach { isAgent =>
      val url = getUrl(isAgent = isAgent, mode)

      s"GET $url" should {
        if(!isAgent) {
          testNavBar(url = url)(
            userAnswersRepo.upsertUserAnswer(emptyUserAnswersWithLSP.setAnswer(ReasonableExcusePage, UnexpectedHospital)).futureValue
          )
        }

        "return an OK with a view" when {
          "the page has already been answered" in new Setup() {
            stubAuthRequests(isAgent)
            userAnswersRepo.upsertUserAnswer(hospitalAnswers.setAnswer(HasHospitalStayEndedPage, true)).futureValue

            val result: WSResponse = get(url)
            result.status shouldBe OK

            val document: nodes.Document = Jsoup.parse(result.body)
            document.select(s"#${HasHospitalStayEndedForm.key}").hasAttr("checked") shouldBe true
            document.select(s"#${HasHospitalStayEndedForm.key}-2").hasAttr("checked") shouldBe false
          }

        "the page has the correct elements" in new Setup() {
            stubAuthRequests(isAgent)
            val result: WSResponse = get(url)

            val document: nodes.Document = Jsoup.parse(result.body)

            document.getServiceName.text() shouldBe "Manage your Self Assessment"
            document.title() shouldBe s"${HasHospitalStayEndedMessages.English.headingAndTitle} - Manage your Self Assessment - GOV.UK"
            document.getElementById("captionSpan").text() shouldBe HasHospitalStayEndedMessages.English.lspCaption(
              dateToString(lateSubmissionAppealData.startDate),
              dateToString(lateSubmissionAppealData.endDate)
            )
            document.getH1Elements.text() shouldBe HasHospitalStayEndedMessages.English.headingAndTitle
            document.getElementsByAttributeValue("for", s"${HasHospitalStayEndedForm.key}").text() shouldBe HasHospitalStayEndedMessages.English.yes
            document.getElementsByAttributeValue("for", s"${HasHospitalStayEndedForm.key}-2").text() shouldBe HasHospitalStayEndedMessages.English.no
            document.getSubmitButton.text() shouldBe "Continue"
          }
        }
      }

      s"POST $url" when {

        "the radio option posted is valid" when {

          "the appeal is late" should {

            "save the value to UserAnswers AND redirect to the WhenDidEventEnd page when hasHospitalStayEnded is true" in new Setup(isLate = true) {

              stubAuthRequests(isAgent)

              val result: WSResponse = post(url)(Map(HasHospitalStayEndedForm.key -> true))

              result.status shouldBe SEE_OTHER
              result.header("Location") shouldBe Some(routes.WhenDidEventEndController.onPageLoad(reasonableExcuse.getOrElse(Other), isAgent = isAgent, mode).url)

              userAnswersRepo.getUserAnswer(testJourneyId).futureValue.flatMap(_.getAnswer(HasHospitalStayEndedPage)) shouldBe Some(true)
            }

            if (mode == NormalMode) {

              "save the value to UserAnswers AND redirect to appeal review more than 30 days page when hasHospitalStayEnded is false" in new Setup(isLate = true) {

                stubAuthRequests(isAgent)

                val result: WSResponse = post(url)(Map(HasHospitalStayEndedForm.key -> false))

                result.status shouldBe SEE_OTHER
                result.header("Location") shouldBe Some(routes.AppealDecisionReviewController.onPageLoad(isAgent, NormalMode).url)

                userAnswersRepo.getUserAnswer(testJourneyId).futureValue.flatMap(_.getAnswer(HasHospitalStayEndedPage)) shouldBe Some(false)
              }
            } else {

              "save the value and remove whenEventEnded from UserAnswers AND redirect to the CYA page when hasHospitalStayEnded is false" in new Setup(isLate = true) {

                userAnswersRepo.upsertUserAnswer(hospitalAnswers).futureValue
                stubAuthRequests(isAgent)

                val result: WSResponse = post(url)(Map(HasHospitalStayEndedForm.key -> false))

                result.status shouldBe SEE_OTHER
                result.header("Location") shouldBe Some(routes.CheckYourAnswersController.onPageLoad(isAgent).url)

                userAnswersRepo.getUserAnswer(testJourneyId).futureValue.flatMap(_.getAnswer(HasHospitalStayEndedPage)) shouldBe Some(false)
                userAnswersRepo.getUserAnswer(testJourneyId).futureValue.flatMap(_.getAnswer(WhenDidEventEndPage)) shouldBe None
              }
            }
          }

          "the appeal is NOT late" should {

            if(mode == NormalMode) {
              "save the value to UserAnswers AND redirect to the WhenDidEventEndController page when hasHospitalStayEnded is true" in new Setup() {

                stubAuthRequests(isAgent)

                val result: WSResponse = post(url)(Map(HasHospitalStayEndedForm.key -> true))

                result.status shouldBe SEE_OTHER
                result.header("Location") shouldBe Some(routes.WhenDidEventEndController.onPageLoad(reasonableExcuse.getOrElse(Other), isAgent = isAgent, mode = mode).url)

                userAnswersRepo.getUserAnswer(testJourneyId).futureValue.flatMap(_.getAnswer(HasHospitalStayEndedPage)) shouldBe Some(true)
              }
            }

            "save the value to UserAnswers AND redirect to the Check Answers page when hasHospitalStayEnded is false" in new Setup() {

              stubAuthRequests(isAgent)

              val result: WSResponse = post(url)(Map(HasHospitalStayEndedForm.key -> false))

              result.status shouldBe SEE_OTHER
              result.header("Location") shouldBe Some(routes.CheckYourAnswersController.onPageLoad(isAgent).url)

              userAnswersRepo.getUserAnswer(testJourneyId).futureValue.flatMap(_.getAnswer(HasHospitalStayEndedPage)) shouldBe Some(false)
            }
          }
        }

        "the radio option is invalid" should {

          "render a bad request with the Form Error on the page with a link to the field in error" in new Setup() {

            stubAuthRequests(isAgent)

            val result: WSResponse = post(url)(Map(HasHospitalStayEndedForm.key -> ""))
            result.status shouldBe BAD_REQUEST

            val document: nodes.Document = Jsoup.parse(result.body)
            document.title() should include(HasHospitalStayEndedMessages.English.errorPrefix)
            document.select(".govuk-error-summary__title").text() shouldBe HasHospitalStayEndedMessages.English.thereIsAProblem

            val error1Link: Elements = document.select(".govuk-error-summary__list li:nth-of-type(1) a")
            error1Link.text() shouldBe HasHospitalStayEndedMessages.English.errorRequired
            error1Link.attr("href") shouldBe s"#${HasHospitalStayEndedForm.key}"
          }
        }
      }
    }
  }
}
