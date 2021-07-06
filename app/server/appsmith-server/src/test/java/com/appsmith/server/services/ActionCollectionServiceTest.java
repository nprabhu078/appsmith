package com.appsmith.server.services;

import com.appsmith.server.acl.PolicyGenerator;
import com.appsmith.server.constants.FieldName;
import com.appsmith.server.domains.ActionCollection;
import com.appsmith.server.domains.NewAction;
import com.appsmith.server.domains.NewPage;
import com.appsmith.server.dtos.ActionCollectionDTO;
import com.appsmith.server.dtos.ActionDTO;
import com.appsmith.server.exceptions.AppsmithError;
import com.appsmith.server.exceptions.AppsmithException;
import com.appsmith.server.repositories.ActionCollectionRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.client.result.UpdateResult;
import lombok.extern.slf4j.Slf4j;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.convert.MongoConverter;
import org.springframework.test.context.junit4.SpringRunner;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.test.StepVerifier;

import javax.validation.Validator;
import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@RunWith(SpringRunner.class)
@Slf4j
public class ActionCollectionServiceTest {

    ActionCollectionService actionCollectionService;
    @MockBean
    private Scheduler scheduler;
    @MockBean
    private Validator validator;
    @MockBean
    private MongoConverter mongoConverter;
    @MockBean
    private ReactiveMongoTemplate reactiveMongoTemplate;
    @MockBean
    private AnalyticsService analyticsService;
    @MockBean
    private SessionUserService sessionUserService;
    @MockBean
    private CollectionService collectionService;
    @MockBean
    private PolicyGenerator policyGenerator;

    @MockBean
    NewPageService newPageService;

    @MockBean
    LayoutActionService layoutActionService;

    @MockBean
    ActionCollectionRepository actionCollectionRepository;

    @MockBean
    NewActionService newActionService;

    private final File mockObjects = new File("src/test/resources/test_assets/ActionCollectionServiceTest/mockObjects.json");

    @Before
    public void setUp() {
        actionCollectionService = new ActionCollectionServiceImpl(
                scheduler,
                validator,
                mongoConverter,
                reactiveMongoTemplate,
                actionCollectionRepository,
                analyticsService,
                collectionService,
                layoutActionService,
                newActionService,
                newPageService,
                policyGenerator);

        Mockito
                .when(analyticsService.sendCreateEvent(Mockito.any()))
                .thenAnswer(invocationOnMock -> Mono.justOrEmpty(invocationOnMock.getArguments()[0]));

        Mockito
                .when(analyticsService.sendUpdateEvent(Mockito.any()))
                .thenAnswer(invocationOnMock -> Mono.justOrEmpty(invocationOnMock.getArguments()[0]));

        Mockito
                .when(analyticsService.sendDeleteEvent(Mockito.any()))
                .thenAnswer(invocationOnMock -> Mono.justOrEmpty(invocationOnMock.getArguments()[0]));
    }

    @Test
    public void testCreateCollection_withId_throwsError() {
        ActionCollectionDTO actionCollectionDTO = new ActionCollectionDTO();
        actionCollectionDTO.setId("testId");
        final Mono<ActionCollectionDTO> actionCollectionDTOMono = actionCollectionService.createCollection(actionCollectionDTO);

        StepVerifier.create(actionCollectionDTOMono)
                .expectErrorMatches(throwable -> throwable instanceof AppsmithException &&
                        throwable.getMessage().equals(AppsmithError.INVALID_PARAMETER.getMessage(FieldName.ID)))
                .verify();
    }

    @Test
    public void testCreateCollection_withoutOrgPageApplicationPluginIds_throwsError() {
        ActionCollectionDTO actionCollectionDTO = new ActionCollectionDTO();
        final Mono<ActionCollectionDTO> actionCollectionDTOMono = actionCollectionService.createCollection(actionCollectionDTO);

        StepVerifier.create(actionCollectionDTOMono)
                .expectErrorMatches(throwable -> throwable instanceof AppsmithException)
                .verify();
    }

