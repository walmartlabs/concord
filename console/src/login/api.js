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
import * as common from '../api';

export const login = (username: string, password: string) => {
  console.debug("API: login['%s'] -> starting...", username);

  const opts = { credentials: 'same-origin', headers: {} };
  if (username !== undefined && password !== undefined) {
    opts.headers['Authorization'] = 'Basic ' + btoa(username + ':' + password);
  }

  return fetch('/api/service/console/whoami', opts)
    .then((response) => {
      if (!response.ok) {
        throw common.defaultError(response);
      }

      return response.json();
    })
    .then((json) => {
      console.debug("API: login ['%s'] -> done, got %o", username, json);
      return json;
    });
};
