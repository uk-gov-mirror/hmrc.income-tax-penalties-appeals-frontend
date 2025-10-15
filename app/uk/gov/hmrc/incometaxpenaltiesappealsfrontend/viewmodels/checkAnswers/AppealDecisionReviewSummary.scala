/*
 * Copyright 2025 HM Revenue & Customs
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

package uk.gov.hmrc.incometaxpenaltiesappealsfrontend.viewmodels.checkAnswers

import play.api.i18n.Messages
import play.twirl.api.Html
import uk.gov.hmrc.govukfrontend.views.Aliases.{ActionItem, Text}
import uk.gov.hmrc.govukfrontend.views.viewmodels.summarylist.{Actions, SummaryListRow}
import uk.gov.hmrc.incometaxpenaltiesappealsfrontend.controllers
import uk.gov.hmrc.incometaxpenaltiesappealsfrontend.controllers.auth.models.CurrentUserRequestWithAnswers
import uk.gov.hmrc.incometaxpenaltiesappealsfrontend.models.{AppealDecisionReviewEnum, CheckMode}
import uk.gov.hmrc.incometaxpenaltiesappealsfrontend.pages.{AppealDecisionReviewPage, ReasonableExcusePage}
import uk.gov.hmrc.incometaxpenaltiesappealsfrontend.utils.DateFormatter
import uk.gov.hmrc.incometaxpenaltiesappealsfrontend.views.helpers.SummaryListRowHelper

object AppealDecisionReviewSummary extends SummaryListRowHelper with DateFormatter {

  def row(showActionLinks: Boolean = true)(implicit user: CurrentUserRequestWithAnswers[_], messages: Messages): Option[SummaryListRow] =
      AppealDecisionReviewPage.value.map { review30Days =>
        summaryListRow(
          label = messages(s"checkYourAnswers.appealDecisionReviewed.key"),
          value = Html(messages(s"common.$review30Days")),
          actions = Option.when(showActionLinks)(Actions(
            items = Seq(
              ActionItem(
                content = Text(messages("common.change")),
                href = controllers.routes.AppealDecisionReviewController.onPageLoad(isAgent = user.isAgent, CheckMode).url,
                visuallyHiddenText = Some(messages(s"checkYourAnswers.appealDecisionReviewed.change.hidden"))
              ).withId("ChangeAppealDecisionReviewed")
            )
          ))
        )
      }
}
