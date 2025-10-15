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

import play.api.mvc.{Action, AnyContent, Call, MessagesControllerComponents}
import uk.gov.hmrc.incometaxpenaltiesappealsfrontend.config.{AppConfig, ErrorHandler}
import uk.gov.hmrc.incometaxpenaltiesappealsfrontend.controllers.auth.actions.AuthActions
import uk.gov.hmrc.incometaxpenaltiesappealsfrontend.controllers.auth.models.CurrentUserRequestWithAnswers
import uk.gov.hmrc.incometaxpenaltiesappealsfrontend.forms.AppealDecisionReviewForm
import uk.gov.hmrc.incometaxpenaltiesappealsfrontend.models.{AppealDecisionReviewEnum, CheckMode, Mode, NormalMode}
import uk.gov.hmrc.incometaxpenaltiesappealsfrontend.pages.{AppealDecisionReviewPage, LateAppealPage}
import uk.gov.hmrc.incometaxpenaltiesappealsfrontend.services.UserAnswersService
import uk.gov.hmrc.incometaxpenaltiesappealsfrontend.utils.TimeMachine
import uk.gov.hmrc.incometaxpenaltiesappealsfrontend.views.html._

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}


class AppealDecisionReviewController @Inject()(appealDecisionReview: AppealDecisionReviewView,
                                               val authActions: AuthActions,
                                               userAnswersService: UserAnswersService,
                                               override val errorHandler: ErrorHandler,
                                               override val controllerComponents: MessagesControllerComponents
                                       )(implicit ec: ExecutionContext, timeMachine: TimeMachine, val appConfig: AppConfig) extends BaseUserAnswersController {

  def onPageLoad(isAgent: Boolean, mode: Mode): Action[AnyContent] = authActions.asMTDUserWithUserAnswers(isAgent) { implicit user =>
    Ok(appealDecisionReview(
      form = fillForm(AppealDecisionReviewForm.form(), AppealDecisionReviewPage),
      isAgent = user.isAgent,
      mode
    ))
  }

  def submit(isAgent: Boolean, mode: Mode): Action[AnyContent] = authActions.asMTDUserWithUserAnswers(isAgent).async { implicit user =>
    AppealDecisionReviewForm.form().bindFromRequest().fold(
      formWithErrors =>
        Future(BadRequest(appealDecisionReview(
          form = formWithErrors,
          isAgent = user.isAgent,
          mode
        ))),
      value => {
        val updatedAnswers = user.userAnswers.setAnswer(AppealDecisionReviewPage, value)
        if(value != AppealDecisionReviewEnum.yes && mode == CheckMode) {
          // If user selects "No" in check mode, we need to remove LateAppealPage answer as it's no longer relevant
          val answersWithLateAppealRemoved = updatedAnswers.removeAnswer(LateAppealPage)
          userAnswersService.updateAnswers(answersWithLateAppealRemoved).map { _ =>
            Redirect(nextPage(mode, value))
          }
        } else
        userAnswersService.updateAnswers(updatedAnswers).map { _ =>
          Redirect(nextPage(mode, value))
        }
      }
    )
  }

  private def nextPage(mode: Mode, value: AppealDecisionReviewEnum.Value)(implicit user: CurrentUserRequestWithAnswers[AnyContent]): Call =
    (value, mode) match {
      case (AppealDecisionReviewEnum.yes, _) => routes.LateAppealController.onPageLoad(isAgent = user.isAgent, is2ndStageAppeal = user.is2ndStageAppeal, mode = mode)
      case (_, _) => routes.CheckYourAnswersController.onPageLoad(user.isAgent)
    }
}
