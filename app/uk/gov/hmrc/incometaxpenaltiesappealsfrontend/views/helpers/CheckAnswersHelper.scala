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

package uk.gov.hmrc.incometaxpenaltiesappealsfrontend.views.helpers

import play.api.i18n.Messages
import uk.gov.hmrc.govukfrontend.views.viewmodels.summarylist.SummaryListRow
import uk.gov.hmrc.incometaxpenaltiesappealsfrontend.controllers.auth.models.CurrentUserRequestWithAnswers
import uk.gov.hmrc.incometaxpenaltiesappealsfrontend.models.upscan.UploadJourney
import uk.gov.hmrc.incometaxpenaltiesappealsfrontend.viewmodels.checkAnswers._

import javax.inject.Inject

class CheckAnswersHelper @Inject()(lateAppealSummary: LateAppealSummary) {

  def constructSummaryListRows(uploadedFiles: Seq[UploadJourney], showActionLinks: Boolean = true)(implicit user: CurrentUserRequestWithAnswers[_], messages: Messages): Seq[SummaryListRow] =
    Seq(
      WhoPlannedToSubmitSummary.row(showActionLinks),
      WhatCausedYouToMissDeadlineSummary.row(showActionLinks),
      JointAppealSummary.row(showActionLinks),
      ReasonableExcuseSummary.row(showActionLinks),
      WhenDidEventHappenSummary.row(showActionLinks),
      HasHospitalStayEndedSummary.row(showActionLinks),
      WhenDidEventEndSummary.row(showActionLinks),
      CrimeReportedSummary.row(showActionLinks),
      AppealDecisionReviewSummary.row(showActionLinks),
      MissedDeadlineReasonSummary.row(showActionLinks),
      lateAppealSummary.row(showActionLinks),
      ExtraEvidenceSummary.row(showActionLinks),
      UploadedDocumentsSummary.row(uploadedFiles, showActionLinks)
    ).flatten
}
