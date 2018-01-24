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

const NAMESPACE = 'process/portal';

const types = {
  PROCESS_PORTAL_START_REQUEST: `${NAMESPACE}/start/request`,
  PROCESS_PORTAL_START_RESPONSE: `${NAMESPACE}/start/response`
};

export default types;

export const startProcess = (entryPoint: string) => ({
  type: types.PROCESS_PORTAL_START_REQUEST,
  entryPoint: entryPoint
});
