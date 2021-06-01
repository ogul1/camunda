/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.camunda.operate.zeebeimport;

import static io.camunda.operate.webapp.rest.VariableRestService.VARIABLE_URL;
import static org.assertj.core.api.Assertions.assertThat;
import static io.camunda.operate.webapp.rest.ProcessInstanceRestService.PROCESS_INSTANCE_URL;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import java.util.List;
import io.camunda.operate.entities.FlowNodeInstanceEntity;
import io.camunda.operate.util.OperateZeebeIntegrationTest;
import io.camunda.operate.util.ZeebeTestUtil;
import io.camunda.operate.webapp.es.reader.FlowNodeInstanceReader;
import io.camunda.operate.webapp.rest.dto.VariableDto;
import io.camunda.operate.webapp.rest.dto.VariableRequestDto;
import java.util.Random;
import org.assertj.core.api.Condition;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;

public class VariableIT extends OperateZeebeIntegrationTest {

  @Autowired
  private FlowNodeInstanceReader flowNodeInstanceReader;

  protected String getVariablesURL(Long processInstanceKey) {
    return String.format(PROCESS_INSTANCE_URL + "/%s/variables-new", processInstanceKey);
  }

  protected String getVariableURL(String variableId) {
    return VARIABLE_URL + "/" + variableId;
  }

  @Test
  public void testVariablesLoaded() throws Exception {
    // having
    String processId = "demoProcess";
    BpmnModelInstance process = Bpmn.createExecutableProcess(processId)
      .startEvent("start")
        .serviceTask("task1").zeebeJobType("task1")
        .subProcess("subProcess")
          .zeebeInput("=var1", "subprocessVarIn")
        .embeddedSubProcess()
        .startEvent()
          .serviceTask("task2").zeebeJobType("task2")
          .zeebeInput("=subprocessVarIn", "taskVarIn")
          .zeebeOutput("=taskVarOut", "varOut")
        .endEvent()
        .subProcessDone()
        .serviceTask("task3").zeebeJobType("task3")
      .endEvent()
      .done();
    deployProcess(process, processId + ".bpmn");

    //TC 1 - when process instance is started
    final Long processInstanceKey = ZeebeTestUtil.startProcessInstance(zeebeClient, processId, "{\"var1\": \"initialValue\", \"otherVar\": 123}");
    elasticsearchTestRule.processAllRecordsAndWait(flowNodeIsActiveCheck, processInstanceKey, "task1");
    elasticsearchTestRule.processAllRecordsAndWait(variableExistsCheck, processInstanceKey, "otherVar");

    //then
    List<VariableDto> variables = getVariables(processInstanceKey);
    assertThat(variables).hasSize(2);
    assertVariable(variables, "var1","\"initialValue\"");
    assertVariable(variables, "otherVar","123");
    assertThat(variables).extracting(VariableDto::getIsPreview).containsOnly(false);

    //TC2 - when subprocess and task with input mapping are activated
    completeTask(processInstanceKey, "task1", null);
    elasticsearchTestRule.processAllRecordsAndWait(flowNodeIsActiveCheck, processInstanceKey, "task2");
    elasticsearchTestRule.processAllRecordsAndWait(variableExistsCheck, processInstanceKey, "taskVarIn");


    //then
    variables = getVariables(processInstanceKey,"subProcess");
    assertThat(variables).hasSize(1);
    assertVariable(variables, "subprocessVarIn","\"initialValue\"");

    variables = getVariables(processInstanceKey,"task2");
    assertThat(variables).hasSize(1);
    assertVariable(variables, "taskVarIn","\"initialValue\"");

    //TC3 - when activity with output mapping is completed
    completeTask(processInstanceKey, "task2", "{\"taskVarOut\": \"someResult\", \"otherTaskVar\": 456}");
    elasticsearchTestRule.processAllRecordsAndWait(flowNodeIsActiveCheck, processInstanceKey, "task3");
    elasticsearchTestRule.processAllRecordsAndWait(variableExistsCheck, processInstanceKey, "otherTaskVar");

    //then
    variables = getVariables(processInstanceKey,"task2");
    assertThat(variables).hasSize(3);
    assertVariable(variables, "taskVarIn","\"initialValue\"");
    assertVariable(variables, "taskVarOut","\"someResult\"");
    assertVariable(variables, "otherTaskVar","456");
    variables = getVariables(processInstanceKey,"subProcess");
    assertThat(variables).hasSize(1);
    assertVariable(variables, "subprocessVarIn","\"initialValue\"");
    variables = getVariables(processInstanceKey);
    assertThat(variables).hasSize(3);
    assertVariable(variables, "var1","\"initialValue\"");
    assertVariable(variables, "varOut","\"someResult\"");
    assertVariable(variables, "otherVar","123");

    //TC4 - when variables are updated
    ZeebeTestUtil.updateVariables(zeebeClient, processInstanceKey, "{\"var1\": \"updatedValue\" , \"newVar\": 555 }");
    //elasticsearchTestRule.processAllEvents(2, ImportValueType.VARIABLE);
    elasticsearchTestRule.processAllRecordsAndWait(variableEqualsCheck, processInstanceKey,processInstanceKey,"var1","\"updatedValue\"");
    elasticsearchTestRule.processAllRecordsAndWait(variableEqualsCheck, processInstanceKey,processInstanceKey,"newVar","555");
    
    variables = getVariables(processInstanceKey);
    assertThat(variables).hasSize(4);
    assertVariable(variables, "var1","\"updatedValue\"");
    assertVariable(variables, "otherVar","123");
    assertVariable(variables, "varOut","\"someResult\"");
    assertVariable(variables, "newVar","555");

    //TC5 - when task is completed with new payload and process instance is finished
    completeTask(processInstanceKey, "task3", "{\"task3Completed\": true}");
    elasticsearchTestRule.processAllRecordsAndWait(variableExistsCheck, processInstanceKey, "task3Completed");

    //then
    variables = getVariables(processInstanceKey);
    assertThat(variables).hasSize(5);
    assertVariable(variables, "var1","\"updatedValue\"");
    assertVariable(variables, "otherVar","123");
    assertVariable(variables, "varOut","\"someResult\"");
    assertVariable(variables, "newVar","555");
    assertVariable(variables, "task3Completed","true");

  }

