package org.camunda.operate.entities;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;


public class WorkflowInstanceEntity extends OperateEntity {

  private String workflowId;

  private OffsetDateTime startDate;
  private OffsetDateTime endDate;

  private WorkflowInstanceState state;

  private String businessKey;

  private List<IncidentEntity> incidents = new ArrayList<>();

  private List<ActivityInstanceEntity> activities = new ArrayList<>();

  private List<OperationEntity> operations = new ArrayList<>();

  private Integer partitionId;

  private long position;

  private String topicName;

  public String getWorkflowId() {
    return workflowId;
  }

  public void setWorkflowId(String workflowId) {
    this.workflowId = workflowId;
  }

  public OffsetDateTime getStartDate() {
    return startDate;
  }

  public void setStartDate(OffsetDateTime startDate) {
    this.startDate = startDate;
  }

  public OffsetDateTime getEndDate() {
    return endDate;
  }

  public void setEndDate(OffsetDateTime endDate) {
    this.endDate = endDate;
  }

  public WorkflowInstanceState getState() {
    return state;
  }

  public void setState(WorkflowInstanceState state) {
    this.state = state;
  }

  public String getBusinessKey() {
    return businessKey;
  }

  public void setBusinessKey(String businessKey) {
    this.businessKey = businessKey;
  }

  public List<IncidentEntity> getIncidents() {
    return incidents;
  }

  public void setIncidents(List<IncidentEntity> incidents) {
    this.incidents = incidents;
  }

  public List<ActivityInstanceEntity> getActivities() {
    return activities;
  }

  public void setActivities(List<ActivityInstanceEntity> activityInstances) {
    this.activities = activityInstances;
  }

  public List<OperationEntity> getOperations() {
    return operations;
  }

  public void setOperations(List<OperationEntity> operations) {
    this.operations = operations;
  }

  public Integer getPartitionId() {
    return partitionId;
  }

  public void setPartitionId(Integer partitionId) {
    this.partitionId = partitionId;
  }

  public long getPosition() {
    return position;
  }

  public void setPosition(long position) {
    this.position = position;
  }

  public String getTopicName() {
    return topicName;
  }

  public void setTopicName(String topicName) {
    this.topicName = topicName;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o)
      return true;
    if (o == null || getClass() != o.getClass())
      return false;
    if (!super.equals(o))
      return false;

    WorkflowInstanceEntity that = (WorkflowInstanceEntity) o;

    if (position != that.position)
      return false;
    if (workflowId != null ? !workflowId.equals(that.workflowId) : that.workflowId != null)
      return false;
    if (startDate != null ? !startDate.equals(that.startDate) : that.startDate != null)
      return false;
    if (endDate != null ? !endDate.equals(that.endDate) : that.endDate != null)
      return false;
    if (state != that.state)
      return false;
    if (businessKey != null ? !businessKey.equals(that.businessKey) : that.businessKey != null)
      return false;
    if (incidents != null ? !incidents.equals(that.incidents) : that.incidents != null)
      return false;
    if (activities != null ? !activities.equals(that.activities) : that.activities != null)
      return false;
    if (operations != null ? !operations.equals(that.operations) : that.operations != null)
      return false;
    if (partitionId != null ? !partitionId.equals(that.partitionId) : that.partitionId != null)
      return false;
    return topicName != null ? topicName.equals(that.topicName) : that.topicName == null;
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + (workflowId != null ? workflowId.hashCode() : 0);
    result = 31 * result + (startDate != null ? startDate.hashCode() : 0);
    result = 31 * result + (endDate != null ? endDate.hashCode() : 0);
    result = 31 * result + (state != null ? state.hashCode() : 0);
    result = 31 * result + (businessKey != null ? businessKey.hashCode() : 0);
    result = 31 * result + (incidents != null ? incidents.hashCode() : 0);
    result = 31 * result + (activities != null ? activities.hashCode() : 0);
    result = 31 * result + (operations != null ? operations.hashCode() : 0);
    result = 31 * result + (partitionId != null ? partitionId.hashCode() : 0);
    result = 31 * result + (int) (position ^ (position >>> 32));
    result = 31 * result + (topicName != null ? topicName.hashCode() : 0);
    return result;
  }
}
