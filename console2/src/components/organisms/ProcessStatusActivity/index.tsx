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

import { addMinutes, isBefore, parseISO as parseDate } from 'date-fns';
import * as React from 'react';
import { useCallback, useEffect, useRef, useState } from 'react';
import { Route } from 'react-router';
import { Divider } from 'semantic-ui-react';

import { get as apiGet, isFinal, ProcessEntry } from '../../../api/process';
import {
    AnsibleHost,
    AnsibleStatsEntry,
    getAnsibleStats as apiGetAnsibleStats,
    listAnsibleHosts as apiListAnsibleHosts,
    SearchFilter
} from '../../../api/process/ansible';
import { FormListEntry, list as apiListForms } from '../../../api/process/form';

import { usePolling } from '../../../api/usePolling';
import {
    AnsibleStats,
    ProcessActionList,
    ProcessStatusTable,
    ProcessToolbar
} from '../../molecules';
import ProcessCheckpointActivity from '../ProcessCheckpointActivity';
import RequestErrorActivity from '../RequestErrorActivity';

import './styles.css';

interface ExternalProps {
    process: ProcessEntry;
}

const DATA_FETCH_INTERVAL = 5000;
const ANSIBLE_HOST_LIMIT = 10;

const ProcessStatusActivity = (props: ExternalProps) => {
    const stickyRef = useRef(null);
    const isInitialMount = useRef(true);

    const sharedAnsibleHostsFilter = useRef<SearchFilter>({});

    const [process, setProcess] = useState<ProcessEntry>(props.process);
    const [forms, setForms] = useState<FormListEntry[]>([]);
    const [ansibleStats, setAnsibleStats] = useState<AnsibleStatsEntry>({
        uniqueHosts: 0,
        hostGroups: [],
        stats: {}
    });
    const [ansibleHosts, setAnsibleHosts] = useState<AnsibleHost[]>([]);
    const [ansibleHostsNext, setAnsibleHostsNext] = useState<number>();
    const [ansibleHostsPrev, setAnsibleHostsPrev] = useState<number>();
    const [ansibleHostsFilter, setAnsibleHostsFilter] = useState();

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
        const process = await apiGet(props.process.instanceId, ['checkpoints', 'history']);
        setProcess(process);

        const forms = await apiListForms(props.process.instanceId);
        setForms(forms);

        const ansibleStats = await apiGetAnsibleStats(props.process.instanceId);
        setAnsibleStats(ansibleStats);

        fetchAnsibleHosts(sharedAnsibleHostsFilter.current);

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
                />

                <RequestErrorActivity error={error} />
            </div>
        );
    }

    const hasCheckpoints = process.checkpoints && process.checkpoints.length > 0;
    const hasStatusHistory = process.statusHistory && process.statusHistory.length > 0;

    return (
        <div ref={stickyRef}>
            <ProcessToolbar
                stickyRef={stickyRef}
                loading={loading}
                refresh={refresh}
                process={process}
            />

            <Divider content="Process Details" horizontal={true} />
            <ProcessStatusTable data={process} />

            {forms.length > 0 && (
                <>
                    <Divider content="Required Actions" horizontal={true} />
                    <Route
                        render={({ history }) => (
                            <ProcessActionList
                                instanceId={props.process.instanceId}
                                forms={forms}
                                onOpenWizard={() =>
                                    history.push(
                                        `/process/${props.process.instanceId}/wizard?fullScreen=true`
                                    )
                                }
                            />
                        )}
                    />
                </>
            )}

            {hasCheckpoints && hasStatusHistory && (
                <>
                    <Divider content="Checkpoints" horizontal={true} />
                    <ProcessCheckpointActivity
                        instanceId={process.instanceId}
                        processStatus={process.status}
                        processDisabled={process.disabled}
                        checkpoints={process.checkpoints!}
                        statusHistory={process.statusHistory!}
                        onRestoreComplete={refresh}
                    />
                </>
            )}

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

export default ProcessStatusActivity;