  @Test
  public void testVariablesPages() throws Exception {
    //given
    final String bpmnProcessId = "testProcess";
    StringBuffer vars = new StringBuffer("{");
    for (int i = 0; i < 10; i++) {
      if (vars.length() > 1) {
        vars.append(",\n");
      }
      vars.append("\"var").append(i).append("\": \"value_").append(i).append("\"");
    }
    vars.append("}");
    final String flowNodeId = "taskA";
    final Long processInstanceKey = tester
        .createAndDeploySimpleProcess(bpmnProcessId, flowNodeId)
        .startProcessInstance(bpmnProcessId, vars.toString())
        .waitUntil()
        .flowNodeIsActive(flowNodeId)
        .getProcessInstanceKey();

    //when requesting page 1
    List<VariableDto> variables = getVariables(processInstanceKey, new VariableRequestDto()
        .setScopeId(String.valueOf(processInstanceKey))
        .setPageSize(3));

    //then
    assertThat(variables).hasSize(3);
    assertThat(variables.get(0).getIsFirst()).isTrue();
    for (int i = 0; i < 3; i++) {
      assertVariable(variables, "var" + i, "\"value_" + i + "\"");
    }
    String[] sortValues = variables.get(2).getSortValues();

    //when requesting page 2
    variables = getVariables(processInstanceKey, new VariableRequestDto()
        .setScopeId(String.valueOf(processInstanceKey))
        .setSearchAfter(sortValues)
        .setPageSize(3));
    assertThat(variables).hasSize(3);
    assertThat(variables.get(0).getIsFirst()).isFalse();
    for (int i = 3; i < 6; i++) {
      assertVariable(variables, "var" + i, "\"value_" + i + "\"");
    }
    sortValues = variables.get(2).getSortValues();

    //when requesting page 3
    variables = getVariables(processInstanceKey, new VariableRequestDto()
        .setScopeId(String.valueOf(processInstanceKey))
        .setSearchAfter(sortValues)
        .setPageSize(5));
    assertThat(variables).hasSize(4);
    assertThat(variables.get(0).getIsFirst()).isFalse();
    for (int i = 6; i < 10; i++) {
      assertVariable(variables, "var" + i, "\"value_" + i + "\"");
    }
    sortValues = variables.get(3).getSortValues();

    //when requesting with searchBefore
    variables = getVariables(processInstanceKey, new VariableRequestDto()
        .setScopeId(String.valueOf(processInstanceKey))
        .setSearchBefore(sortValues)
        .setPageSize(5));
    assertThat(variables).hasSize(5);
    assertThat(variables.get(0).getIsFirst()).isFalse();
    for (int i = 4; i <= 8; i++) {
      assertVariable(variables, "var" + i, "\"value_" + i + "\"");
    }
  }

