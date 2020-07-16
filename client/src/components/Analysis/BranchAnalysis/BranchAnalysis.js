/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import equal from 'deep-equal';

import {BPMNDiagram, MessageBox, DocsLink} from 'components';
import {incompatibleFilters, containsSuspensionFilter, loadProcessDefinitionXml} from 'services';
import {t} from 'translation';

import DiagramBehavior from './DiagramBehavior';
import Statistics from './Statistics';
import BranchControlPanel from './BranchControlPanel';
import {loadFrequencyData} from './service';

import './BranchAnalysis.scss';

export default class BranchAnalysis extends React.Component {
  constructor(props) {
    super(props);

    this.state = {
      config: {
        processDefinitionKey: '',
        processDefinitionVersions: [],
        tenantIds: [],
        filter: [],
      },
      data: null,
      hoveredControl: null,
      hoveredNode: null,
      gateway: null,
      endEvent: null,
      optimizeVersion: 'latest',
      xml: null,
    };
  }

  render() {
    const {xml, config, hoveredControl, hoveredNode, gateway, endEvent, data} = this.state;

    return (
      <div className="BranchAnalysis">
        <BranchControlPanel
          {...config}
          hoveredControl={hoveredControl}
          hoveredNode={hoveredNode}
          onChange={this.updateConfig}
          gateway={gateway}
          endEvent={endEvent}
          updateHover={this.updateHoveredControl}
          updateSelection={this.updateSelection}
          xml={xml}
        />
        {config.filter && incompatibleFilters(config.filter) && (
          <MessageBox type="warning">{t('common.filter.incompatibleFilters')}</MessageBox>
        )}
        {config.filter && containsSuspensionFilter(config.filter) && (
          <DocsLink location="technical-guide/update/2.7-to-3.0/#suspension-filter">
            {(docsLink) => (
              <MessageBox
                type="warning"
                dangerouslySetInnerHTML={{
                  __html: t('common.filter.suspensionFilterWarning', {docsLink}),
                }}
              />
            )}
          </DocsLink>
        )}
        <div className="content">
          <div className="BranchAnalysis__diagram">
            {xml && (
              <BPMNDiagram xml={xml}>
                <DiagramBehavior
                  hoveredControl={hoveredControl}
                  hoveredNode={hoveredNode}
                  updateHover={this.updateHoveredNode}
                  updateSelection={this.updateSelection}
                  gateway={gateway}
                  endEvent={endEvent}
                  data={data}
                  setViewer={this.setViewer}
                />
              </BPMNDiagram>
            )}
          </div>
          <Statistics gateway={gateway} endEvent={endEvent} config={config} viewer={this.viewer} />
        </div>
      </div>
    );
  }

  async componentDidUpdate(_, prevState) {
    const {config} = this.state;
    const {config: prevConfig} = prevState;
    const procDefConfigured = config.processDefinitionKey && config.processDefinitionVersions;
    const procDefChanged =
      prevConfig.processDefinitionKey !== config.processDefinitionKey ||
      !equal(prevConfig.processDefinitionVersions, config.processDefinitionVersions);
    const tenantsChanged = !equal(prevConfig.tenantIds, config.tenantIds);
    const filterChanged = !equal(prevConfig.filter, config.filter);

    if (procDefConfigured && (procDefChanged || tenantsChanged || filterChanged)) {
      this.setState({
        data: await loadFrequencyData(
          config.processDefinitionKey,
          config.processDefinitionVersions,
          config.tenantIds,
          config.filter
        ),
      });
    }
  }

  setViewer = (viewer) => {
    this.viewer = viewer;
  };

  updateHoveredControl = (newField) => {
    this.setState({hoveredControl: newField});
  };

  updateHoveredNode = (newNode) => {
    this.setState({hoveredNode: newNode});
  };

  updateSelection = (type, node) => {
    this.setState({[type]: node});
  };

  updateConfig = async (updates) => {
    const newConfig = {...this.state.config, ...updates};

    const changes = {
      config: newConfig,
    };

    if (updates.processDefinitionKey && updates.processDefinitionVersions && updates.tenantIds) {
      changes.xml = await loadProcessDefinitionXml(
        updates.processDefinitionKey,
        updates.processDefinitionVersions[0],
        updates.tenantIds[0]
      );

      if (changes.xml !== this.state.xml) {
        changes.gateway = null;
        changes.endEvent = null;
      }
    } else if (
      !newConfig.processDefinitionKey ||
      !newConfig.processDefinitionVersions ||
      !newConfig.tenantIds
    ) {
      changes.xml = null;
      changes.gateway = null;
      changes.endEvent = null;
    }

    this.setState(changes);
  };
}