    @Test
    public void testCreateCollection_withRepeatedActionName_throwsError() throws IOException {
        ActionCollectionDTO actionCollectionDTO = new ActionCollectionDTO();
        actionCollectionDTO.setName("testCollection");
        actionCollectionDTO.setPageId("testPageId");
        actionCollectionDTO.setApplicationId("testApplicationId");
        actionCollectionDTO.setOrganizationId("testOrganizationId");
        actionCollectionDTO.setPluginId("testPluginId");

        ObjectMapper objectMapper = new ObjectMapper();
        final JsonNode jsonNode = objectMapper.readValue(mockObjects, JsonNode.class);
        final NewPage newPage = objectMapper.convertValue(jsonNode.get("newPage"), NewPage.class);
        Mockito
                .when(newPageService.findById(Mockito.any(), Mockito.any()))
                .thenReturn(Mono.just(newPage));
        Mockito
                .when(layoutActionService.isNameAllowed(Mockito.any(), Mockito.any(), Mockito.any()))
                .thenReturn(Mono.just(false));

        Mockito
                .when(actionCollectionRepository
                        .findAllActionCollectionsByNameAndPageIdsAndViewMode(
                                Mockito.any(),
                                Mockito.any(),
                                Mockito.anyBoolean(),
                                Mockito.any(),
                                Mockito.any()))
                .thenReturn(Flux.empty());

        final Mono<ActionCollectionDTO> actionCollectionDTOMono = actionCollectionService.createCollection(actionCollectionDTO);

        StepVerifier.create(actionCollectionDTOMono)
                .expectErrorMatches(throwable -> throwable instanceof AppsmithException &&
                        throwable.getMessage()
                                .equals(AppsmithError.DUPLICATE_KEY_USER_ERROR
                                        .getMessage(actionCollectionDTO.getName(), FieldName.NAME)))
                .verify();
    }

    @Test
    public void testCreateCollection_withRepeatedCollectionName_throwsError() throws IOException {
        ActionCollectionDTO actionCollectionDTO = new ActionCollectionDTO();
        actionCollectionDTO.setName("testCollection");
        actionCollectionDTO.setPageId("testPageId");
        actionCollectionDTO.setApplicationId("testApplicationId");
        actionCollectionDTO.setOrganizationId("testOrganizationId");
        actionCollectionDTO.setPluginId("testPluginId");

        ObjectMapper objectMapper = new ObjectMapper();
        final JsonNode jsonNode = objectMapper.readValue(mockObjects, JsonNode.class);
        final NewPage newPage = objectMapper.convertValue(jsonNode.get("newPage"), NewPage.class);
        final ActionCollection actionCollection = objectMapper.convertValue(jsonNode.get("actionCollectionWithAction"), ActionCollection.class);
        Mockito
                .when(newPageService.findById(Mockito.any(), Mockito.any()))
                .thenReturn(Mono.just(newPage));
        Mockito
                .when(layoutActionService.isNameAllowed(Mockito.any(), Mockito.any(), Mockito.any()))
                .thenReturn(Mono.just(true));

        Mockito
                .when(actionCollectionRepository
                        .findAllActionCollectionsByNameAndPageIdsAndViewMode(
                                Mockito.any(),
                                Mockito.any(),
                                Mockito.anyBoolean(),
                                Mockito.any(),
                                Mockito.any()))
                .thenReturn(Flux.just(actionCollection));

        final Mono<ActionCollectionDTO> actionCollectionDTOMono = actionCollectionService.createCollection(actionCollectionDTO);

        StepVerifier.create(actionCollectionDTOMono)
                .expectErrorMatches(throwable -> throwable instanceof AppsmithException &&
                        throwable.getMessage()
                                .equals(AppsmithError.DUPLICATE_KEY_USER_ERROR
                                        .getMessage(actionCollectionDTO.getName(), FieldName.NAME)))
                .verify();
    }