  @Test
  public void testVariablesPagesWithAfterBeforeOrEqual() throws Exception {
    //given
    final String bpmnProcessId = "testProcess";
    StringBuffer vars = new StringBuffer("{");
    for (int i = 0; i < 10; i++) {
      if (vars.length() > 1) {
        vars.append(",\n");
      }
      vars.append("\"var").append(i).append("\": \"value_").append(i).append("\"");
    }
    vars.append("}");
    final String flowNodeId = "taskA";
    final Long processInstanceKey = tester
        .createAndDeploySimpleProcess(bpmnProcessId, flowNodeId)
        .startProcessInstance(bpmnProcessId, vars.toString())
        .waitUntil()
        .flowNodeIsActive(flowNodeId)
        .getProcessInstanceKey();

    //when requesting page 1
    List<VariableDto> variables = getVariables(processInstanceKey, new VariableRequestDto()
        .setScopeId(String.valueOf(processInstanceKey))
        .setPageSize(3));

    //then
    assertThat(variables).hasSize(3);
    assertThat(variables.get(0).getIsFirst()).isTrue();
    for (int i = 0; i < 3; i++) {
      assertVariable(variables, "var" + i, "\"value_" + i + "\"");
    }
    String[] sortValues = variables.get(0).getSortValues();

    //when requesting page 1 once again with searchAfterOrEqual
    variables = getVariables(processInstanceKey, new VariableRequestDto()
        .setScopeId(String.valueOf(processInstanceKey))
        .setSearchAfterOrEqual(sortValues)
        .setPageSize(3));
    assertThat(variables).hasSize(3);
    assertThat(variables.get(0).getIsFirst()).isTrue();
    for (int i = 0; i < 3; i++) {
      assertVariable(variables, "var" + i, "\"value_" + i + "\"");
    }
    sortValues = variables.get(2).getSortValues();

    //when requesting page 2
    variables = getVariables(processInstanceKey, new VariableRequestDto()
        .setScopeId(String.valueOf(processInstanceKey))
        .setSearchAfter(sortValues)
        .setPageSize(3));
    assertThat(variables).hasSize(3);
    assertThat(variables.get(0).getIsFirst()).isFalse();
    for (int i = 3; i < 6; i++) {
      assertVariable(variables, "var" + i, "\"value_" + i + "\"");
    }
    sortValues = variables.get(0).getSortValues();

    //when requesting page 2 once again with searchAfterOrEqual
    variables = getVariables(processInstanceKey, new VariableRequestDto()
        .setScopeId(String.valueOf(processInstanceKey))
        .setSearchAfterOrEqual(sortValues)
        .setPageSize(3));
    assertThat(variables).hasSize(3);
    assertThat(variables.get(0).getIsFirst()).isFalse();
    for (int i = 3; i < 6; i++) {
      assertVariable(variables, "var" + i, "\"value_" + i + "\"");
    }
    sortValues = variables.get(2).getSortValues();

    //when requesting page 2 once again with searchBeforeOrEqual
    variables = getVariables(processInstanceKey, new VariableRequestDto()
        .setScopeId(String.valueOf(processInstanceKey))
        .setSearchBeforeOrEqual(sortValues)
        .setPageSize(3));
    assertThat(variables).hasSize(3);
    assertThat(variables.get(0).getIsFirst()).isFalse();
    for (int i = 3; i < 6; i++) {
      assertVariable(variables, "var" + i, "\"value_" + i + "\"");
    }
  }

