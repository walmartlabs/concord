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

import { list as apiList } from '../../../api/process/attachment';
import { useState } from 'react';
import { get as apiGet, isFinal, ProcessEntry } from '../../../api/process';
import { ProcessAttachmentsList, ProcessToolbar } from '../../molecules';
import { useRef } from 'react';
import RequestErrorActivity from '../RequestErrorActivity';
import { useCallback } from 'react';
import { usePolling } from '../../../api/usePolling';

interface ExternalProps {
    process: ProcessEntry;
}

const DATA_FETCH_INTERVAL = 5000;

const ProcessAttachmentsActivity = (props: ExternalProps) => {
    const stickyRef = useRef(null);

    const [process, setProcess] = useState<ProcessEntry>(props.process);
    const [data, setData] = useState<string[]>([]);

    const fetchData = useCallback(async () => {
        const process = await apiGet(props.process.instanceId, ['history']);
        setProcess(process);

        const data = await apiList(props.process.instanceId);
        setData(makeAttachmentsList(data));

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

    return (
        <div ref={stickyRef}>
            <ProcessToolbar
                stickyRef={stickyRef}
                loading={loading}
                refresh={refresh}
                process={process}
            />

            <ProcessAttachmentsList instanceId={props.process.instanceId} data={data} />
        </div>
    );
};

const makeAttachmentsList = (data: string[]): string[] => {
    return data.filter((attachment) => attachment.indexOf('_state') < 0);
};

export default ProcessAttachmentsActivity;
