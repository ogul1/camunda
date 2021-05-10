/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {runAllEffects} from 'react';
import {shallow} from 'enzyme';

import {Input, BPMNDiagram, Button} from 'components';
import {loadProcessDefinitionXml} from 'services';

import {DefinitionEditor} from './DefinitionEditor';
import {loadVersions} from './service';

jest.mock('services', () => {
  return {
    ...jest.requireActual('services'),
    loadProcessDefinitionXml: jest.fn().mockReturnValue('<diagram XML>'),
  };
});

jest.mock('./service', () => ({
  loadVersions: jest.fn().mockReturnValue([
    {version: 3, versionTag: 'Version 3'},
    {version: 2, versionTag: 'Version 3'},
    {version: 1, versionTag: 'Version 3'},
  ]),
  loadTenants: jest.fn().mockReturnValue([
    {
      tenants: [{id: null, name: 'Not Defined'}],
    },
  ]),
}));

const props = {
  mightFail: (data, cb) => cb(data),
  type: 'process',
  definition: {
    key: 'definitionA',
    name: 'Definition A',
    displayName: 'Definition A',
    versions: ['latest'],
    tenantIds: [null],
  },
  tenantInfo: {tenants: [{id: null, name: 'Not Defined'}]},
};

it('should show available versions for the given definition', () => {
  const node = shallow(<DefinitionEditor {...props} />);
  runAllEffects();

  expect(node.find('VersionPopover').prop('versions')).toEqual(loadVersions());
});

it('should show available tenants for the given definition and versions', () => {
  const node = shallow(<DefinitionEditor {...props} />);

  expect(node.find('TenantPopover').prop('tenants')).toEqual(props.tenantInfo.tenants);
});

it('should allow users to set a display name', () => {
  const spy = jest.fn();
  const node = shallow(<DefinitionEditor {...props} onChange={spy} />);

  node.find(Input).simulate('change', {target: {value: 'new display name'}});
  node.find(Input).simulate('blur');

  expect(spy.mock.calls[0][0].displayName).toBe('new display name');
});

it('should allow changing version and tenants', () => {
  const spy = jest.fn();
  const node = shallow(<DefinitionEditor {...props} onChange={spy} />);

  node.find('VersionPopover').simulate('change', ['3', '1']);
  expect(spy.mock.calls[0][0].versions).toEqual(['3', '1']);

  node.find('TenantPopover').simulate('change', [null, 'tenantV']);
  expect(spy.mock.calls[1][0].tenantIds).toEqual([null, 'tenantV']);
});

it('should show the diagram of the definition', () => {
  const node = shallow(<DefinitionEditor {...props} />);
  runAllEffects();

  expect(node.find('.diagram').find(BPMNDiagram)).toExist();
  expect(node.find('.diagram').find(BPMNDiagram).prop('xml')).toBe(loadProcessDefinitionXml());
});

it('should allow opening the diagram in a bigger modal', () => {
  const node = shallow(<DefinitionEditor {...props} />);
  runAllEffects();

  node.find('.diagram').find(Button).simulate('click');

  expect(node.find('.diagramModal').prop('open')).toBe(true);
  expect(node.find('.diagramModal').find(BPMNDiagram).prop('xml')).toBe(loadProcessDefinitionXml());
});

it('should allow removing the definition', () => {
  const spy = jest.fn();
  const node = shallow(<DefinitionEditor {...props} onChange={spy} />);

  node.find('.actionBar').find(Button).simulate('click');

  expect(spy).toHaveBeenCalledWith(null);
});
