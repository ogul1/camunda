/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.streamprocessor.records;

import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.util.buffer.BufferWriter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Spliterator;
import java.util.function.Consumer;

public final class RecordBatch implements MutableRecordBatch {

  final List<ImmutableRecordBatchEntry> recordBatchEntries = new ArrayList<>();
  private int batchSize;
  private final RecordBatchSizePredicate recordBatchSizePredicate;

  public RecordBatch(final RecordBatchSizePredicate recordBatchSizePredicate) {
    this.recordBatchSizePredicate = recordBatchSizePredicate;
  }

  @Override
  public void appendRecord(
      final long key,
      final int sourceIndex,
      final RecordType recordType,
      final Intent intent,
      final RejectionType rejectionType,
      final String rejectionReason,
      final ValueType valueType,
      final BufferWriter valueWriter) {
    final var recordBatchEntry =
        RecordBatchEntry.createEntry(
            key,
            sourceIndex,
            recordType,
            intent,
            rejectionType,
            rejectionReason,
            valueType,
            valueWriter);
    final var entryLength = recordBatchEntry.getLength();

    if (!recordBatchSizePredicate.test(recordBatchEntries.size() + 1, batchSize + entryLength)) {
      // todo decided whether we want to throw or return a bool or a either?!
      throw new IllegalStateException(
          "Can't append entry: '"
              + recordBatchEntry
              + "' with size: "
              + entryLength
              + " this would exceed the maximum batch size."
              + " [ currentBatchEntryCount: "
              + recordBatchEntries.size()
              + ", currentBatchSize: "
              + batchSize
              + "]");
    }

    recordBatchEntries.add(recordBatchEntry);
    batchSize += entryLength;
  }

  public int getBatchSize() {
    return batchSize;
  }

  @Override
  public Iterator<ImmutableRecordBatchEntry> iterator() {
    return recordBatchEntries.iterator();
  }

  @Override
  public void forEach(final Consumer<? super ImmutableRecordBatchEntry> action) {
    recordBatchEntries.forEach(action);
  }

  @Override
  public Spliterator<ImmutableRecordBatchEntry> spliterator() {
    return recordBatchEntries.spliterator();
  }
}