    @Test
    public void testCreateCollection_createActionFailure_returnsWithIncompleteCollection() throws IOException {
        ActionCollectionDTO actionCollectionDTO = new ActionCollectionDTO();
        actionCollectionDTO.setName("testCollection");
        actionCollectionDTO.setPageId("testPageId");
        actionCollectionDTO.setApplicationId("testApplicationId");
        actionCollectionDTO.setOrganizationId("testOrganizationId");
        actionCollectionDTO.setPluginId("testPluginId");

        ObjectMapper objectMapper = new ObjectMapper();
        final JsonNode jsonNode = objectMapper.readValue(mockObjects, JsonNode.class);
        final NewPage newPage = objectMapper.convertValue(jsonNode.get("newPage"), NewPage.class);

        Mockito
                .when(newPageService.findById(Mockito.any(), Mockito.any()))
                .thenReturn(Mono.just(newPage));
        Mockito
                .when(layoutActionService.isNameAllowed(Mockito.any(), Mockito.any(), Mockito.any()))
                .thenReturn(Mono.just(true));

        Mockito
                .when(actionCollectionRepository
                        .findAllActionCollectionsByNameAndPageIdsAndViewMode(
                                Mockito.any(),
                                Mockito.any(),
                                Mockito.anyBoolean(),
                                Mockito.any(),
                                Mockito.any()))
                .thenReturn(Flux.empty());

        Mockito
                .when(layoutActionService.createAction(Mockito.any()))
                .thenReturn(Mono.just(new ActionDTO()));

        Mockito
                .when(actionCollectionRepository.save(Mockito.any()))
                .thenAnswer(invocation -> {
                    final ActionCollection argument = (ActionCollection) invocation.getArguments()[0];
                    argument.setId("testActionCollectionId");
                    return Mono.just(argument);
                });

        final Mono<ActionCollectionDTO> actionCollectionDTOMono = actionCollectionService.createCollection(actionCollectionDTO);

        StepVerifier.create(actionCollectionDTOMono)
                .assertNext(actionCollectionDTO1 -> {
                    Assert.assertTrue(actionCollectionDTO1.getActions().isEmpty());
                })
                .verifyComplete();
    }

    @Test
    public void testCreateCollection_validCollection_returnsPopulatedCollection() throws IOException {
        ActionCollectionDTO actionCollectionDTO = new ActionCollectionDTO();
        actionCollectionDTO.setName("testCollection");
        actionCollectionDTO.setPageId("testPageId");
        actionCollectionDTO.setApplicationId("testApplicationId");
        actionCollectionDTO.setOrganizationId("testOrganizationId");
        actionCollectionDTO.setPluginId("testPluginId");
        ActionDTO action = new ActionDTO();
        action.setName("testAction");
        actionCollectionDTO.setActions(List.of(action));

        ObjectMapper objectMapper = new ObjectMapper();
        final JsonNode jsonNode = objectMapper.readValue(mockObjects, JsonNode.class);
        final NewPage newPage = objectMapper.convertValue(jsonNode.get("newPage"), NewPage.class);

        Mockito
                .when(newPageService.findById(Mockito.any(), Mockito.any()))
                .thenReturn(Mono.just(newPage));
        Mockito
                .when(layoutActionService.isNameAllowed(Mockito.any(), Mockito.any(), Mockito.any()))
                .thenReturn(Mono.just(true));

        Mockito
                .when(actionCollectionRepository
                        .findAllActionCollectionsByNameAndPageIdsAndViewMode(
                                Mockito.any(),
                                Mockito.any(),
                                Mockito.anyBoolean(),
                                Mockito.any(),
                                Mockito.any()))
                .thenReturn(Flux.empty());

        Mockito
                .when(layoutActionService.createAction(Mockito.any()))
                .thenAnswer(invocation -> {
                    final ActionDTO argument = (ActionDTO) invocation.getArguments()[0];
                    argument.setId("testActionId");
                    return Mono.just(argument);
                });

        Mockito
                .when(actionCollectionRepository.save(Mockito.any()))
                .thenAnswer(invocation -> {
                    final ActionCollection argument = (ActionCollection) invocation.getArguments()[0];
                    argument.setId("testActionCollectionId");
                    return Mono.just(argument);
                });

        Mockito
                .when(layoutActionService.updateAction(Mockito.any(), Mockito.any()))
                .thenAnswer(invocation -> {
                    final ActionDTO argument = (ActionDTO) invocation.getArguments()[1];
                    return Mono.just(argument);
                });

        final Mono<ActionCollectionDTO> actionCollectionDTOMono = actionCollectionService.createCollection(actionCollectionDTO);

        StepVerifier.create(actionCollectionDTOMono)
                .assertNext(actionCollectionDTO1 -> {
                    Assert.assertEquals(1, actionCollectionDTO1.getActions().size());
                    final ActionDTO actionDTO = actionCollectionDTO1.getActions().get(0);
                    Assert.assertEquals("testAction", actionDTO.getName());
                    Assert.assertEquals("testActionId", actionDTO.getId());
                    Assert.assertEquals("testCollection.testAction", actionDTO.getFullyQualifiedName());
                })
                .verifyComplete();
    }

