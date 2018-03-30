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
import type { ConcordId, FetchRange } from '../../types';

const NAMESPACE = 'process/log';

const types = {
    PROCESS_LOG_REQUEST: `${NAMESPACE}/request`,
    PROCESS_LOG_RESPONSE: `${NAMESPACE}/response`
};

export default types;

export const loadData = (instanceId: ConcordId, fetchRange: FetchRange, reset: boolean) => ({
    type: types.PROCESS_LOG_REQUEST,
    instanceId,
    fetchRange,
    reset
});
