/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2018 Wal-Mart Store, Inc.
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
// @ts-check

import React from 'react';
import Pie from './Pie';
import { Group } from '@vx/group';
import { Legend } from './Legend';
import * as _ from 'lodash';

function Label({ x, y, children }) {
    return (
        <text fill="black" textAnchor="middle" x={x} y={y} dy=".33em" fontSize={12}>
            {children}
        </text>
    );
}

export class EventPieChart extends React.Component {
    constructor(props) {
        super(props);
        this.state = {
            allHosts: [],
            statData: null
        };
    }

    formatAnsibleStats = (ansibleStats) => {
        const propsArray = [];
        _.forIn(_.keysIn(ansibleStats.data), (value, index) => {
            if (value !== 'exitCode') {
                propsArray.push({
                    key: index,
                    value: _.toUpper(value),
                    count: ansibleStats.data[value].length,
                    data: ansibleStats.data[value]
                });
            }
        });
        return propsArray;
    };

    getStatusColor(status) {
        switch (status) {
            case 'OK':
                return '#5DB571';
            case 'CHANGED':
                return '#00A4D3';
            case 'FAILURES':
                return '#EC6357';
            case 'UNREACHABLE':
                return '#BDB9B9';
            case 'SKIPPED':
                return '#F6BC32';
            default:
                return '#3F3F3D';
        }
    }

    getUniqHosts = (ansibleStats) => {
        let uniqHosts = [];
        _.forIn(_.keysIn(ansibleStats.data), (value, index) => {
            if (value !== 'exitCode') {
                uniqHosts = _.uniq(uniqHosts.concat(ansibleStats.data[value]));
            }
        });
        return uniqHosts;
    };

    render() {
        const {
            width,
            height,
            margin = {
                top: 40,
                left: 20,
                right: 20,
                bottom: 110
            },
            ansibleStats,
            setHostListFn
        } = this.props;

        if (width < 10) return null;
        const radius = Math.min(width, height) / 1.5;

        const statData = this.formatAnsibleStats(ansibleStats);

        return (
            <svg width={width} height={height}>
                <Group top={height / 1.5 - margin.top} left={width / 1.5}>
                    {ansibleStats && (
                        <Pie
                            data={statData}
                            pieValue={(d) => d.count}
                            outerRadius={radius - 60}
                            innerRadius={radius - 100}
                            fill={(d) => {
                                return this.getStatusColor(d.data.value);
                            }}
                            cornerRadius={3}
                            padAngle={0}
                            arcClick={(arc) => {
                                setHostListFn(arc.data.data);
                            }}
                            centroid={(centroid, arc) => {
                                const [x, y] = centroid;
                                const { startAngle, endAngle } = arc;
                                if (endAngle - startAngle < 0.1) return null;
                                return (
                                    <Label x={x} y={y}>
                                        {arc.data.count}
                                    </Label>
                                );
                            }}
                        />
                    )}
                    <text textAnchor="middle" x="0" y="-10" fontSize="35">
                        {this.getUniqHosts(ansibleStats).length}
                    </text>
                    <text textAnchor="middle" x="0" y="20" fontSize="20">
                        Total Hosts
                    </text>
                </Group>
                <Legend
                    x={10}
                    y={10}
                    size={30}
                    statData={statData}
                    style={{ cursor: 'pointer' }}
                    labelClick={(obj) => {
                        setHostListFn(obj.data);
                    }}
                />
            </svg>
        );
    }
}

export default EventPieChart;