    @Test
    public void testUpdateUnpublishedActionCollection_withoutId_throwsError() {
        ActionCollectionDTO actionCollectionDTO = new ActionCollectionDTO();
        actionCollectionDTO.setId("testId");
        final Mono<ActionCollectionDTO> actionCollectionDTOMono =
                actionCollectionService.updateUnpublishedActionCollection(null, actionCollectionDTO);

        StepVerifier.create(actionCollectionDTOMono)
                .expectErrorMatches(throwable -> throwable instanceof AppsmithException &&
                        throwable.getMessage().equals(AppsmithError.INVALID_PARAMETER.getMessage(FieldName.ID)))
                .verify();
    }

    @Test
    public void testUpdateUnpublishedActionCollection_withInvalidId_throwsError() {
        ActionCollectionDTO actionCollectionDTO = new ActionCollectionDTO();
        actionCollectionDTO.setId("testId");

        Mockito
                .when(actionCollectionRepository.findById(Mockito.anyString(), Mockito.any()))
                .thenReturn(Mono.empty());

        final Mono<ActionCollectionDTO> actionCollectionDTOMono =
                actionCollectionService.updateUnpublishedActionCollection("testId", actionCollectionDTO);

        StepVerifier.create(actionCollectionDTOMono)
                .expectErrorMatches(throwable -> throwable instanceof AppsmithException &&
                        throwable.getMessage().equals(AppsmithError.NO_RESOURCE_FOUND.getMessage(FieldName.ACTION_COLLECTION, "testId")))
                .verify();
    }

    @Test
    public void testUpdateUnpublishedActionCollection_withModifiedCollection_returnsValidCollection() throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        final JsonNode jsonNode = objectMapper.readValue(mockObjects, JsonNode.class);
        final ActionCollection actionCollection = objectMapper.convertValue(jsonNode.get("actionCollectionWithAction"), ActionCollection.class);
        final ActionCollectionDTO modifiedActionCollectionDTO = objectMapper.convertValue(jsonNode.get("actionCollectionDTOWithModifiedActions"), ActionCollectionDTO.class);
        final ActionCollection modifiedActionCollection = objectMapper.convertValue(jsonNode.get("actionCollectionAfterModifiedActions"), ActionCollection.class);
        final ActionCollectionDTO unpublishedCollection = modifiedActionCollection.getUnpublishedCollection();
        unpublishedCollection.setActionIds(Set.of("testActionId1", "testActionId3"));
        unpublishedCollection.setArchivedActionIds(Set.of("testActionId2"));

        final Instant archivedAfter = Instant.now();

        Map<String, ActionDTO> updatedActions = new HashMap<>();
        Mockito
                .when(layoutActionService.updateAction(Mockito.any(), Mockito.any()))
                .thenAnswer(invocation -> {
                    final ActionDTO argument = (ActionDTO) invocation.getArguments()[1];
                    updatedActions.put(argument.getId(), argument);
                    return Mono.just(argument);
                });

        Mockito
                .when(newActionService.deleteUnpublishedAction(Mockito.any()))
                .thenAnswer(invocation -> {
                    final ActionDTO argument = (ActionDTO) invocation.getArguments()[1];
                    return Mono.just(argument);
                });

        Mockito
                .when(reactiveMongoTemplate.updateFirst(Mockito.any(), Mockito.any(), Mockito.any(Class.class)))
                .thenReturn(Mono.just((Mockito.mock(UpdateResult.class))));

