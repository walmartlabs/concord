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
import { get as apiGet, isFinal, ProcessEntry } from '../../../../api/process';
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
import { AnsibleStats, AnsibleViewToggle, ProcessToolbar } from '../../../molecules';
import RequestErrorActivity from '../../RequestErrorActivity';
import { Divider } from 'semantic-ui-react';

interface ExternalProps {
    process: ProcessEntry;
    viewSwitchHandler: (checked: boolean) => void;
}

const DATA_FETCH_INTERVAL = 5000;
const ANSIBLE_HOST_LIMIT = 10;

const ProcessAnsibleActivityOld = (props: ExternalProps) => {
    const stickyRef = useRef(null);
    const isInitialMount = useRef(true);

    const sharedAnsibleHostsFilter = useRef<SearchFilter>({});

    const [process, setProcess] = useState<ProcessEntry>(props.process);
    const [ansibleStats, setAnsibleStats] = useState<AnsibleStatsEntry>({
        uniqueHosts: 0,
        hostGroups: [],
        stats: {}
    });
    const [ansibleHosts, setAnsibleHosts] = useState<AnsibleHost[]>([]);
    const [ansibleHostsNext, setAnsibleHostsNext] = useState<number>();
    const [ansibleHostsPrev, setAnsibleHostsPrev] = useState<number>();
    const [ansibleHostsFilter, setAnsibleHostsFilter] = useState();

    const viewSwitchHandler = props.viewSwitchHandler;
    const switchHandler = useCallback(
        (ev: any, { checked }: any) => {
            viewSwitchHandler(checked === true);
        },
        [viewSwitchHandler]
    );

    const createToolbarActions = useCallback(() => {
        return <AnsibleViewToggle checked={false} onChange={switchHandler} />;
    }, [switchHandler]);

    const fetchAnsibleHosts = useCallback(
        async (filter: SearchFilter) => {
            const limit = filter.limit || ANSIBLE_HOST_LIMIT;
            const ansibleHosts = await apiListAnsibleHosts(props.process.instanceId, {
                ...filter,
                limit
            });
            setAnsibleHosts(ansibleHosts.items);
            setAnsibleHostsNext(ansibleHosts.next);
            setAnsibleHostsPrev(ansibleHosts.prev);
        },
        [props.process.instanceId]
    );

    const fetchData = useCallback(async () => {
        const process = await apiGet(props.process.instanceId, []);
        setProcess(process);

        const ansibleStats = await apiGetAnsibleStats(props.process.instanceId);
        setAnsibleStats(ansibleStats);

        await fetchAnsibleHosts(sharedAnsibleHostsFilter.current);

        // because Ansible stats are calculated by an async process on the backend, we poll for
        // additional 10 minutes after the process finishes to make sure we got everything
        const changedRecently = isBefore(
            Date.now(),
            addMinutes(parseDate(process.lastUpdatedAt), 10)
        );

        return !isFinal(process.status) || changedRecently;
    }, [props.process.instanceId, fetchAnsibleHosts]);

    const [loading, error, refresh] = usePolling(fetchData, DATA_FETCH_INTERVAL);

    useEffect(() => {
        if (isInitialMount.current) {
            isInitialMount.current = false;
            return;
        }

        sharedAnsibleHostsFilter.current = ansibleHostsFilter;
        fetchAnsibleHosts(ansibleHostsFilter);
    }, [ansibleHostsFilter, fetchAnsibleHosts]);

    if (error) {
        return (
            <div ref={stickyRef}>
                <ProcessToolbar
                    stickyRef={stickyRef}
                    loading={loading}
                    refresh={refresh}
                    process={process}
                    additionalActions={createToolbarActions()}
                />

                <RequestErrorActivity error={error} />
            </div>
        );
    }

    return (
        <div ref={stickyRef}>
            <ProcessToolbar
                stickyRef={stickyRef}
                loading={loading}
                refresh={refresh}
                process={process}
                additionalActions={createToolbarActions()}
            />

            {ansibleStats.uniqueHosts > 0 && (
                <>
                    <Divider content="Ansible Stats" horizontal={true} />
                    <AnsibleStats
                        instanceId={props.process.instanceId}
                        hosts={ansibleHosts}
                        stats={ansibleStats}
                        next={ansibleHostsNext}
                        prev={ansibleHostsPrev}
                        refresh={setAnsibleHostsFilter}
                    />
                </>
            )}
        </div>
    );
};

export default ProcessAnsibleActivityOld;
