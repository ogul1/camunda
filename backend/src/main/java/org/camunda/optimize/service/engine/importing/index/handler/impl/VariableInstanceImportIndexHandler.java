package org.camunda.optimize.service.engine.importing.index.handler.impl;

import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.service.engine.importing.index.handler.ScrollBasedImportIndexHandler;
import org.camunda.optimize.service.util.EsHelper;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.indices.TermsLookup;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.sort.SortBuilders;
import org.elasticsearch.search.sort.SortOrder;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;

import static org.camunda.optimize.service.es.schema.type.ProcessInstanceType.BOOLEAN_VARIABLES;
import static org.camunda.optimize.service.es.schema.type.ProcessInstanceType.DATE_VARIABLES;
import static org.camunda.optimize.service.es.schema.type.ProcessInstanceType.DOUBLE_VARIABLES;
import static org.camunda.optimize.service.es.schema.type.ProcessInstanceType.ENGINE;
import static org.camunda.optimize.service.es.schema.type.ProcessInstanceType.INTEGER_VARIABLES;
import static org.camunda.optimize.service.es.schema.type.ProcessInstanceType.LONG_VARIABLES;
import static org.camunda.optimize.service.es.schema.type.ProcessInstanceType.PROCESS_DEFINITION_ID;
import static org.camunda.optimize.service.es.schema.type.ProcessInstanceType.PROCESS_INSTANCE_ID;
import static org.camunda.optimize.service.es.schema.type.ProcessInstanceType.SHORT_VARIABLES;
import static org.camunda.optimize.service.es.schema.type.ProcessInstanceType.START_DATE;
import static org.camunda.optimize.service.es.schema.type.ProcessInstanceType.STRING_VARIABLES;
import static org.camunda.optimize.service.es.schema.type.VariableProcessInstanceTrackingType.PROCESS_INSTANCE_IDS;
import static org.camunda.optimize.service.es.schema.type.VariableProcessInstanceTrackingType.VARIABLE_PROCESS_INSTANCE_TRACKING_TYPE;
import static org.elasticsearch.index.query.QueryBuilders.existsQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.elasticsearch.index.query.QueryBuilders.termsLookupQuery;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class VariableInstanceImportIndexHandler extends ScrollBasedImportIndexHandler {

  public VariableInstanceImportIndexHandler(EngineContext engineContext) {
    this.engineContext = engineContext;
  }


  @Override
  public long fetchMaxEntityCount() {
    // here the import index is based on process instances and therefore
    // we need to fetch the maximum number of process instances
    performRefresh();

    SearchResponse response;
    if (scrollId != null) {
      response = esclient
        .prepareSearchScroll(scrollId)
        .setScroll(new TimeValue(configurationService.getElasticsearchScrollTimeout()))
        .get();
    } else {
      response = esclient
        .prepareSearch(configurationService.getOptimizeIndex(configurationService.getProcessInstanceType()))
        .setTypes(configurationService.getProcessInstanceType())
        .setQuery(buildBasicQuery())
        .setSize(0) // Don't return any documents, we don't need them.
        .get();
    }
    return response.getHits().getTotalHits();
  }

  protected Set<String> performScrollQuery() {
    logger.debug("Performing scroll search query!");
    Set<String> result = new HashSet<>();
    SearchResponse scrollResp =
      esclient
        .prepareSearchScroll(scrollId)
        .setScroll(new TimeValue(configurationService.getElasticsearchScrollTimeout()))
        .get();

    logger.debug("Scroll search query got [{}] results", scrollResp.getHits().getHits().length);

    for (SearchHit hit : scrollResp.getHits().getHits()) {
      result.add(hit.getId());
    }
    scrollId = scrollResp.getScrollId();
    return result;
  }

  protected Set<String> performInitialSearchQuery() {
    logger.debug("Performing initial search query!");
    performRefresh();
    Set<String> result = new HashSet<>();
    QueryBuilder query;
    query = buildBasicQuery();
    SearchResponse scrollResp = esclient
        .prepareSearch(configurationService.getOptimizeIndex(configurationService.getProcessInstanceType()))
        .setTypes(configurationService.getProcessInstanceType())
        .setScroll(new TimeValue(configurationService.getElasticsearchScrollTimeout()))
        .setQuery(query)
        .setFetchSource(false)
        .setSize(configurationService.getEngineImportVariableInstanceMaxPageSize())
        .addSort(SortBuilders.fieldSort("startDate").order(SortOrder.ASC))
        .get();

    logger.debug("Initial search query got [{}] results", scrollResp.getHits().getHits().length);

    for (SearchHit hit : scrollResp.getHits().getHits()) {
      result.add(hit.getId());
    }
    scrollId = scrollResp.getScrollId();
    return result;
  }

  private void performRefresh() {
    esclient
      .admin()
      .indices()
      .prepareRefresh(configurationService.getOptimizeIndex(configurationService.getProcessInstanceType()))
      .get();
  }

  private QueryBuilder buildBasicQuery() {
    TermsLookup termsLookup = new TermsLookup(
      configurationService.getOptimizeIndex(VARIABLE_PROCESS_INSTANCE_TRACKING_TYPE),
      VARIABLE_PROCESS_INSTANCE_TRACKING_TYPE,
      EsHelper.constructKey(VARIABLE_PROCESS_INSTANCE_TRACKING_TYPE, engineContext.getEngineAlias()),
      PROCESS_INSTANCE_IDS);
    BoolQueryBuilder query = QueryBuilders.boolQuery();
    query
      .must(termQuery(ENGINE, engineContext.getEngineAlias()))
      .must(existsQuery(START_DATE))
      .mustNot(termsLookupQuery(PROCESS_INSTANCE_ID, termsLookup))
      .mustNot(existsQuery(BOOLEAN_VARIABLES))
      .mustNot(existsQuery(DOUBLE_VARIABLES))
      .mustNot(existsQuery(STRING_VARIABLES))
      .mustNot(existsQuery(SHORT_VARIABLES))
      .mustNot(existsQuery(LONG_VARIABLES))
      .mustNot(existsQuery(DATE_VARIABLES))
      .mustNot(existsQuery(INTEGER_VARIABLES));
    if (configurationService.areProcessDefinitionsToImportDefined()) {
      for (String processDefinitionId : configurationService.getProcessDefinitionIdsToImport()) {
        query
          .should(termQuery(PROCESS_DEFINITION_ID, processDefinitionId));
      }
    }
    return query;
  }
  @Override
  protected String getElasticsearchTrackingType() {
    return VARIABLE_PROCESS_INSTANCE_TRACKING_TYPE;
  }
}
