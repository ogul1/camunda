package org.camunda.optimize.service.engine.importing.service;

import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.instance.FlowNode;
import org.camunda.optimize.dto.engine.ProcessDefinitionXmlEngineDto;
import org.camunda.optimize.dto.optimize.importing.ProcessDefinitionXmlOptimizeDto;
import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.service.engine.importing.diff.MissingEntitiesFinder;
import org.camunda.optimize.service.es.ElasticsearchImportJobExecutor;
import org.camunda.optimize.service.es.job.ElasticsearchImportJob;
import org.camunda.optimize.service.es.job.importing.ProcessDefinitionXmlElasticsearchImportJob;
import org.camunda.optimize.service.es.writer.ProcessDefinitionXmlWriter;

import java.io.ByteArrayInputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ProcessDefinitionXmlImportService extends
  ImportService<ProcessDefinitionXmlEngineDto, ProcessDefinitionXmlOptimizeDto> {

  private ProcessDefinitionXmlWriter processDefinitionXmlWriter;

  public ProcessDefinitionXmlImportService(
      ProcessDefinitionXmlWriter processDefinitionXmlWriter,
      ElasticsearchImportJobExecutor elasticsearchImportJobExecutor,
      MissingEntitiesFinder<ProcessDefinitionXmlEngineDto> missingEntitiesFinder,
      EngineContext engineContext
  ) {
    super(elasticsearchImportJobExecutor, missingEntitiesFinder, engineContext);
    this.processDefinitionXmlWriter = processDefinitionXmlWriter;

  }

  @Override
  protected ElasticsearchImportJob<ProcessDefinitionXmlOptimizeDto>
  createElasticsearchImportJob(List<ProcessDefinitionXmlOptimizeDto> processDefinitions) {
    ProcessDefinitionXmlElasticsearchImportJob procDefImportJob = new ProcessDefinitionXmlElasticsearchImportJob(processDefinitionXmlWriter);
    procDefImportJob.setEntitiesToImport(processDefinitions);
    return procDefImportJob;
  }

  @Override
  protected ProcessDefinitionXmlOptimizeDto mapEngineEntityToOptimizeEntity(ProcessDefinitionXmlEngineDto engineEntity) {
    ProcessDefinitionXmlOptimizeDto optimizeDto = new ProcessDefinitionXmlOptimizeDto();
    optimizeDto.setBpmn20Xml(engineEntity.getBpmn20Xml());
    optimizeDto.setProcessDefinitionId(engineEntity.getId());
    optimizeDto.setFlowNodeNames(constructFlowNodeNames(engineEntity.getBpmn20Xml()));
    optimizeDto.setEngine(engineContext.getEngineAlias());
    return optimizeDto;
  }

  private Map<String, String> constructFlowNodeNames(String bpmn20Xml) {
    Map<String, String> result = new HashMap<>();
    BpmnModelInstance model = Bpmn.readModelFromStream(new ByteArrayInputStream(bpmn20Xml.getBytes()));
    for (FlowNode node : model.getModelElementsByType(FlowNode.class)) {
      result.put(node.getId(), node.getName());
    }
    return result;
  }

}
