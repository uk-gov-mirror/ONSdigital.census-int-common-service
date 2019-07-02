package uk.gov.ons.ctp.common.event.model;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = false)
public class AddressNotValidEvent extends GenericMessage {
  private AddressNotValidPayload payload = new AddressNotValidPayload();
}
