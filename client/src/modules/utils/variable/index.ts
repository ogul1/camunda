/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

export function createBeautyfiedJSON(
  validJSONstring: string,
  indentationSpace = 0
) {
  return JSON.stringify(removeTabs(validJSONstring), null, indentationSpace);
}

function removeTabs(validJSONstring: string) {
  // removes all possible spaces, a user could have added during in-line edit.
  return JSON.parse(validJSONstring);
}
