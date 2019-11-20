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

import * as React from 'react';
import { useCallback, useRef, useState } from 'react';
import { Route } from 'react-router';
import { Divider } from 'semantic-ui-react';

import { get as apiGet, isFinal, ProcessEntry } from '../../../api/process';
import { FormListEntry, list as apiListForms } from '../../../api/process/form';

import { usePolling } from '../../../api/usePolling';
import { ProcessActionList, ProcessStatusTable, ProcessToolbar } from '../../molecules';
import ProcessCheckpointActivity from '../ProcessCheckpointActivity';
import RequestErrorActivity from '../RequestErrorActivity';

import './styles.css';

interface ExternalProps {
    process: ProcessEntry;
}

const DATA_FETCH_INTERVAL = 5000;

const ProcessStatusActivity = (props: ExternalProps) => {
    const stickyRef = useRef(null);

    const [process, setProcess] = useState<ProcessEntry>(props.process);
    const [forms, setForms] = useState<FormListEntry[]>([]);

    const fetchData = useCallback(async () => {
        const process = await apiGet(props.process.instanceId, ['checkpoints', 'history']);
        setProcess(process);

        const forms = await apiListForms(props.process.instanceId);
        setForms(forms);

        return !isFinal(process.status);
    }, [props.process.instanceId]);

    const [loading, error, refresh] = usePolling(fetchData, DATA_FETCH_INTERVAL);

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

            {forms.length > 0 && !isFinal(process.status) && (
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
        </div>
    );
};

export default ProcessStatusActivity;
