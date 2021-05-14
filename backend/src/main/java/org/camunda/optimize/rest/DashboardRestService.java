/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.rest;

import lombok.AllArgsConstructor;
import org.camunda.optimize.dto.optimize.query.IdResponseDto;
import org.camunda.optimize.dto.optimize.query.dashboard.DashboardDefinitionRestDto;
import org.camunda.optimize.dto.optimize.rest.AuthorizedDashboardDefinitionResponseDto;
import org.camunda.optimize.rest.mapper.DashboardRestMapper;
import org.camunda.optimize.service.dashboard.DashboardService;
import org.camunda.optimize.service.security.SessionService;
import org.springframework.stereotype.Component;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.UriInfo;
import java.util.Optional;

import static org.camunda.optimize.rest.queryparam.QueryParamUtil.normalizeNullStringValue;

@AllArgsConstructor
@Path("/dashboard")
@Component
public class DashboardRestService {

  private final DashboardService dashboardService;
  private final SessionService sessionService;
  private final DashboardRestMapper dashboardRestMapper;

  /**
   * Creates a new dashboard.
   *
   * @return the id of the dashboard
   */
  @POST
  @Produces(MediaType.APPLICATION_JSON)
  @Consumes(MediaType.APPLICATION_JSON)
  public IdResponseDto createNewDashboard(@Context final ContainerRequestContext requestContext,
                                          DashboardDefinitionRestDto dashboardDefinitionDto) {
    String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
    return dashboardService.createNewDashboardAndReturnId(
      userId,
      Optional.ofNullable(dashboardDefinitionDto)
        .orElseGet(DashboardDefinitionRestDto::new)
    );
  }

  @POST
  @Path("/{id}/copy")
  @Produces(MediaType.APPLICATION_JSON)
  public IdResponseDto copyDashboard(@Context UriInfo uriInfo,
                                     @Context ContainerRequestContext requestContext,
                                     @PathParam("id") String dashboardId,
                                     @QueryParam("collectionId") String collectionId,
                                     @QueryParam("name") String newDashboardName) {
    String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);

    if (collectionId == null) {
      return dashboardService.copyDashboard(dashboardId, userId, newDashboardName);
    } else {
      // 'null' or collectionId value provided
      collectionId = normalizeNullStringValue(collectionId);
      return dashboardService.copyAndMoveDashboard(dashboardId, userId, collectionId, newDashboardName);
    }
  }

  /**
   * Retrieve the dashboard to the specified id.
   */
  @GET
  @Path("/{id}")
  @Produces(MediaType.APPLICATION_JSON)
  public AuthorizedDashboardDefinitionResponseDto getDashboard(@Context ContainerRequestContext requestContext,
                                                               @PathParam("id") String dashboardId) {
    String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
    AuthorizedDashboardDefinitionResponseDto dashboardDefinition =
      dashboardService.getDashboardDefinition(dashboardId, userId);
    dashboardRestMapper.prepareRestResponse(dashboardDefinition);
    return dashboardDefinition;
  }

  /**
   * Updates the given fields of a dashboard to the given id.
   *
   * @param dashboardId      the id of the dashboard
   * @param updatedDashboard dashboard that needs to be updated. Only the fields that are defined here are actually
   *                         updated.
   */
  @PUT
  @Path("/{id}")
  @Produces(MediaType.APPLICATION_JSON)
  @Consumes(MediaType.APPLICATION_JSON)
  public void updateDashboard(@Context ContainerRequestContext requestContext,
                              @PathParam("id") String dashboardId,
                              DashboardDefinitionRestDto updatedDashboard) {
    updatedDashboard.setId(dashboardId);
    String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
    dashboardService.updateDashboard(updatedDashboard, userId);
  }

  /**
   * Delete the dashboard to the specified id.
   */
  @DELETE
  @Path("/{id}")
  @Produces(MediaType.APPLICATION_JSON)
  public void deleteDashboard(@Context ContainerRequestContext requestContext,
                              @PathParam("id") String dashboardId) {
    String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
    dashboardService.deleteDashboard(dashboardId, userId);
  }

}

