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

import { AnsibleEvent, AnsibleStatus, ProcessEventEntry } from '../../../api/process/event';
import { AnsibleHostListEntry } from '../AnsibleHostList';
import { AnsibleStatChartEntry } from '../AnsibleStatChart';

export const makeHostList = (
    events: Array<ProcessEventEntry<AnsibleEvent>>
): AnsibleHostListEntry[] => {
    const result: { [id: string]: AnsibleHostListEntry } = {};
    events.forEach((e) => {
        const data = e.data;
        const hostDuration =
            ((result[data.host] && result[data.host].duration) || 0) + (e.duration || 0);
        result[data.host] = { host: data.host, status: data.status!, duration: hostDuration };
    });

    return Object.keys(result).map((k) => result[k]);
};

const hostsByStatus = (
    evs: Array<ProcessEventEntry<AnsibleEvent>>,
    status: AnsibleStatus,
    additionalFilter?: (data: AnsibleEvent) => boolean
): string[] =>
    evs
        .filter(
            ({ data }) =>
                data.status === status && (additionalFilter ? additionalFilter(data) : true)
        )
        .map(({ data }) => data.host!);

export const makeStats = (
    events: Array<ProcessEventEntry<AnsibleEvent>>
): AnsibleStatChartEntry[] => {
    const evs = events.filter(({ data }) => !!data.host && !!data.status);

    // skip events with ignore_errors=true, they should be marked as "OK" (if there was no other failures)
    const failed = new Set(hostsByStatus(evs, AnsibleStatus.FAILED, (data) => !data.ignore_errors));

    const unreachable = new Set(
        hostsByStatus(evs, AnsibleStatus.UNREACHABLE).filter((h) => !failed.has(h))
    );

    const changed = new Set(
        hostsByStatus(evs, AnsibleStatus.CHANGED).filter(
            (h) => !failed.has(h) && !unreachable.has(h)
        )
    );

    const ok = new Set(
        hostsByStatus(evs, AnsibleStatus.OK).filter(
            (h) => !failed.has(h) && !unreachable.has(h) && !changed.has(h)
        )
    );

    const skipped = new Set(
        hostsByStatus(evs, AnsibleStatus.SKIPPED).filter(
            (h) => !failed.has(h) && !unreachable.has(h) && !changed.has(h) && !ok.has(h)
        )
    );

    const result = [
        { status: AnsibleStatus.OK, value: ok.size },
        { status: AnsibleStatus.CHANGED, value: changed.size },
        { status: AnsibleStatus.SKIPPED, value: skipped.size },
        { status: AnsibleStatus.UNREACHABLE, value: unreachable.size },
        { status: AnsibleStatus.FAILED, value: failed.size }
    ];

    return result.filter((r) => r.value > 0);
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

export const countUniqueHosts = (hosts: AnsibleHostListEntry[]): number =>
    new Set(hosts.map((h) => h.host)).size;
