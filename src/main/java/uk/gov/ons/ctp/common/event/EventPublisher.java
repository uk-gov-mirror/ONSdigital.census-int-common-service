package uk.gov.ons.ctp.common.event;

import java.lang.reflect.Field;
import java.util.Date;
import java.util.UUID;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.util.ReflectionUtils;
import com.godaddy.logging.Logger;
import com.godaddy.logging.LoggerFactory;
import lombok.Getter;
import uk.gov.ons.ctp.common.error.CTPException;
import uk.gov.ons.ctp.common.error.CTPException.Fault;
import uk.gov.ons.ctp.common.event.model.EventPayload;
import uk.gov.ons.ctp.common.event.model.FulfilmentPayload;
import uk.gov.ons.ctp.common.event.model.FulfilmentRequest;
import uk.gov.ons.ctp.common.event.model.FulfilmentRequestedEvent;
import uk.gov.ons.ctp.common.event.model.GenericMessage;
import uk.gov.ons.ctp.common.event.model.GenericPayload;
import uk.gov.ons.ctp.common.event.model.Header;
import uk.gov.ons.ctp.common.event.model.RespondentAuthenticatedEvent;
import uk.gov.ons.ctp.common.event.model.RespondentAuthenticatedPayload;
import uk.gov.ons.ctp.common.event.model.RespondentAuthenticatedResponse;
import uk.gov.ons.ctp.common.event.model.RespondentRefusalDetails;
import uk.gov.ons.ctp.common.event.model.RespondentRefusalEvent;
import uk.gov.ons.ctp.common.event.model.RespondentRefusalPayload;
import uk.gov.ons.ctp.common.event.model.SurveyLaunchedEvent;
import uk.gov.ons.ctp.common.event.model.SurveyLaunchedPayload;
import uk.gov.ons.ctp.common.event.model.SurveyLaunchedResponse;

/** Service responsible for the publication of events. */
public class EventPublisher {

  private static final Logger log = LoggerFactory.getLogger(EventPublisher.class);

  private RabbitTemplate template;

  @Getter
  public enum EventType {
    SURVEY_LAUNCHED("RESPONDENT_HOME", "RH", SurveyLaunchedEvent.class),
    RESPONDENT_AUTHENTICATED("RESPONDENT_HOME", "RH", RespondentAuthenticatedEvent.class),
    FULFILMENT_REQUESTED("CONTACT_CENTRE_API", "CC", FulfilmentRequestedEvent.class),
    REFUSAL_RECEIVED("CONTACT_CENTRE_API", "CC", RespondentRefusalEvent.class);

    private String source;
    private String channel;
    private Class<?> messageClass;

    EventType(String source, String channel, Class<?> messageClass) {
      this.source = source;
      this.channel = channel;
      this.messageClass = messageClass;
    }
  }

  /**
   * Constructor taking publishing helper class
   *
   * @param template Helper class for asynchronous publishing
   */
  public EventPublisher(RabbitTemplate template) {
    this.template = template;
  }

  /**
   * Method to publish a respondent Event.
   *
   * @param routingKey message routing key for event
   * @param payload message payload for event
   * @return String UUID transaction Id for event
   * @throws CTPException if a failure was detected.
   */
  public String sendEvent(String routingKey, EventPayload payload) throws CTPException {

    EventType eventType;
    GenericPayload genericPayload;
    
    if (payload instanceof SurveyLaunchedResponse) {
      eventType = EventType.SURVEY_LAUNCHED;
      genericPayload = new SurveyLaunchedPayload((SurveyLaunchedResponse) payload);

    } else if (payload instanceof RespondentAuthenticatedResponse) {
      eventType = EventType.RESPONDENT_AUTHENTICATED;
      genericPayload = new RespondentAuthenticatedPayload((RespondentAuthenticatedResponse) payload);

    } else if (payload instanceof FulfilmentRequest) {
      eventType = EventType.FULFILMENT_REQUESTED;
      genericPayload = new FulfilmentPayload((FulfilmentRequest) payload);

    } else if (payload instanceof RespondentRefusalDetails) {
      eventType = EventType.REFUSAL_RECEIVED;
      genericPayload = new RespondentRefusalPayload((RespondentRefusalDetails) payload);

    } else {
      log.error(payload.getClass().getName() + " not supported");
      throw new CTPException(
          CTPException.Fault.SYSTEM_ERROR, payload.getClass().getName() + " not supported");
    }

    GenericMessage message = createMessage(eventType, genericPayload);
    
    template.convertAndSend(routingKey, message);
    return message.getEvent().getTransactionId();
  }

  private GenericMessage createMessage(EventType eventType, GenericPayload genericPayload) throws CTPException {
    GenericMessage message = null;
    try {
      message = (GenericMessage) eventType.getMessageClass().getConstructor().newInstance();
    } catch (Exception e) {   
      e.printStackTrace();
    }
    
    Header header = buildHeader(eventType);
    message.setEvent(header);
    
    setPayload(message, genericPayload);
    
    return message;
  }

  private static Header buildHeader(EventType type) {
    return Header.builder()
        .type(type.toString())
        .source(type.getSource())
        .channel(type.getChannel())
        .dateTime(new Date())
        .transactionId(UUID.randomUUID().toString())
        .build();
  }
  
  private void setPayload(GenericMessage message, GenericPayload genericPayload) throws CTPException {
    try {
      Field field = ReflectionUtils.findField(message.getClass(), "payload");
      ReflectionUtils.makeAccessible(field);
      ReflectionUtils.setField(field, message, genericPayload);
    } catch (Exception e) {
      String errorMessage = "Failed to set payload for message of type: " + message.getClass();
      log.error(e, errorMessage);
      throw new CTPException(Fault.SYSTEM_ERROR, e, errorMessage);
    }
  }
}
