/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.rest;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ErrorResponseDto {
  private String errorCode;
  private String errorMessage;
  private String detailedMessage;
  private AuthorizedReportDefinitionDto reportDefinition;

  public ErrorResponseDto() {
  }

  public ErrorResponseDto(String errorMessage, AuthorizedReportDefinitionDto reportDefinition) {
    this.errorMessage = errorMessage;
    this.reportDefinition = reportDefinition;
  }

  public ErrorResponseDto(String errorMessage) {
    this.errorMessage = errorMessage;
  }

  public ErrorResponseDto(String errorCode, String errorMessage) {
    this.errorCode = errorCode;
    this.errorMessage = errorMessage;
  }

  public ErrorResponseDto(String errorCode, String errorMessage, String detailedMessage) {
    this.errorCode = errorCode;
    this.errorMessage = errorMessage;
    this.detailedMessage = detailedMessage;
  }
}