  @Test
  public void testBigVariablesWithPreview() throws Exception {
    // given
    final int size = operateProperties.getImporter().getVariableSizeThreshold();
    final String varSuffix = "9999999999";
    final String bpmnProcessId = "testProcess";
    String vars = createBigVarsWithSuffix(size, varSuffix);
    final String flowNodeId = "taskA";
    final Long processInstanceKey =
        tester
            .createAndDeploySimpleProcess(bpmnProcessId, flowNodeId)
            .startProcessInstance(bpmnProcessId, vars)
            .waitUntil()
            .flowNodeIsActive(flowNodeId)
            .getProcessInstanceKey();

    // when requesting list of variables
    List<VariableDto> variables =
        getVariables(
            processInstanceKey,
            new VariableRequestDto()
                .setScopeId(String.valueOf(processInstanceKey))
                .setPageSize(10));

    //then "value" contains truncated value
    Condition<String> suffix = new Condition<>(s -> s.contains(varSuffix), "contains");
    Condition<String> length = new Condition<>(s -> s.length() == size, "length");
    assertThat(variables).extracting(VariableDto::getValue).areNot(suffix).are(length);
    assertThat(variables).extracting(VariableDto::getIsPreview).containsOnly(true);
    String variableId = variables.get(0).getId();

    //when requesting one variable
    final VariableDto variable = getOneVariable(variableId);

    //then
    assertThat(variable.getValue()).contains(varSuffix);
    assertThat(variable.getIsPreview()).isFalse();
  }

  private String createBigVarsWithSuffix(final int size, final String varSuffix) {
    StringBuffer vars = new StringBuffer("{");
    for (int i = 0; i < 3; i++) {
      if (vars.length() > 1) {
        vars.append(",\n");
      }
      vars.append("\"var")
          .append(i)
          .append("\": \"")
          .append(createBigVariable(size) + varSuffix)
          .append("\"");
    }
    vars.append("}");
    return vars.toString();
  }

  @Test
  public void testBigVariablesWithActiveOperations() throws Exception {
    // given
    final int size = operateProperties.getImporter().getVariableSizeThreshold();
    final String varSuffix = "9999999999";
    final String bpmnProcessId = "testProcess";
    String vars = createBigVarsWithSuffix(size, varSuffix);
    final String flowNodeId = "taskA";
    final Long processInstanceKey =
        tester
            .createAndDeploySimpleProcess(bpmnProcessId, flowNodeId)
            .startProcessInstance(bpmnProcessId, vars)
            .waitUntil()
            .flowNodeIsActive(flowNodeId)
            .getProcessInstanceKey();

    //when
    //we call UPDATE_VARIABLE operation on instance
    final String varName = "var1";
    final String newVarValue = createBigVariable(size);
    postUpdateVariableOperation(processInstanceKey, varName, newVarValue + varSuffix);
    elasticsearchTestRule.refreshOperateESIndices();

    //then variable with new value is returned
    List<VariableDto> variables = getVariables(processInstanceKey);

    //then "value" contains truncated value
    assertThat(variables)
        .filteredOn(v -> v.getName().equals(varName))
        .extracting(VariableDto::getValue)
        .containsOnly(newVarValue);
  }

