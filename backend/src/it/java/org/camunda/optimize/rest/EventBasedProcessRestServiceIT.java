/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.rest;

import lombok.NonNull;
import lombok.SneakyThrows;
import org.camunda.optimize.AbstractIT;
import org.camunda.optimize.dto.optimize.query.event.EventMappingDto;
import org.camunda.optimize.dto.optimize.query.event.EventProcessMappingDto;
import org.camunda.optimize.dto.optimize.query.event.EventProcessPublishStateDto;
import org.camunda.optimize.dto.optimize.query.event.EventProcessState;
import org.camunda.optimize.dto.optimize.query.event.EventTypeDto;
import org.camunda.optimize.dto.optimize.query.event.IndexableEventProcessPublishStateDto;
import org.camunda.optimize.dto.optimize.rest.ErrorResponseDto;
import org.camunda.optimize.exception.OptimizeIntegrationTestException;
import org.camunda.optimize.service.es.schema.index.events.EventProcessPublishStateIndex;
import org.camunda.optimize.service.security.util.LocalDateUtil;
import org.camunda.optimize.service.util.IdGenerator;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.SortBuilders;
import org.elasticsearch.search.sort.SortOrder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.core.Response;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.EVENT_PROCESS_PUBLISH_STATE_INDEX;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

public class EventBasedProcessRestServiceIT extends AbstractIT {

  private static final String FULL_PROCESS_DEFINITION_XML_PATH = "/bpmn/leadQualification.bpmn";
  private static final String VALID_SCRIPT_TASK_ID = "ScriptTask_1";
  private static final String VALID_USER_TASK_ID = "UserTask_1d75hsy";
  private static final String VALID_SERVICE_TASK_ID = "ServiceTask_0j2w5af";

  @Test
  public void createEventProcessMappingWithoutAuthorization() {
    // when
    Response response = eventProcessClient
      .createCreateEventProcessMappingRequest(
        eventProcessClient.createEventProcessMappingDto(FULL_PROCESS_DEFINITION_XML_PATH)
      )
      .withoutAuthentication()
      .execute();

    // then the status code is not authorized
    assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
  }

  @Test
  public void createEventProcessMapping() {
    // when
    Response response = eventProcessClient
      .createCreateEventProcessMappingRequest(
        eventProcessClient.createEventProcessMappingDto(FULL_PROCESS_DEFINITION_XML_PATH)
      )
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  public void createEventProcessMappingWithEventMappingCombinations() {
    // given event mappings with IDs existing in XML
    Map<String, EventMappingDto> eventMappings = new HashMap<>();
    eventMappings.put(VALID_SCRIPT_TASK_ID, createEventMappingsDto(createMappedEventDto(), createMappedEventDto()));
    eventMappings.put(VALID_USER_TASK_ID, createEventMappingsDto(createMappedEventDto(), null));
    eventMappings.put(VALID_SERVICE_TASK_ID, createEventMappingsDto(null, createMappedEventDto()));
    EventProcessMappingDto eventProcessMappingDto = eventProcessClient.createEventProcessMappingDtoWithMappings(
      eventMappings,
      "process name",
      FULL_PROCESS_DEFINITION_XML_PATH
    );

    // when
    Response response = eventProcessClient.createCreateEventProcessMappingRequest(eventProcessMappingDto).execute();

    // then
    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  public void createEventProcessMappingWithEventMappingIdNotExistInXml() {
    // given event mappings with ID does not exist in XML
    EventProcessMappingDto eventProcessMappingDto = eventProcessClient.createEventProcessMappingDtoWithMappings(
      Collections.singletonMap("invalid_Id", createEventMappingsDto(createMappedEventDto(), createMappedEventDto())),
      "process name",
      FULL_PROCESS_DEFINITION_XML_PATH
    );

    // when
    Response response = eventProcessClient.createCreateEventProcessMappingRequest(eventProcessMappingDto).execute();

    // then
    assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_BAD_REQUEST);
  }

  @Test
  public void createEventProcessMappingWithEventMappingsAndXmlNotPresent() {
    // given event mappings but no XML provided
    EventProcessMappingDto eventProcessMappingDto = eventProcessClient.createEventProcessMappingDtoWithMappings(
      Collections.singletonMap("some_task_id", createEventMappingsDto(createMappedEventDto(), createMappedEventDto())),
      "process name",
      null
    );

    // when
    Response response = eventProcessClient.createCreateEventProcessMappingRequest(eventProcessMappingDto).execute();

    // then
    assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_BAD_REQUEST);
  }

