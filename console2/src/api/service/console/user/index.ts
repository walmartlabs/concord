/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2023 - 2018 Walmart Inc.
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

import {ConcordId, ConcordKey, fetchJson, queryParams} from '../../../common';
import { ProcessEntry } from '../../../process';

export interface UserActivity {
    processes: ProcessEntry[];
}

export interface ProcessCardEntry {
    id: ConcordId;
    orgName: ConcordKey;
    projectName: ConcordKey;
    repoName: ConcordKey;
    entryPoint: string;
    name: string;
    description?: string;
    icon?: string;
    isCustomForm: boolean;
}

export const getActivity = (
    maxOwnProcesses: number
): Promise<UserActivity> =>
    fetchJson(
        `/api/v2/service/console/user/activity?${queryParams({ maxOwnProcesses })}`
    );

export const listProcessCards = (
): Promise<ProcessCardEntry[]> =>
    fetchJson(
        `/api/v1/processcard`
    );

export const getProcessCard = (
    cardId: ConcordId
): Promise<ProcessCardEntry> =>
    fetchJson(
        `/api/v1/processcard/${cardId}`
    );
