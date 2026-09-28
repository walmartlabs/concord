/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2026 Walmart Inc.
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
import { Menu } from 'semantic-ui-react';

import './SegmentedFilter.css';

export interface SegmentedOption<T extends string> {
    value: T;
    label: string;
    count?: number;
}

interface Props<T extends string> {
    options: SegmentedOption<T>[];
    value: T;
    onChange: (value: T) => void;
}

/**
 * A compact filter for the page: a Semantic UI "secondary" menu (no borders, the active item is highlighted).
 */
const SegmentedFilter = <T extends string>({ options, value, onChange }: Props<T>) => (
    <Menu secondary={true} compact={true} size="mini" className="SegmentedFilter">
        {options.map((o) => (
            <Menu.Item key={o.value} active={o.value === value} onClick={() => onChange(o.value)}>
                {o.label}
                {o.count !== undefined && <span className="Count">{o.count}</span>}
            </Menu.Item>
        ))}
    </Menu>
);

export default SegmentedFilter;
