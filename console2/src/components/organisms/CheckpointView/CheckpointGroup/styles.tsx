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
import { ProcessStatus } from '../../../../api/process';
import { SemanticCOLORS } from 'semantic-ui-react';

import './styles.css';

const classNames = (...values: Array<string | undefined>) => values.filter(Boolean).join(' ');

export const FlexWrapper = ({ className, ...props }: React.HTMLAttributes<HTMLDivElement>) => (
    <div {...props} className={classNames('checkpoint-group-flex-wrapper', className)} />
);

export const GroupWrapper = ({ className, ...props }: React.HTMLAttributes<HTMLDivElement>) => (
    <div {...props} className={classNames('checkpoint-group-wrapper', className)} />
);

export const CheckpointNode = ({ className, ...props }: React.HTMLAttributes<HTMLDivElement>) => (
    <div {...props} className={classNames('checkpoint-node', className)} />
);

export const getStatusColor = (status: string): string => {
    switch (status) {
        case ProcessStatus.NEW:
        case ProcessStatus.PREPARING:
        case ProcessStatus.RUNNING:
        case ProcessStatus.STARTING:
        case ProcessStatus.SUSPENDED:
            return '#4182C3';
        case ProcessStatus.FINISHED:
            return '#0ca934';
        case ProcessStatus.CANCELLED:
        case ProcessStatus.FAILED:
        case ProcessStatus.TIMED_OUT:
            return '#DB2928';
        case ProcessStatus.ENQUEUED:
        case ProcessStatus.RESUMING:
        case ProcessStatus.WAITING:
        default:
            return 'grey';
    }
};

export const getStatusButtonColor = (status: string): SemanticCOLORS => {
    switch (status) {
        case ProcessStatus.NEW:
        case ProcessStatus.PREPARING:
        case ProcessStatus.RUNNING:
        case ProcessStatus.STARTING:
        case ProcessStatus.SUSPENDED:
            return 'blue';
        case ProcessStatus.FINISHED:
            return 'green';
        case ProcessStatus.CANCELLED:
        case ProcessStatus.FAILED:
        case ProcessStatus.TIMED_OUT:
            return 'red';
        case ProcessStatus.ENQUEUED:
        case ProcessStatus.RESUMING:
        case ProcessStatus.WAITING:
        default:
            return 'grey';
    }
};

interface CheckpointBoxProps {
    statusColor?: any;
}

export const CheckpointBox = ({
    className,
    statusColor,
    style,
    ...props
}: CheckpointBoxProps & React.HTMLAttributes<HTMLDivElement>) => (
    <div
        {...props}
        className={classNames('checkpoint-box', className)}
        style={{ background: statusColor || 'gray', ...style }}
    />
);

export const EmptyBox = ({ className, ...props }: React.HTMLAttributes<HTMLDivElement>) => (
    <div {...props} className={classNames('checkpoint-empty-box', className)} />
);

export const GroupItems = ({ className, ...props }: React.HTMLAttributes<HTMLDivElement>) => (
    <div {...props} className={classNames('checkpoint-group-items', className)} />
);
