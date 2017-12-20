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
import type {ConcordId} from "../../types";

const NAMESPACE = "process/form";

const types = {
    PROCESS_FORM_REQUEST: `${NAMESPACE}/request`,
    PROCESS_FORM_RESPONSE: `${NAMESPACE}/response`,

    PROCESS_FORM_SUBMIT_REQUEST: `${NAMESPACE}/submit/request`,
    PROCESS_FORM_SUBMIT_RESPONSE: `${NAMESPACE}/submit/response`
};

export default types;

export const loadData = (instanceId: ConcordId, formInstanceId: ConcordId) => ({
    type: types.PROCESS_FORM_REQUEST,
    instanceId,
    formInstanceId
});

export const submit = (instanceId: ConcordId, formInstanceId: ConcordId, data: mixed, wizard: boolean, yieldFlow: boolean) => ({
    type: types.PROCESS_FORM_SUBMIT_REQUEST,
    instanceId,
    formInstanceId,
    data,
    wizard,
    yieldFlow
});
