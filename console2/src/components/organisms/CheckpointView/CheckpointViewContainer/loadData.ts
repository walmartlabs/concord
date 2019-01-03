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
import { generateCheckpointGroups } from './checkpointUtils';
import { listProcesses } from '../../../../api/service/console';

export interface FetchProcessArgs {
    orgId: string;
    projectId: string;
    limit?: number;
    offset?: number;
}

export const loadData = (args: FetchProcessArgs) => async ({ setState }: any) => {
    setState({ loadingData: true });

    const processes = await listProcesses(args.orgId, args.projectId, args.limit, args.offset);
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