  private String createBigVariable(int size) {
    Random random = new Random();
    StringBuffer sb = new StringBuffer();
    for (int i = 0; i < size; i++) {
      sb.append(random.nextInt(9));
    }
    return sb.toString();
  }

  @Test
  public void testVariablesRequestFailOnEmptyScopeId() throws Exception {
    MvcResult mvcResult = mockMvc.perform(post(getVariablesURL(0L))
        .content(mockMvcTestRule.json(new VariableRequestDto()))
        .contentType(mockMvcTestRule.getContentType()))
      .andExpect(status().isBadRequest())
      .andReturn();

    assertThat(mvcResult.getResolvedException().getMessage()).isEqualTo("ScopeId must be specifies in the request.");
  }

  private void assertVariable(List<VariableDto> variables, String name, String value) {
    assertThat(variables).filteredOn(v -> v.getName().equals(name)).hasSize(1)
      .allMatch(v -> v.getValue().equals(value));
  }

  protected List<VariableDto> getVariables(Long processInstanceKey) throws Exception {
    MvcResult mvcResult = mockMvc
      .perform(post(getVariablesURL(processInstanceKey))
      .content(mockMvcTestRule.json(new VariableRequestDto()
          .setScopeId(String.valueOf(processInstanceKey))))
          .contentType(mockMvcTestRule.getContentType()))
      .andExpect(status().isOk())
      .andExpect(content().contentType(mockMvcTestRule.getContentType()))
      .andReturn();
    return mockMvcTestRule.listFromResponse(mvcResult, VariableDto.class);
  }

  protected List<VariableDto> getVariables(Long processInstanceKey, String activityId) throws Exception {
    final List<FlowNodeInstanceEntity> allActivityInstances = tester.getAllFlowNodeInstances(processInstanceKey);
    final Long task1Id = findActivityInstanceId(allActivityInstances, activityId);
    MvcResult mvcResult = mockMvc
      .perform(post(getVariablesURL(processInstanceKey))
        .content(mockMvcTestRule.json(new VariableRequestDto()
            .setScopeId(String.valueOf(task1Id))))
        .contentType(mockMvcTestRule.getContentType()))
      .andExpect(status().isOk())
      .andExpect(content().contentType(mockMvcTestRule.getContentType()))
      .andReturn();
    return mockMvcTestRule.listFromResponse(mvcResult, VariableDto.class);
  }

  protected List<VariableDto> getVariables(Long processInstanceKey, VariableRequestDto request) throws Exception {
    MvcResult mvcResult = mockMvc
      .perform(post(getVariablesURL(processInstanceKey))
        .content(mockMvcTestRule.json(request))
        .contentType(mockMvcTestRule.getContentType()))
      .andExpect(status().isOk())
      .andExpect(content().contentType(mockMvcTestRule.getContentType()))
      .andReturn();
    return mockMvcTestRule.listFromResponse(mvcResult, VariableDto.class);
  }

  protected VariableDto getOneVariable(String variableId) throws Exception {
    MvcResult mvcResult = mockMvc
      .perform(get(getVariableURL(variableId)))
      .andExpect(status().isOk())
      .andExpect(content().contentType(mockMvcTestRule.getContentType()))
      .andReturn();
    return mockMvcTestRule.fromResponse(mvcResult, VariableDto.class);
  }

  protected Long findActivityInstanceId(List<FlowNodeInstanceEntity> allActivityInstances, String activityId) {
    assertThat(allActivityInstances).filteredOn(ai -> ai.getFlowNodeId().equals(activityId)).hasSize(1);
    return Long.valueOf(allActivityInstances.stream().filter(ai -> ai.getFlowNodeId().equals(activityId)).findFirst().get().getId());
  }

}