        Mockito
                .when(actionCollectionRepository.findById(Mockito.anyString(), Mockito.any()))
                .thenReturn(Mono.just(actionCollection));

        Mockito
                .when(actionCollectionRepository.findById(Mockito.anyString()))
                .thenReturn(Mono.just(modifiedActionCollection));

        Mockito
                .when(newActionService.findActionDTObyIdAndViewMode(Mockito.any(), Mockito.anyBoolean(), Mockito.any()))
                .thenAnswer(invocation -> {
                    String id = (String) invocation.getArguments()[0];
                    return Mono.just(updatedActions.get(id));
                });

        final Mono<ActionCollectionDTO> actionCollectionDTOMono =
                actionCollectionService.updateUnpublishedActionCollection("testCollectionId", modifiedActionCollectionDTO);

        StepVerifier.create(actionCollectionDTOMono)
                .assertNext(actionCollectionDTO1 -> {
                    Assert.assertEquals(2, actionCollectionDTO1.getActions().size());
                    Assert.assertEquals(1, actionCollectionDTO1.getArchivedActions().size());
                    Assert.assertTrue(
                            actionCollectionDTO1
                                    .getActions()
                                    .stream()
                                    .map(ActionDTO::getId)
                                    .collect(Collectors.toSet())
                                    .containsAll(Set.of("testActionId1", "testActionId3")));
                    Assert.assertEquals("testActionId2", actionCollectionDTO1.getArchivedActions().get(0).getId());
                    Assert.assertTrue(archivedAfter.isBefore(actionCollectionDTO1.getArchivedActions().get(0).getArchivedAt()));
                })
                .verifyComplete();
    }

    @Test
    public void testDeleteUnpublishedActionCollection_withInvalidId_throwsError() {
        Mockito
                .when(actionCollectionRepository.findById(Mockito.any(), Mockito.any()))
                .thenReturn(Mono.empty());

        final Mono<ActionCollectionDTO> actionCollectionMono =
                actionCollectionService.deleteUnpublishedActionCollection("invalidId");

        StepVerifier
                .create(actionCollectionMono)
                .expectErrorMatches(throwable -> throwable instanceof AppsmithException &&
                        throwable
                                .getMessage()
                                .equals(AppsmithError.NO_RESOURCE_FOUND.getMessage(FieldName.ACTION_COLLECTION, "invalidId")))
                .verify();
    }

    @Test
    public void testDeleteUnpublishedActionCollection_withPublishedCollectionAndNoActions_returnsActionCollectionDTO() throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        final JsonNode jsonNode = objectMapper.readValue(mockObjects, JsonNode.class);
        final ActionCollection actionCollection = objectMapper.convertValue(jsonNode.get("actionCollectionWithAction"), ActionCollection.class);
        final ActionCollectionDTO unpublishedCollection = actionCollection.getUnpublishedCollection();
        unpublishedCollection.setActions(List.of());

        Instant deletedAt = Instant.now();

        Mockito
                .when(actionCollectionRepository.findById(Mockito.any(), Mockito.any()))
                .thenReturn(Mono.just(actionCollection));

        Mockito
                .when(actionCollectionRepository.save(Mockito.any()))
                .thenAnswer(invocation -> {
                    final ActionCollection argument = (ActionCollection) invocation.getArguments()[0];
                    return Mono.just(argument);
                });

        final Mono<ActionCollectionDTO> actionCollectionDTOMono = actionCollectionService.deleteUnpublishedActionCollection("testCollectionId");

        StepVerifier
                .create(actionCollectionDTOMono)
                .assertNext(actionCollectionDTO -> {
                    Assert.assertEquals("testCollection", actionCollectionDTO.getName());
                    Assert.assertEquals(0, actionCollectionDTO.getActions().size());
                    Assert.assertTrue(deletedAt.isBefore(actionCollectionDTO.getDeletedAt()));
                })
                .verifyComplete();
    }

    @Test
    public void testDeleteUnpublishedActionCollection_withPublishedCollectionAndActions_returnsActionCollectionDTO() throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        final JsonNode jsonNode = objectMapper.readValue(mockObjects, JsonNode.class);
        final ActionCollection actionCollection = objectMapper.convertValue(jsonNode.get("actionCollectionWithAction"), ActionCollection.class);
        actionCollection.getUnpublishedCollection().setActionIds(Set.of("testActionId1", "testActionId2"));

        Instant deletedAt = Instant.now();

        Mockito
                .when(actionCollectionRepository.findById(Mockito.any(), Mockito.any()))
                .thenReturn(Mono.just(actionCollection));

        Mockito
                .when(newActionService.deleteUnpublishedAction(Mockito.any()))
                .thenReturn(Mono.just(actionCollection.getUnpublishedCollection().getActions().get(0)));

        Mockito
                .when(actionCollectionRepository.save(Mockito.any()))
                .thenAnswer(invocation -> {
                    final ActionCollection argument = (ActionCollection) invocation.getArguments()[0];
                    return Mono.just(argument);
                });

        final Mono<ActionCollectionDTO> actionCollectionDTOMono = actionCollectionService.deleteUnpublishedActionCollection("testCollectionId");

        StepVerifier
                .create(actionCollectionDTOMono)
                .assertNext(actionCollectionDTO -> {
                    Assert.assertEquals("testCollection", actionCollectionDTO.getName());
                    Assert.assertEquals(2, actionCollectionDTO.getActions().size());
                    Assert.assertTrue(deletedAt.isBefore(actionCollectionDTO.getDeletedAt()));
                })
                .verifyComplete();
    }

    @Test
    public void testDeleteUnpublishedActionCollection_withoutPublishedCollectionAndNoActions_returnsActionCollectionDTO() throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        final JsonNode jsonNode = objectMapper.readValue(mockObjects, JsonNode.class);
        final ActionCollection actionCollection = objectMapper.convertValue(jsonNode.get("actionCollectionWithAction"), ActionCollection.class);

        actionCollection.setPublishedCollection(null);

        Mockito
                .when(actionCollectionRepository.findById(Mockito.any(), Mockito.any()))
                .thenReturn(Mono.just(actionCollection));

        Mockito
                .when(actionCollectionRepository.findById(Mockito.anyString()))
                .thenReturn(Mono.just(actionCollection));

        Mockito
                .when(actionCollectionRepository.delete(Mockito.any()))
                .thenReturn(Mono.empty());

        final Mono<ActionCollectionDTO> actionCollectionDTOMono = actionCollectionService.deleteUnpublishedActionCollection("testCollectionId");

        StepVerifier
                .create(actionCollectionDTOMono)
                .assertNext(Assert::assertNotNull)
                .verifyComplete();
    }

    @Test
    public void testDeleteUnpublishedActionCollection_withoutPublishedCollectionAndWithActions_returnsActionCollectionDTO() throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        final JsonNode jsonNode = objectMapper.readValue(mockObjects, JsonNode.class);
        final ActionCollection actionCollection = objectMapper.convertValue(jsonNode.get("actionCollectionWithAction"), ActionCollection.class);
        actionCollection.getUnpublishedCollection().setActionIds(Set.of("testActionId1", "testActionId2"));
        actionCollection.setPublishedCollection(null);

        Mockito
                .when(actionCollectionRepository.findById(Mockito.any(), Mockito.any()))
                .thenReturn(Mono.just(actionCollection));

        Mockito
                .when(actionCollectionRepository.findById(Mockito.anyString()))
                .thenReturn(Mono.just(actionCollection));

        Mockito
                .when(newActionService.delete(Mockito.any()))
                .thenReturn(Mono.just(new NewAction()));

        Mockito
                .when(actionCollectionRepository.delete(Mockito.any()))
                .thenReturn(Mono.empty());

        final Mono<ActionCollectionDTO> actionCollectionDTOMono = actionCollectionService.deleteUnpublishedActionCollection("testCollectionId");

        StepVerifier
                .create(actionCollectionDTOMono)
                .assertNext(Assert::assertNotNull)
                .verifyComplete();
    }
}