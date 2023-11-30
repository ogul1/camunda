/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.importing.job;

import org.camunda.optimize.dto.optimize.ImportRequestDto;
import org.camunda.optimize.dto.optimize.importing.FlowNodeEventDto;
import org.camunda.optimize.service.CamundaEventImportService;
import org.camunda.optimize.service.db.writer.activity.CompletedActivityInstanceWriter;
import org.camunda.optimize.service.importing.DatabaseImportJob;
import org.camunda.optimize.service.db.es.writer.ElasticsearchWriterUtil;
import org.camunda.optimize.service.util.configuration.ConfigurationService;

import java.util.ArrayList;
import java.util.List;

public class CompletedActivityInstanceDatabaseImportJob extends DatabaseImportJob<FlowNodeEventDto> {

  private final CompletedActivityInstanceWriter completedActivityInstanceWriter;
  private final CamundaEventImportService camundaEventImportService;
  private final ConfigurationService configurationService;

  public CompletedActivityInstanceDatabaseImportJob(CompletedActivityInstanceWriter completedActivityInstanceWriter,
                                                    CamundaEventImportService camundaEventImportService,
                                                    ConfigurationService configurationService,
                                                    Runnable callback) {
    super(callback);
    this.completedActivityInstanceWriter = completedActivityInstanceWriter;
    this.camundaEventImportService = camundaEventImportService;
    this.configurationService = configurationService;
  }

  @Override
  protected void persistEntities(List<FlowNodeEventDto> newOptimizeEntities) {
    final List<ImportRequestDto> importRequests = new ArrayList<>();
    importRequests.addAll(completedActivityInstanceWriter.generateActivityInstanceImports(newOptimizeEntities));
    importRequests.addAll(camundaEventImportService.generateCompletedCamundaActivityEventsImports(newOptimizeEntities));
    //todo handle in the OPT-7228
    ElasticsearchWriterUtil.executeImportRequestsAsBulk(
      "Completed activity instances",
      importRequests,
      configurationService.getSkipDataAfterNestedDocLimitReached()
    );
  }

}
