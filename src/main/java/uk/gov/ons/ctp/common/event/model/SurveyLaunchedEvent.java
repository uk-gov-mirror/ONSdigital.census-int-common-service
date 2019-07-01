package uk.gov.ons.ctp.common.event.model;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = false)
public class SurveyLaunchedEvent extends GenericMessage {
  private SurveyLaunchedPayload payload = new SurveyLaunchedPayload();
}
