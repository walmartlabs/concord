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
import { AnsibleStatus, getStatusColor } from '../../../../api/process/ansible';
import { ChartEntry } from '../../DonutChart';
import { DonutChart } from '../../../molecules';

export interface AnsibleStatChartEntry {
    status: AnsibleStatus;
    value: number;
}

interface Props {
    width: number;
    height: number;
    margin?: {
        top: number;
        left: number;
    };
    data: AnsibleStatChartEntry[];
    uniqueHosts: number;
    onClick?: (status?: AnsibleStatus) => void;
}

interface State {
    selectedStatus?: AnsibleStatus;
}

const toChartData = (data: AnsibleStatChartEntry[]): ChartEntry[] =>
    data.map(({ status, value }) => ({
        key: status,
        value,
        color: getStatusColor(status)
    }));

class AnsibleStatChart extends React.PureComponent<Props, State> {
    constructor(props: Props) {
        super(props);
        this.state = {};
    }

    handleStatusFilter(status: AnsibleStatus) {
        const currentStatus = this.state.selectedStatus;
        const newStatus = currentStatus === status ? undefined : status;

        this.setState({ selectedStatus: newStatus });

        const { onClick } = this.props;
        if (onClick !== undefined) {
            onClick(newStatus);
        }
    }

    render() {
        const {
            width,
            height,
            margin = { top: 40, left: 20 },
            data,
            onClick,
            uniqueHosts
        } = this.props;

        const { selectedStatus } = this.state;

        const radius = Math.min(width, height) / 1.5;

        const x = 10;
        const y = 10;
        const size = 30;

        return (
            <svg width={width} height={height}>
                <g>
                    {data.map((d, idx) => (
                        <g key={idx}>
                            <rect
                                x={x}
                                y={y + (idx + 1) * size}
                                width={size * 0.8}
                                height={size * 0.8}
                                fill={getStatusColor(d.status)}
                                rx="7"
                                ry="7"
                                onClick={() => this.handleStatusFilter(d.status)}
                                style={onClick ? { cursor: 'pointer' } : undefined}
                            />
                            <text
                                x={x + size}
                                y={y + size / 2 + (idx + 1) * size + 3}
                                fontFamily="Verdana; Helvetica; sans-serif"
                                fontWeight={selectedStatus === d.status ? 'bold' : undefined}
                                fontSize={size / 2}
                                onClick={() => this.handleStatusFilter(d.status)}
                                style={onClick ? { cursor: 'pointer' } : undefined}>
                                {d.status}: {d.value}
                            </text>
                        </g>
                    ))}
                </g>
                <g transform={`translate(${width / 1.5}, ${height / 1.5 - margin.top})`}>
                    <DonutChart
                        innerRadius={radius - 60}
                        outerRadius={radius - 100}
                        cornerRadius={3}
                        data={toChartData(data)}
                        onClick={(e) => this.handleStatusFilter(e.key as AnsibleStatus)}
                    />

                    <text textAnchor="middle" x="0" y="-10" fontSize="35">
                        {uniqueHosts}
                    </text>
                    <text textAnchor="middle" x="0" y="20" fontSize="20">
                        Total Hosts
                    </text>
                </g>
            </svg>
        );
    }
}

export default AnsibleStatChart;
