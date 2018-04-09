package org.camunda.optimize.service.engine.importing.service.mediator;

import org.camunda.optimize.dto.engine.HistoricVariableInstanceDto;
import org.camunda.optimize.plugin.ImportAdapterProvider;
import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.service.engine.importing.diff.MissingEntitiesFinder;
import org.camunda.optimize.service.engine.importing.fetcher.instance.VariableInstanceFetcher;
import org.camunda.optimize.service.engine.importing.index.handler.impl.VariableInstanceImportIndexHandler;
import org.camunda.optimize.service.engine.importing.index.page.IdSetBasedImportPage;
import org.camunda.optimize.service.engine.importing.service.VariableInstanceImportService;
import org.camunda.optimize.service.es.writer.VariableWriter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.List;
import java.util.Optional;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class VariableInstanceEngineImportMediator
    extends BackoffImportMediator<VariableInstanceImportIndexHandler> {

  private VariableInstanceFetcher engineEntityFetcher;
  private VariableInstanceImportService variableInstanceImportService;

  @Autowired
  private VariableWriter variableWriter;
  @Autowired
  private ImportAdapterProvider importAdapterProvider;

  protected EngineContext engineContext;

  public VariableInstanceEngineImportMediator(EngineContext engineContext) {
    this.engineContext = engineContext;
  }

  @PostConstruct
  public void init() {
    importIndexHandler = provider.getVariableInstanceImportIndexHandler(engineContext.getEngineAlias());
    engineEntityFetcher = beanHelper.getInstance(VariableInstanceFetcher.class, engineContext);
    MissingEntitiesFinder<HistoricVariableInstanceDto> missingEntitiesFinder =
      new MissingEntitiesFinder<>(
        configurationService,
        esClient,
        configurationService.getVariableType()
      );
    variableInstanceImportService = new VariableInstanceImportService(
      variableWriter,
      importAdapterProvider,
      elasticsearchImportJobExecutor,
      missingEntitiesFinder,
      engineContext
    );
  }

  @Override
  protected boolean importNextEnginePage() {
    Optional<IdSetBasedImportPage> page = importIndexHandler.getNextPage();
    if (page.isPresent() && !page.get().getIds().isEmpty()) {
      List<HistoricVariableInstanceDto> entities =  engineEntityFetcher.fetchEngineEntities(page.get());
      if (!entities.isEmpty()) {
        variableInstanceImportService.executeImport(entities);
        return true;
      }
    }
    return false;
  }

  @Override
  public boolean hasNewPage() {
    return importIndexHandler.hasNewPage();
  }

}