  @ParameterizedTest(name = "Invalid mapped event: {0}")
  @MethodSource("createInvalidMappedEventDtos")
  public void createEventProcessMappingWithInvalidEventMappings(EventTypeDto invalidEventTypeDto) {
    // given event mappings but mapped events have fields missing
    invalidEventTypeDto.setGroup(null);
    EventProcessMappingDto eventProcessMappingDto = eventProcessClient.createEventProcessMappingDtoWithMappings(
      Collections.singletonMap("some_task_id", createEventMappingsDto(invalidEventTypeDto, createMappedEventDto())),
      "process name",
      FULL_PROCESS_DEFINITION_XML_PATH
    );

    // when
    Response response = eventProcessClient.createCreateEventProcessMappingRequest(eventProcessMappingDto).execute();

    // then a bad request exception is thrown
    assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_BAD_REQUEST);
  }

  @Test
  public void getEventProcessMappingWithoutAuthorization() {
    // when
    Response response = eventProcessClient.createGetEventProcessMappingRequest(IdGenerator.getNextId())
      .withoutAuthentication()
      .execute();

    // then the status code is not authorized
    assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
  }

  @Test
  public void getEventProcessMappingWithId() {
    // given
    EventProcessMappingDto eventProcessMappingDto = createEventProcessMappingDtoWithSimpleMappings();
    OffsetDateTime now = OffsetDateTime.parse("2019-11-25T10:00:00+01:00");
    LocalDateUtil.setCurrentTime(now);
    String expectedId = eventProcessClient.createEventProcessMapping(eventProcessMappingDto);

    // when
    EventProcessMappingDto actual = eventProcessClient.getEventProcessMapping(expectedId);

    // then the report is returned with expect
    assertThat(actual.getId()).isEqualTo(expectedId);
    assertThat(actual).isEqualToIgnoringGivenFields(
      eventProcessMappingDto,
      EventProcessMappingDto.Fields.id,
      EventProcessMappingDto.Fields.lastModified,
      EventProcessMappingDto.Fields.lastModifier,
      EventProcessMappingDto.Fields.state
    );
    assertThat(actual.getLastModified()).isEqualTo(now);
    assertThat(actual.getLastModifier()).isEqualTo("demo");
    assertThat(actual.getState()).isEqualTo(EventProcessState.MAPPED);
  }

  @Test
  public void getEventProcessMappingWithId_unmappedState() {
    // given
    EventProcessMappingDto eventProcessMappingDto = eventProcessClient.createEventProcessMappingDtoWithMappings(
      null, "process name", FULL_PROCESS_DEFINITION_XML_PATH
    );
    String expectedId = eventProcessClient.createEventProcessMapping(eventProcessMappingDto);

    // when
    EventProcessMappingDto actual = eventProcessClient.getEventProcessMapping(expectedId);

    // then the report is returned in state unmapped
    assertThat(actual.getState()).isEqualTo(EventProcessState.UNMAPPED);
  }

  @Test
  public void getEventProcessMappingWithIdNotExists() {
    // when
    Response response = eventProcessClient
      .createGetEventProcessMappingRequest(IdGenerator.getNextId()).execute();

    // then the report is returned with expect
    assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_NOT_FOUND);
  }

  @Test
  public void getAllEventProcessMappingWithoutAuthorization() {
    // when
    Response response = embeddedOptimizeExtension
      .getRequestExecutor()
      .withoutAuthentication()
      .buildGetAllEventProcessMappingsRequests()
      .execute();

    // then the status code is not authorized
    assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
  }

  @Test
  public void getAllEventProcessMappings() {
    // given
    final Map<String, EventMappingDto> firstProcessMappings = Collections.singletonMap(
      VALID_SERVICE_TASK_ID,
      createEventMappingsDto(createMappedEventDto(), createMappedEventDto())
    );
    EventProcessMappingDto firstExpectedDto = eventProcessClient.createEventProcessMappingDtoWithMappings(
      firstProcessMappings,
      "process name",
      FULL_PROCESS_DEFINITION_XML_PATH
    );
    OffsetDateTime now = OffsetDateTime.parse("2019-11-25T10:00:00+01:00");
    LocalDateUtil.setCurrentTime(now);
    String firstExpectedId = eventProcessClient.createEventProcessMapping(firstExpectedDto);
    EventProcessMappingDto secondExpectedDto = eventProcessClient
      .createEventProcessMappingDto(FULL_PROCESS_DEFINITION_XML_PATH);
    String secondExpectedId = eventProcessClient.createEventProcessMapping(secondExpectedDto);

    // when
    List<EventProcessMappingDto> response = eventProcessClient.getAllEventProcessMappings();

    // then the response contains expected processes with xml omitted
    assertThat(response).extracting(
      EventProcessMappingDto.Fields.id, EventProcessMappingDto.Fields.name,
      EventProcessMappingDto.Fields.xml, EventProcessMappingDto.Fields.lastModified,
      EventProcessMappingDto.Fields.lastModifier, EventProcessMappingDto.Fields.mappings,
      EventProcessMappingDto.Fields.state
    )
      .containsExactlyInAnyOrder(
        tuple(
          firstExpectedId,
          firstExpectedDto.getName(),
          null,
          now,
          "demo",
          firstProcessMappings,
          EventProcessState.MAPPED
        ),
        tuple(secondExpectedId, secondExpectedDto.getName(), null, now, "demo", null, EventProcessState.UNMAPPED)
      );
  }

  @Test
  public void updateEventProcessMappingWithoutAuthorization() {
    // when
    EventProcessMappingDto updateDto =
      eventProcessClient.createEventProcessMappingDto(FULL_PROCESS_DEFINITION_XML_PATH);
    Response response = eventProcessClient
      .createUpdateEventProcessMappingRequest("doesNotMatter", updateDto)
      .withoutAuthentication()
      .execute();

    // then the status code is not authorized
    assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
  }

  @Test
  public void updateEventProcessMappingWithMappingsAdded() {
    // given
    OffsetDateTime createdTime = OffsetDateTime.parse("2019-11-24T18:00:00+01:00");
    LocalDateUtil.setCurrentTime(createdTime);
    String storedEventProcessMappingId = eventProcessClient.createEventProcessMapping(
      eventProcessClient.createEventProcessMappingDto(FULL_PROCESS_DEFINITION_XML_PATH)
    );

    // when
    EventProcessMappingDto updateDto = eventProcessClient.createEventProcessMappingDtoWithMappings(
      Collections.singletonMap(
        VALID_SERVICE_TASK_ID,
        createEventMappingsDto(createMappedEventDto(), createMappedEventDto())
      ),
      "new process name",
      FULL_PROCESS_DEFINITION_XML_PATH
    );
    OffsetDateTime updatedTime = OffsetDateTime.parse("2019-11-25T10:00:00+01:00");
    LocalDateUtil.setCurrentTime(updatedTime);
    Response response = eventProcessClient
      .createUpdateEventProcessMappingRequest(storedEventProcessMappingId, updateDto).execute();

    // then the update response code is correct
    assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_NO_CONTENT);

    // then the fields have been updated
    EventProcessMappingDto storedDto = eventProcessClient.getEventProcessMapping(storedEventProcessMappingId);
    assertThat(storedDto)
      .isEqualToIgnoringGivenFields(
        updateDto,
        EventProcessMappingDto.Fields.id,
        EventProcessMappingDto.Fields.lastModified,
        EventProcessMappingDto.Fields.lastModifier,
        EventProcessMappingDto.Fields.state
      )
      .extracting("id").isEqualTo(storedEventProcessMappingId);
    assertThat(storedDto.getLastModified()).isEqualTo(updatedTime);
    assertThat(storedDto.getLastModifier()).isEqualTo("demo");
  }

  @Test
  public void updateEventProcessMappingWithIdNotExists() {
    // when
    Response response = eventProcessClient.createUpdateEventProcessMappingRequest(
      "doesNotExist",
      eventProcessClient.createEventProcessMappingDto(FULL_PROCESS_DEFINITION_XML_PATH)
    ).execute();

    // then the report is returned with expect
    assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_NOT_FOUND);
  }

  @Test
  public void updateEventProcessMappingWithEventMappingIdNotExistInXml() {
    // given
    String storedEventProcessMappingId = eventProcessClient.createEventProcessMapping(
      eventProcessClient.createEventProcessMappingDto(FULL_PROCESS_DEFINITION_XML_PATH)
    );

    // when update event mappings with ID does not exist in XML
    EventProcessMappingDto updateDto = eventProcessClient.createEventProcessMappingDtoWithMappings(
      Collections.singletonMap("invalid_Id", createEventMappingsDto(createMappedEventDto(), createMappedEventDto())),
      "process name",
      FULL_PROCESS_DEFINITION_XML_PATH
    );
    Response response = eventProcessClient
      .createUpdateEventProcessMappingRequest(storedEventProcessMappingId, updateDto).execute();

    // then the update response code is correct
    assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_BAD_REQUEST);
  }

  @ParameterizedTest(name = "Invalid mapped event: {0}")
  @MethodSource("createInvalidMappedEventDtos")
  public void updateEventProcessMappingWithInvalidEventMappings(EventTypeDto invalidEventTypeDto) {
    // given existing event based process
    String storedEventProcessMappingId = eventProcessClient.createEventProcessMapping(
      eventProcessClient.createEventProcessMappingDto(FULL_PROCESS_DEFINITION_XML_PATH)
    );

    // when update event mappings with a mapped event with missing fields
    EventProcessMappingDto updateDto = eventProcessClient.createEventProcessMappingDtoWithMappings(
      Collections.singletonMap(
        VALID_SERVICE_TASK_ID,
        createEventMappingsDto(invalidEventTypeDto, createMappedEventDto())
      ),
      "process name",
      FULL_PROCESS_DEFINITION_XML_PATH
    );
    Response response = eventProcessClient
      .createUpdateEventProcessMappingRequest(storedEventProcessMappingId, updateDto)
      .execute();

    // then a bad request exception is thrown
    assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_BAD_REQUEST);
  }

  @Test
  public void updateEventProcessMappingWithEventMappingAndNoXmlPresent() {
    // given
    String storedEventProcessMappingId = eventProcessClient.createEventProcessMapping(
      eventProcessClient.createEventProcessMappingDto(null)
    );

    // when update event mappings and no XML present
    EventProcessMappingDto updateDto = eventProcessClient.createEventProcessMappingDtoWithMappings(
      Collections.singletonMap("some_task_id", createEventMappingsDto(createMappedEventDto(), createMappedEventDto())),
      "process name",
      null
    );
    Response response = eventProcessClient
      .createUpdateEventProcessMappingRequest(storedEventProcessMappingId, updateDto)
      .execute();

    // then the update response code is correct
    assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_BAD_REQUEST);
  }

  @Test
  public void publishMappedEventProcessMapping() {
    // given
    EventProcessMappingDto eventProcessMappingDto = createEventProcessMappingDtoWithSimpleMappings();
    String eventProcessId = eventProcessClient.createEventProcessMapping(eventProcessMappingDto);

    // when
    LocalDateUtil.setCurrentTime(OffsetDateTime.now());
    eventProcessClient.publishEventProcessMapping(eventProcessId);

    final EventProcessMappingDto storedEventProcessMapping = eventProcessClient.getEventProcessMapping(
      eventProcessId
    );

    // then
    assertThat(storedEventProcessMapping.getState()).isEqualTo(EventProcessState.PUBLISH_PENDING);
    assertThat(storedEventProcessMapping.getPublishingProgress()).isEqualTo(0.0D);

    assertThat(getEventProcessPublishStateDtoFromElasticsearch(eventProcessId)).get()
      .isEqualToIgnoringGivenFields(
        EventProcessPublishStateDto.builder()
          .processMappingId(storedEventProcessMapping.getId())
          .name(storedEventProcessMapping.getName())
          .publishDateTime(LocalDateUtil.getCurrentDateTime())
          .state(EventProcessState.PUBLISH_PENDING)
          .lastImportedEventIngestDateTime(OffsetDateTime.ofInstant(Instant.ofEpochMilli(0), ZoneId.systemDefault()))
          .publishProgress(0.0D)
          .xml(storedEventProcessMapping.getXml())
          .mappings(eventProcessMappingDto.getMappings())
          .deleted(false)
          .build(),
        EventProcessPublishStateDto.Fields.id
      );
  }

  @Test
  public void publishUnpublishedChangesEventProcessMapping() {
    // given
    EventProcessMappingDto eventProcessMappingDto = createEventProcessMappingDtoWithSimpleMappings();
    String eventProcessId = eventProcessClient.createEventProcessMapping(eventProcessMappingDto);

    // when
    eventProcessClient.publishEventProcessMapping(eventProcessId);

    final EventProcessMappingDto updateDto = eventProcessClient.createEventProcessMappingDtoWithMappings(
      eventProcessMappingDto.getMappings(),
      "new process name",
      FULL_PROCESS_DEFINITION_XML_PATH
    );
    eventProcessClient.updateEventProcessMapping(eventProcessId, updateDto);

    LocalDateUtil.setCurrentTime(OffsetDateTime.now().plusSeconds(1));
    eventProcessClient.publishEventProcessMapping(eventProcessId);

    final EventProcessMappingDto republishedEventProcessMapping = eventProcessClient.getEventProcessMapping(
      eventProcessId
    );

    // then
    assertThat(republishedEventProcessMapping.getState()).isEqualTo(EventProcessState.PUBLISH_PENDING);
    assertThat(republishedEventProcessMapping.getPublishingProgress()).isEqualTo(0.0D);

    assertThat(getEventProcessPublishStateDtoFromElasticsearch(eventProcessId)).get()
      .hasFieldOrPropertyWithValue(EventProcessPublishStateDto.Fields.xml, updateDto.getXml())
      .hasFieldOrPropertyWithValue(
        EventProcessPublishStateDto.Fields.publishDateTime,
        LocalDateUtil.getCurrentDateTime()
      );
  }

  @NonNull
  private OffsetDateTime getPublishedDateForEventProcessMappingOrFail(final String eventProcessId) {
    return getEventProcessPublishStateDtoFromElasticsearch(eventProcessId)
      .orElseThrow(() -> new OptimizeIntegrationTestException("Failed reading first publish date"))
      .getPublishDateTime();
  }

  @Test
  public void publishUnmappedEventProcessMapping_fails() {
    // given
    EventProcessMappingDto eventProcessMappingDto = eventProcessClient.createEventProcessMappingDtoWithMappings(
      null, "unmapped", FULL_PROCESS_DEFINITION_XML_PATH
    );
    String eventProcessId = eventProcessClient.createEventProcessMapping(eventProcessMappingDto);

    // when
    final ErrorResponseDto errorResponse = eventProcessClient
      .createPublishEventProcessMappingRequest(eventProcessId)
      .execute(ErrorResponseDto.class, HttpServletResponse.SC_BAD_REQUEST);

    final EventProcessMappingDto actual = eventProcessClient.getEventProcessMapping(eventProcessId);

    // then
    assertThat(errorResponse.getErrorCode()).isEqualTo("invalidEventProcessState");

    assertThat(actual.getState()).isEqualTo(EventProcessState.UNMAPPED);
    assertThat(actual.getPublishingProgress()).isEqualTo(null);

    assertThat(getEventProcessPublishStateDtoFromElasticsearch(eventProcessId)).isEmpty();
  }

  @Test
  public void publishPublishPendingEventProcessMapping_fails() {
    // given
    EventProcessMappingDto eventProcessMappingDto = createEventProcessMappingDtoWithSimpleMappings();
    String eventProcessId = eventProcessClient.createEventProcessMapping(eventProcessMappingDto);

    eventProcessClient.publishEventProcessMapping(eventProcessId);
    final OffsetDateTime firstPublishDate = getPublishedDateForEventProcessMappingOrFail(eventProcessId);

    // when
    final ErrorResponseDto errorResponse = eventProcessClient
      .createPublishEventProcessMappingRequest(eventProcessId)
      .execute(ErrorResponseDto.class, HttpServletResponse.SC_BAD_REQUEST);

    final EventProcessMappingDto actual = eventProcessClient.getEventProcessMapping(eventProcessId);

    // then
    assertThat(errorResponse.getErrorCode()).isEqualTo("invalidEventProcessState");

    assertThat(actual.getState()).isEqualTo(EventProcessState.PUBLISH_PENDING);
    assertThat(actual.getPublishingProgress()).isEqualTo(0.0D);

    assertThat(getEventProcessPublishStateDtoFromElasticsearch(eventProcessId)).get()
      .hasFieldOrPropertyWithValue(EventProcessPublishStateDto.Fields.publishDateTime, firstPublishDate);
  }

  @Test
  public void publishedEventProcessMapping_failsIfNotExists() {
    // given

    // when
    final ErrorResponseDto errorResponse = eventProcessClient
      .createPublishEventProcessMappingRequest("notExistingId")
      .execute(ErrorResponseDto.class, HttpServletResponse.SC_NOT_FOUND);

    // then
    assertThat(errorResponse.getErrorCode()).isEqualTo("notFoundError");
  }

  @Test
  public void cancelPublishPendingEventProcessMapping() {
    // given
    EventProcessMappingDto eventProcessMappingDto = createEventProcessMappingDtoWithSimpleMappings();
    String eventProcessId = eventProcessClient.createEventProcessMapping(eventProcessMappingDto);

    eventProcessClient.publishEventProcessMapping(eventProcessId);

    // when
    eventProcessClient.cancelPublishEventProcessMapping(eventProcessId);

    final EventProcessMappingDto actual = eventProcessClient.getEventProcessMapping(eventProcessId);

    // then
    assertThat(actual.getState()).isEqualTo(EventProcessState.MAPPED);
    assertThat(actual.getPublishingProgress()).isEqualTo(null);

    assertThat(getEventProcessPublishStateDtoFromElasticsearch(eventProcessId)).isEmpty();
  }

  @Test
  public void cancelPublishUnmappedEventProcessMapping_fails() {
    // given
    EventProcessMappingDto eventProcessMappingDto = eventProcessClient.createEventProcessMappingDtoWithMappings(
      null, "unmapped", FULL_PROCESS_DEFINITION_XML_PATH
    );
    String eventProcessId = eventProcessClient.createEventProcessMapping(eventProcessMappingDto);

    // when
    final ErrorResponseDto errorResponse = eventProcessClient
      .createPublishEventProcessMappingRequest(eventProcessId)
      .execute(ErrorResponseDto.class, HttpServletResponse.SC_BAD_REQUEST);

    // then
    assertThat(errorResponse.getErrorCode()).isEqualTo("invalidEventProcessState");
  }

  @Test
  public void cancelPublishMappedEventProcessMapping_fails() {
    // given
    EventProcessMappingDto eventProcessMappingDto = createEventProcessMappingDtoWithSimpleMappings();
    String eventProcessId = eventProcessClient.createEventProcessMapping(eventProcessMappingDto);

    // when
    final ErrorResponseDto errorResponse = eventProcessClient
      .createCancelPublishEventProcessMappingRequest(eventProcessId)
      .execute(ErrorResponseDto.class, HttpServletResponse.SC_BAD_REQUEST);

    // then
    assertThat(errorResponse.getErrorCode()).isEqualTo("invalidEventProcessState");
  }

  @Test
  public void cancelPublishedEventProcessMapping_failsIfNotExists() {
    // given

    // when
    final ErrorResponseDto errorResponse = eventProcessClient
      .createCancelPublishEventProcessMappingRequest("notExistingId")
      .execute(ErrorResponseDto.class, HttpServletResponse.SC_NOT_FOUND);

    // then
    assertThat(errorResponse.getErrorCode()).isEqualTo("notFoundError");
  }

  @Test
  public void deleteEventProcessMappingWithoutAuthorization() {
    // when
    Response response = eventProcessClient
      .createDeleteEventProcessMappingRequest("doesNotMatter")
      .withoutAuthentication()
      .execute();

    // then the status code is not authorized
    assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
  }

  @Test
  public void deleteEventProcessMapping() {
    // given
    String storedEventProcessMappingId = eventProcessClient.createEventProcessMapping(
      eventProcessClient.createEventProcessMappingDto(FULL_PROCESS_DEFINITION_XML_PATH)
    );

    // when
    Response response = eventProcessClient
      .createDeleteEventProcessMappingRequest(storedEventProcessMappingId).execute();

    // then the delete response code is correct
    assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_NO_CONTENT);

    // then the process no longer exists
    Response getResponse = eventProcessClient
      .createGetEventProcessMappingRequest(storedEventProcessMappingId).execute();
    assertThat(getResponse.getStatus()).isEqualTo(HttpServletResponse.SC_NOT_FOUND);
  }

  @Test
  public void deletePublishedEventProcessMapping() {
    // given
    EventProcessMappingDto eventProcessMappingDto = createEventProcessMappingDtoWithSimpleMappings();
    String eventProcessId = eventProcessClient.createEventProcessMapping(eventProcessMappingDto);

    eventProcessClient.publishEventProcessMapping(eventProcessId);

    // when
    eventProcessClient.deleteEventProcessMapping(eventProcessId);

    // then
    assertThat(getEventProcessPublishStateDtoFromElasticsearch(eventProcessId)).isEmpty();
  }

  @Test
  public void deleteEventProcessMappingNotExists() {
    // when
    Response response = eventProcessClient
      .createDeleteEventProcessMappingRequest("doesNotMatter")
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_NOT_FOUND);
  }

  @SneakyThrows
  private Optional<EventProcessPublishStateDto> getEventProcessPublishStateDtoFromElasticsearch(
    final String eventProcessMappingId) {
    final SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
      .query(
        boolQuery()
          .must(termQuery(EventProcessPublishStateIndex.PROCESS_MAPPING_ID, eventProcessMappingId))
          .must(termQuery(EventProcessPublishStateIndex.DELETED, false))
      )
      .sort(SortBuilders.fieldSort(EventProcessPublishStateIndex.PUBLISH_DATE_TIME).order(SortOrder.DESC))
      .size(1);
    final SearchResponse searchResponse = elasticSearchIntegrationTestExtension
      .getOptimizeElasticClient()
      .search(new SearchRequest(EVENT_PROCESS_PUBLISH_STATE_INDEX).source(searchSourceBuilder), RequestOptions.DEFAULT);

    EventProcessPublishStateDto result = null;
    if (searchResponse.getHits().totalHits > 0) {
      result = elasticSearchIntegrationTestExtension.getObjectMapper().readValue(
        searchResponse.getHits().getAt(0).getSourceAsString(),
        IndexableEventProcessPublishStateDto.class
      ).toEventProcessPublishStateDto();
    }

    return Optional.ofNullable(result);
  }

  private EventProcessMappingDto createEventProcessMappingDtoWithSimpleMappings() {
    return eventProcessClient.createEventProcessMappingDtoWithMappings(
      Collections.singletonMap(
        VALID_SERVICE_TASK_ID,
        createEventMappingsDto(createMappedEventDto(), createMappedEventDto())
      ),
      "process name",
      FULL_PROCESS_DEFINITION_XML_PATH
    );
  }

  private EventMappingDto createEventMappingsDto(EventTypeDto startEventDto, EventTypeDto endEventDto) {
    return EventMappingDto.builder()
      .start(startEventDto)
      .end(endEventDto)
      .build();
  }

  private static Stream<EventTypeDto> createInvalidMappedEventDtos() {
    return Stream.of(
      EventTypeDto.builder()
        .group(null)
        .source(IdGenerator.getNextId())
        .eventName(IdGenerator.getNextId())
        .build(),
      EventTypeDto.builder()
        .group(IdGenerator.getNextId())
        .source(null)
        .eventName(IdGenerator.getNextId())
        .build(),
      EventTypeDto.builder()
        .group(IdGenerator.getNextId())
        .source(IdGenerator.getNextId())
        .eventName(null)
        .build()
    );
  }

  private static EventTypeDto createMappedEventDto() {
    return EventTypeDto.builder()
      .group(IdGenerator.getNextId())
      .source(IdGenerator.getNextId())
      .eventName(IdGenerator.getNextId())
      .build();
  }

}
