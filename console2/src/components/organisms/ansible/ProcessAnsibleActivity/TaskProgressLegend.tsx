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
import React from 'react';
import {
    FAILED_COLOR_HEX,
    OK_COLOR_HEX,
    RUNNING_COLOR_HEX,
    SKIPPED_COLOR_HEX,
    UNREACHABLE_COLOR_HEX
} from './types';
import { Grid } from 'semantic-ui-react';

export interface ExternalProps {
    loading: boolean;
}

const LEGEND_ITEMS = [
    { name: 'OK', color: OK_COLOR_HEX },
    { name: 'FAILED', color: FAILED_COLOR_HEX },
    { name: 'UNREACHABLE', color: UNREACHABLE_COLOR_HEX },
    { name: 'SKIPPED', color: SKIPPED_COLOR_HEX },
    { name: 'RUNNING', color: RUNNING_COLOR_HEX }
];

const renderLegendItem = (item: { color: string; name: string }) => {
    return (
        <Grid.Column key={item.name}>
            <h5 className="ui image header">
                <svg width={16} height={16} className="taskLegendColor">
                    <g key={item.name}>
                        <rect x={0} y={0} width={16} height={16} fill={item.color} />
                    </g>
                </svg>
                <div className="content taskLegendContent">{item.name}</div>
            </h5>
        </Grid.Column>
    );
};

const TaskProgressLegend = ({ loading }: ExternalProps) => {
    return (
        <Grid columns={7} centered={true} className={loading ? 'loading' : ''}>
            {LEGEND_ITEMS.map((item) => renderLegendItem(item))}
        </Grid>
    );
};

export default TaskProgressLegend;
