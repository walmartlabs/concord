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

import { AnsibleEvent, AnsibleStatsEntry, AnsibleStatus } from '../../../api/process/ansible';
import { AnsibleStatChartEntry } from '../AnsibleStatChart';
import { ProcessEventEntry } from '../../../api/process/event';

export const makeStats = (stats: AnsibleStatsEntry): AnsibleStatChartEntry[] => {
    return Object.keys(AnsibleStatus)
        .map((s) => s as AnsibleStatus)
        .map((s) => ({ status: s, value: stats.stats[s] }))
        .filter((v) => v.value > 0);
};

const compareByHost = (
    { data: a }: ProcessEventEntry<AnsibleEvent>,
    { data: b }: ProcessEventEntry<AnsibleEvent>
) => (a.host! > b.host! ? 1 : a.host! < b.host! ? -1 : 0);

export const getFailures = (
    events: Array<ProcessEventEntry<AnsibleEvent>>
): Array<ProcessEventEntry<AnsibleEvent>> =>
    events
        .filter(({ data }) => data.status === AnsibleStatus.FAILED && !data.ignore_errors)
        .sort(compareByHost);
