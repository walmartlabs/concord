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
import { Column, ColumnProps } from '../shared/Layout';

import './styles.css';

const classNames = (...values: Array<string | undefined>) => values.filter(Boolean).join(' ');

export const LeftWrap = ({ className, ...props }: ColumnProps) =>
    React.createElement(Column, {
        ...props,
        className: classNames('checkpoint-process-left-wrap', className)
    });

export const ContentBlock = ({ className, ...props }: ColumnProps) =>
    React.createElement(Column, {
        ...props,
        className: classNames('checkpoint-process-content-block', className)
    });

export const RightWrap = ({ className, ...props }: ColumnProps) =>
    React.createElement(ContentBlock, {
        ...props,
        className: classNames('checkpoint-process-right-wrap', className)
    });

export const ListItem = ({ className, ...props }: React.LiHTMLAttributes<HTMLLIElement>) =>
    React.createElement('li', {
        ...props,
        className: classNames('checkpoint-process-list-item', className)
    });
