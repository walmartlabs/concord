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

import './layout.css';

const classNames = (...values: Array<string | undefined>) => values.filter(Boolean).join(' ');

export const Row = ({ className, ...props }: React.HTMLAttributes<HTMLDivElement>) => (
    <div {...props} className={classNames('checkpoint-row', className)} />
);

export interface ColumnProps extends React.HTMLAttributes<HTMLDivElement> {
    flex?: number;
    background?: string;
    maxWidth?: number;
}

export const Column = ({
    className,
    flex,
    background,
    maxWidth,
    style,
    ...props
}: ColumnProps) => (
    <div
        {...props}
        className={classNames('checkpoint-column', className)}
        style={{
            flex: flex ?? 1,
            backgroundColor: background,
            maxWidth,
            ...style
        }}
    />
);
