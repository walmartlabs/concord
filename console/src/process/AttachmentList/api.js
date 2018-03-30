/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2018 Wal-Mart Store, Inc.
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
// @ts-check
import type { ConcordId } from '../../types';
import { defaultError } from '../../api';

export const getProcessAttachmentsList = (instanceId: ConcordId): Promise<any> => {
    return fetch(`/api/v1/process/${instanceId}/attachment`, {
        credentials: 'same-origin'
    })
        .then((response) => {
            if (!response.ok) {
                throw defaultError(response);
            }
            return response.json();
        })
        .then((json) => {
            return json;
        });
};
