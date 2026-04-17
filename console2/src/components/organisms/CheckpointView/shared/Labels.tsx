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
import { ClassIcon } from '../../../atoms/ClassIcon';

import './labels.css';

const classNames = (...values: Array<string | undefined>) => values.filter(Boolean).join(' ');

export const Label = ({ className, ...props }: React.HTMLAttributes<HTMLSpanElement>) => (
    <span {...props} className={classNames('checkpoint-text checkpoint-label', className)} />
);

export const StatusText = ({ className, ...props }: React.HTMLAttributes<HTMLSpanElement>) => (
    <span {...props} className={classNames('checkpoint-text checkpoint-status-text', className)} />
);

export const CheckpointName = ({ className, ...props }: React.HTMLAttributes<HTMLSpanElement>) => (
    <span {...props} className={classNames('checkpoint-text checkpoint-name', className)} />
);

export const CheckpointGroupName = ({
    className,
    ...props
}: React.HTMLAttributes<HTMLSpanElement>) => (
    <span {...props} className={classNames('checkpoint-text checkpoint-group-name', className)} />
);

export const LoadError = ({ className, ...props }: React.HTMLAttributes<HTMLDivElement>) => (
    <div {...props} className={classNames('checkpoint-load-error', className)} />
);

export const Status: React.SFC<{ as?: 'span' | 'div' | 'td' }> = ({ as = 'span', children }) => {
    switch (children) {
        case 'FAILED':
            return React.createElement(
                as,
                { style: { color: '#DB2928' } },
                <>
                    {children} <ClassIcon classes="red cancel icon" />
                </>
            );
        case 'FINISHED':
            return React.createElement(
                as,
                { style: { color: 'green' } },
                <>
                    {children} <ClassIcon classes="green check icon" />
                </>
            );
        default:
            return React.createElement(as, null, children);
    }
};
