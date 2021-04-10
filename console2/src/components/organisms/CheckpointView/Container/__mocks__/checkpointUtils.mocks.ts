/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2019 Walmart Inc.
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
import {
    ProcessCheckpointEntry,
    ProcessHistoryEntry,
    ProcessStatus
} from '../../../../../api/process';
import { ColumnDefinition, RenderType } from '../../../../../api/org';

export const validProcessCheckpoints: ProcessCheckpointEntry[] = [
    {
        id: '1',
        name: 'checkpoint 1',
        createdAt: '2019-02-18T17:23:32.520Z'
    },
    {
        id: '2',
        name: 'checkpoint 2',
        createdAt: '2019-02-18T17:23:32.790Z'
    },
    {
        id: '3',
        name: 'checkpoint 3',
        createdAt: '2019-02-18T17:23:32.950Z'
    }
];

export const validProcessHistory: ProcessHistoryEntry[] = [
    {
        changeDate: '2019-02-18T17:23:29.678Z',
        id: 'e89c124e-33a1-11e9-855e-fa163e7ef419',
        status: ProcessStatus.PREPARING
    },
    {
        changeDate: '2019-02-18T17:23:30.426Z',
        id: 'e90e2280-33a1-11e9-855e-fa163e7ef419',
        status: ProcessStatus.ENQUEUED
    },
    {
        changeDate: '2019-02-18T17:23:30.907Z',
        id: 'e9590f7a-33a1-11e9-aa54-fa163e7ef419',
        status: ProcessStatus.STARTING
    },
    {
        changeDate: '2019-02-18T17:23:31.878Z',
        id: 'e9ec1a36-33a1-11e9-bbef-fa163e7ef419',
        status: ProcessStatus.RUNNING
    },
    {
        changeDate: '2019-02-18T17:23:33.445Z',
        id: 'eadac2da-33a1-11e9-bbef-fa163e7ef419',
        status: ProcessStatus.FINISHED
    }
];

export const emptyProcessHistory: ProcessHistoryEntry[] = [];
export const emptyProcessCheckpoints: ProcessCheckpointEntry[] = [];

export const hasMetaColumnDefinition: ColumnDefinition = {
    source: 'meta.repoMetadata',
    caption: 'Target Repo',
    searchType: 'substring',
    searchValueType: 'string'
};

export const missingMetaColumnDefinition: ColumnDefinition = {
    render: RenderType.TIMESTAMP,
    source: 'lastUpdatedAt',
    caption: 'Updated'
};
