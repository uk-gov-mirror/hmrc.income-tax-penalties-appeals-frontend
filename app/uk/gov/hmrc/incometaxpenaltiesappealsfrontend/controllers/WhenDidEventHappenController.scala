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

import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import uk.gov.hmrc.incometaxpenaltiesappealsfrontend.config.{AppConfig, ErrorHandler}
import uk.gov.hmrc.incometaxpenaltiesappealsfrontend.controllers.auth.actions.AuthActions
import uk.gov.hmrc.incometaxpenaltiesappealsfrontend.forms.WhenDidEventHappenForm
import uk.gov.hmrc.incometaxpenaltiesappealsfrontend.models.ReasonableExcuse.{Crime, Other, TechnicalIssues, UnexpectedHospital}
import uk.gov.hmrc.incometaxpenaltiesappealsfrontend.models.{Mode, NormalMode, ReasonableExcuse}
import uk.gov.hmrc.incometaxpenaltiesappealsfrontend.pages.WhenDidEventHappenPage
import uk.gov.hmrc.incometaxpenaltiesappealsfrontend.services.UserAnswersService
import uk.gov.hmrc.incometaxpenaltiesappealsfrontend.utils.TimeMachine
import uk.gov.hmrc.incometaxpenaltiesappealsfrontend.views.html._

import java.time.LocalDate
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}


class WhenDidEventHappenController @Inject()(whenDidEventHappen: WhenDidEventHappenView,
                                             val authActions: AuthActions,
                                             userAnswersService: UserAnswersService,
                                             override val controllerComponents: MessagesControllerComponents,
                                             override val errorHandler: ErrorHandler
                                            )(implicit ec: ExecutionContext, val appConfig: AppConfig, timeMachine: TimeMachine) extends BaseUserAnswersController {


  def onPageLoad(reasonableExcuse: ReasonableExcuse, isAgent: Boolean, mode: Mode): Action[AnyContent] = authActions.asMTDUserWithUserAnswers(isAgent).async { implicit user =>
    Future(Ok(whenDidEventHappen(
      form = fillForm(WhenDidEventHappenForm.form(reasonableExcuse), WhenDidEventHappenPage),
      reasonableExcuse = reasonableExcuse,
      isLPP = user.isLPP,
      mode = mode
    )))
  }

  def submit(reasonableExcuse: ReasonableExcuse, isAgent: Boolean, mode: Mode): Action[AnyContent] = authActions.asMTDUserWithUserAnswers(isAgent).async { implicit user =>
    WhenDidEventHappenForm.form(reasonableExcuse).bindFromRequest().fold(
      formWithErrors =>
        Future.successful(BadRequest(whenDidEventHappen(
          reasonableExcuse,
          formWithErrors,
          isLPP = user.isLPP,
          mode = mode
        ))),
      dateOfEvent => {
        val updatedAnswers = user.userAnswers.setAnswer[LocalDate](WhenDidEventHappenPage, dateOfEvent)
        userAnswersService.updateAnswers(updatedAnswers).map { _ =>

          (reasonableExcuse, mode) match {
            case (TechnicalIssues, _) =>
              Redirect(routes.WhenDidEventEndController.onPageLoad(reasonableExcuse, isAgent, mode))
            case (Crime, NormalMode) =>
              Redirect(routes.CrimeReportedController.onPageLoad(isAgent = user.isAgent, NormalMode))
            case (UnexpectedHospital, _) =>
              Redirect(routes.HasHospitalStayEndedController.onPageLoad(isAgent = user.isAgent, mode))
            case (Other, NormalMode) =>
              Redirect(routes.MissedDeadlineReasonController.onPageLoad(user.isLPP, isAgent, user.is2ndStageAppeal, NormalMode))
            case (_, NormalMode) =>
              Redirect(routes.AppealDecisionReviewController.onPageLoad(isAgent = user.isAgent, mode = mode))
            case (_, _) =>
              Redirect(routes.CheckYourAnswersController.onPageLoad(isAgent = user.isAgent))
          }
        }
      })

  }
}
