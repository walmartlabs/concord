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
import { Icon, Popup, SemanticICONS, SemanticCOLORS } from 'semantic-ui-react';

import { ProcessEntry, ProcessStatus } from '../../../api/process';
import { formatDistanceToNow, isAfter, parseISO as parseDate } from 'date-fns';

enum AdditionalProcessStatus {
    /**
     * ENQUEUED + (startAt is not null)
     */
    SCHEDULED = 'SCHEDULED'
}

export const statusToIcon: {
    [status: string]: { name: SemanticICONS; color?: SemanticCOLORS; loading?: boolean };
} = {
    NEW: { name: 'inbox', color: 'grey' },
    PREPARING: { name: 'info', color: 'blue' },
    ENQUEUED: { name: 'block layout', color: 'grey' },
    WAITING: { name: 'block layout', color: 'grey' },
    SCHEDULED: { name: 'hourglass start', color: 'grey' },
    RESUMING: { name: 'circle notched', color: 'grey', loading: true },
    SUSPENDED: { name: 'wait', color: 'blue' },
    STARTING: { name: 'circle notched', color: 'grey', loading: true },
    RUNNING: { name: 'circle notched', color: 'blue', loading: true },
    FINISHED: { name: 'check', color: 'green' },
    FAILED: { name: 'remove', color: 'red' },
    CANCELLED: { name: 'remove', color: 'grey' },
    TIMED_OUT: { name: 'wait', color: 'red' }
};

type Status = ProcessStatus | AdditionalProcessStatus;

interface ProcessStatusIconProps {
    process: ProcessEntry;
}

const getStatus = (process: ProcessEntry): Status => {
    if (process.status === ProcessStatus.ENQUEUED && process.startAt !== undefined) {
        return AdditionalProcessStatus.SCHEDULED;
    }
    return process.status;
};

const getLabel = (process: ProcessEntry): string => {
    if (process.startAt && process.status === ProcessStatus.ENQUEUED) {
        const startAt = parseDate(process.startAt);
        if (isAfter(startAt, Date.now())) {
            return 'starts in ' + formatDistanceToNow(startAt);
        }
    }

    return process.status;
};

export default ({ process }: ProcessStatusIconProps) => {
    const status = getStatus(process);

    let i = statusToIcon[status];
    if (!i) {
        i = { name: 'question' };
    }

    return (
        <Popup
            trigger={
                <Icon
                    name={i.name}
                    color={i.color}
                    size={'large'}
                    loading={i.loading}
                    style={{ margin: 0 }}
                />
            }
            content={getLabel(process)}
            inverted={true}
            position="top center"
        />
    );
};
