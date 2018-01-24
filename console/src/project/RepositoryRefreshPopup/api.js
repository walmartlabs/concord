/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 Wal-Mart Store, Inc.
 * -----
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * =====
 */
// @flow
import type { ConcordKey } from '../../types';
import * as common from '../../api';

export const refreshRepository = (
  orgName: ConcordKey,
  projectName: ConcordKey,
  repositoryName: ConcordKey
): Promise<any> => {
  console.debug(
    "API: refreshRepository ['%s', '%s', '%s'] -> starting...",
    orgName,
    projectName,
    repositoryName
  );

  const opts = {
    method: 'POST',
    credentials: 'same-origin',
    headers: {
      'Content-Type': 'application/json'
    }
  };

  return fetch(
    `/api/v1/org/${orgName}/project/${projectName}/repository/${repositoryName}/refresh`,
    opts
  ).then((response) => {
    if (!response.ok) {
      return common.parseError(response);
    }

    console.debug(
      "API: refreshRepository ['%s', '%s', '%s'] -> done",
      orgName,
      projectName,
      repositoryName
    );
    return true;
  });
};
