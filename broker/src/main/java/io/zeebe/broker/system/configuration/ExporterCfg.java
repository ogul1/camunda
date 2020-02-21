/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.broker.system.configuration;

import io.zeebe.util.Environment;
import java.util.Map;

/**
 * Exporter component configuration. To be expanded eventually to allow enabling/disabling
 * exporters, and other general configuration.
 */
public final class ExporterCfg implements ConfigurationEntry {
  /**
   * path to the JAR file containing the exporter class
   *
   * <p>optional field: if missing, will lookup the class in the zeebe classpath
   */
  private String jarPath;

  /** fully qualified class name pointing to the class implementing the exporter interface */
  private String className;

  /** map of arguments to use when instantiating the exporter */
  private Map<String, Object> args;

  @Override
  public void init(
      final BrokerCfg globalConfig, final String brokerBase, final Environment environment) {
    if (isExternal()) {
      jarPath = ConfigurationUtil.toAbsolutePath(jarPath, brokerBase);
    }
  }

  public boolean isExternal() {
    return !isEmpty(jarPath);
  }

  public String getJarPath() {
    return jarPath;
  }

  public void setJarPath(final String jarPath) {
    this.jarPath = jarPath;
  }

  public String getClassName() {
    return className;
  }

  public void setClassName(final String className) {
    this.className = className;
  }

  public Map<String, Object> getArgs() {
    return args;
  }

  public void setArgs(final Map<String, Object> args) {
    this.args = args;
  }

  private boolean isEmpty(final String value) {
    return value == null || value.isEmpty();
  }

  @Override
  public String toString() {
    return "ExporterCfg{"
        + ", jarPath='"
        + jarPath
        + '\''
        + ", className='"
        + className
        + '\''
        + ", args="
        + args
        + '}';
  }
}
