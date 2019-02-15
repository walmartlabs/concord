/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2018 Walmart Inc.
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
import { ConcordId } from '../../../../api/common';
import { list as apiList, PaginatedProcessEntries } from '../../../../api/process';
import { generateCheckpointGroups } from './checkpointUtils';
import { ProcessEntry } from '../../../../api/process';

export interface FetchProcessArgs {
    orgId: ConcordId;
    projectId: ConcordId;
    limit?: number;
    offset?: number;
}

export const loadData = (args: FetchProcessArgs) => async ({
    setState,
    state
}: any): Promise<ProcessEntry[] | undefined> => {
    if (state.loadingData) {
        return;
    }

    setState({ loadingData: true });

    const { items: processes }: PaginatedProcessEntries = await apiList(args);

    const checkpointGroups = {};
    processes.forEach((p) => {
        if (p.checkpoints && p.statusHistory) {
            checkpointGroups[p.instanceId] = generateCheckpointGroups(
                p.checkpoints,
                p.statusHistory
            );
        }
    });

    setState({ processes, checkpointGroups, loadingData: false });
};
