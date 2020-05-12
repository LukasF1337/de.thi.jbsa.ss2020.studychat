package de.thi.jbsa.prototype.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.util.List;
import javax.jms.Queue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jms.core.JmsTemplate;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.thi.jbsa.prototype.model.EventEntity;
import de.thi.jbsa.prototype.model.EventName;
import de.thi.jbsa.prototype.model.cmd.PostMessageCmd;
import de.thi.jbsa.prototype.model.event.AbstractEvent;
import de.thi.jbsa.prototype.model.event.MentionEvent;
import de.thi.jbsa.prototype.model.event.MessagePostedEvent;
import de.thi.jbsa.prototype.repository.EventRepository;

@ExtendWith(MockitoExtension.class)
class MessageProcessorServiceTest {

  private final ArgumentCaptor<AbstractEvent> eventArgumentCaptor = ArgumentCaptor.forClass(AbstractEvent.class);

  private final ArgumentCaptor<EventEntity> eventEntityArgumentCaptor = ArgumentCaptor.forClass(EventEntity.class);

  private long entityId = 0;

  @Mock
  private Queue eventQueue;

  @Mock
  private EventRepository eventRepository;

  @Mock
  private JmsTemplate jmsTemplate;

  @InjectMocks
  private MessageProcessorService messageProcessorService;

  @Mock
  private ObjectMapper objectMapper;

  private long getNewEntityId() {
    return entityId++;
  }

  @Test
  void postMessage() {
    // given
    PostMessageCmd postMessageCmd = new PostMessageCmd("timmy", "Hello World!");

    // when
    messageProcessorService.postMessage(postMessageCmd);

    // then
    verify(jmsTemplate).convertAndSend(any(Queue.class), eventArgumentCaptor.capture());
    AbstractEvent extractedEvent = eventArgumentCaptor.getValue();
    assertTrue(extractedEvent instanceof MessagePostedEvent);
    assertEquals(((MessagePostedEvent) extractedEvent).getContent(), postMessageCmd.getContent());
    assertEquals("timmy", ((MessagePostedEvent) extractedEvent).getUserId());
    assertNotNull(((MessagePostedEvent) extractedEvent).getCmdUuid());
  }

  @Test
  void postMessageWithMentionCheckDb() {
    // given
    PostMessageCmd postMessageCmd = new PostMessageCmd("timmy", "Hello World @bernd!");

    // when
    messageProcessorService.postMessage(postMessageCmd);

    // then
    verify(eventRepository, times(2)).save(eventEntityArgumentCaptor.capture());
    List<EventEntity> eventsSavedInDb = eventEntityArgumentCaptor.getAllValues();
    EventEntity mentionEventEntity = eventsSavedInDb.get(0);
    EventEntity messagePostedEventEntity = eventsSavedInDb.get(1);
    assertEquals(EventName.MENTION, mentionEventEntity.getEventName());
    assertEquals(EventName.MESSAGE_POSTED, messagePostedEventEntity.getEventName());
    assertTrue(messagePostedEventEntity.getValue().contains("Hello World @bernd!"));
  }

  @Test
  void postMessageWithMentionCheckJms() {
    // given
    PostMessageCmd postMessageCmd = new PostMessageCmd("timmy", "Hello World @bernd!");

    // when
    messageProcessorService.postMessage(postMessageCmd);

    // then
    verify(jmsTemplate, times(2)).convertAndSend(any(Queue.class), eventArgumentCaptor.capture());
    List<AbstractEvent> receivedEvents = eventArgumentCaptor.getAllValues();
    MentionEvent receivedMentionEvent = (MentionEvent) receivedEvents.get(0);
    MessagePostedEvent receivedMessagePostedEvent = (MessagePostedEvent) receivedEvents.get(1);
    assertEquals("bernd", receivedMentionEvent.getMentionedUser());
    assertEquals("timmy", receivedMentionEvent.getUserId());
    // as the mentionEvent is caused by the MessagePostedEvent it mush have the CausationUuid of the MessagePostedEvent
    assertEquals(receivedMessagePostedEvent.getUuid(), receivedMentionEvent.getCausationUuid());
    assertEquals("Hello World @bernd!", receivedMessagePostedEvent.getContent());
    assertEquals("timmy", receivedMessagePostedEvent.getUserId());
    assertEquals(postMessageCmd.getUuid(), receivedMessagePostedEvent.getCmdUuid());
    assertNotNull(receivedMessagePostedEvent.getEntityId());
  }

  @Test
  void postMessageWithMentionCheckJmsAgainstDb() {
    // given
    PostMessageCmd postMessageCmd = new PostMessageCmd("timmy", "Hello World @bernd!");

    // when
    messageProcessorService.postMessage(postMessageCmd);

    // then
    verify(eventRepository, times(2)).save(eventEntityArgumentCaptor.capture());
    List<EventEntity> eventsSavedInDb = eventEntityArgumentCaptor.getAllValues();
    EventEntity mentionEventEntity = eventsSavedInDb.get(0);
    EventEntity messagePostedEventEntity = eventsSavedInDb.get(1);

    verify(jmsTemplate, times(2)).convertAndSend(any(Queue.class), eventArgumentCaptor.capture());
    List<AbstractEvent> receivedEvents = eventArgumentCaptor.getAllValues();
    MentionEvent receivedMentionEvent = (MentionEvent) receivedEvents.get(0);
    MessagePostedEvent receivedMessagePostedEvent = (MessagePostedEvent) receivedEvents.get(1);

    assertTrue(mentionEventEntity.getValue().contains(receivedMentionEvent.getUuid().toString()));
    assertTrue(messagePostedEventEntity.getValue().contains(receivedMessagePostedEvent.getUuid().toString()));
  }

  @BeforeEach
  void setUp() {
    when(eventRepository.save(any(EventEntity.class))).thenAnswer(invocation -> {
      EventEntity entityToSave = invocation.getArgument(0);
      entityToSave.setId(getNewEntityId());
      return entityToSave;
    });
    eventRepository.deleteAll();
    entityId = 0;
  }
}
