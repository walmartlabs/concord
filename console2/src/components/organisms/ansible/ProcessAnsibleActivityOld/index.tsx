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
import { get as apiGet, isFinal } from '../../../../api/process';
import { default as React, useCallback, useEffect, useRef, useState } from 'react';
import {
    AnsibleHost,
    AnsibleStatsEntry,
    getAnsibleStats as apiGetAnsibleStats,
    listAnsibleHosts as apiListAnsibleHosts,
    SearchFilter
} from '../../../../api/process/ansible';
import { addMinutes, isBefore, parseISO as parseDate } from 'date-fns';
import { usePolling } from '../../../../api/usePolling';
import { AnsibleStats } from '../../../molecules';
import RequestErrorActivity from '../../RequestErrorActivity';
import { ConcordId } from '../../../../api/common';

interface ExternalProps {
    instanceId: ConcordId;
    loadingHandler: (inc: number) => void;
    forceRefresh: boolean;
}

const DATA_FETCH_INTERVAL = 5000;
const ANSIBLE_HOST_LIMIT = 10;

const ProcessAnsibleActivityOld = ({ instanceId, loadingHandler, forceRefresh }: ExternalProps) => {
    const isInitialMount = useRef(true);

    const sharedAnsibleHostsFilter = useRef<SearchFilter>({});

    const [ansibleStats, setAnsibleStats] = useState<AnsibleStatsEntry>({
        uniqueHosts: 0,
        hostGroups: [],
        stats: {}
    });
    const [ansibleHosts, setAnsibleHosts] = useState<AnsibleHost[]>([]);
    const [ansibleHostsNext, setAnsibleHostsNext] = useState<number>();
    const [ansibleHostsPrev, setAnsibleHostsPrev] = useState<number>();
    const [ansibleHostsFilter, setAnsibleHostsFilter] = useState({});

    const fetchAnsibleHosts = useCallback(
        async (filter: SearchFilter) => {
            const limit = filter.limit || ANSIBLE_HOST_LIMIT;
            const ansibleHosts = await apiListAnsibleHosts(instanceId, {
                ...filter,
                limit
            });
            setAnsibleHosts(ansibleHosts.items);
            setAnsibleHostsNext(ansibleHosts.next);
            setAnsibleHostsPrev(ansibleHosts.prev);
        },
        [instanceId]
    );

    const fetchData = useCallback(async () => {
        const process = await apiGet(instanceId, []);

        const ansibleStats = await apiGetAnsibleStats(instanceId);
        setAnsibleStats(ansibleStats);

        await fetchAnsibleHosts(sharedAnsibleHostsFilter.current);

        // because Ansible stats are calculated by an async process on the backend, we poll for
        // additional 10 minutes after the process finishes to make sure we got everything
        const changedRecently = isBefore(
            Date.now(),
            addMinutes(parseDate(process.lastUpdatedAt), 10)
        );

        return !isFinal(process.status) || changedRecently;
    }, [instanceId, fetchAnsibleHosts]);

    const error = usePolling(fetchData, DATA_FETCH_INTERVAL, loadingHandler, forceRefresh);

    useEffect(() => {
        if (isInitialMount.current) {
            isInitialMount.current = false;
            return;
        }

        sharedAnsibleHostsFilter.current = ansibleHostsFilter;
        fetchAnsibleHosts(ansibleHostsFilter);
    }, [ansibleHostsFilter, fetchAnsibleHosts]);

    if (error) {
        return <RequestErrorActivity error={error} />;
    }

    return (
        <>
            {ansibleStats.uniqueHosts > 0 && (
                <>
                    <AnsibleStats
                        instanceId={instanceId}
                        hosts={ansibleHosts}
                        stats={ansibleStats}
                        next={ansibleHostsNext}
                        prev={ansibleHostsPrev}
                        refresh={setAnsibleHostsFilter}
                    />
                </>
            )}
        </>
    );
};

export default ProcessAnsibleActivityOld;
