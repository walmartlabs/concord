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
import { Arc, arc as d3Arc, DefaultArcObject, pie as d3Pie, PieArcDatum } from 'd3';

export interface ChartEntry {
    key: {};
    value: number;
    color: string;
}

interface Props {
    innerRadius: number;
    outerRadius: number;
    cornerRadius?: number;
    padAngle?: number;
    data: ChartEntry[];
    onClick?: (entry: ChartEntry) => void;
}

const renderLabel = (arc: Arc<{}, DefaultArcObject>, o: DefaultArcObject, value: number) => {
    if (o.endAngle - o.startAngle < 0.1) {
        return;
    }

    const c = arc.centroid(o);
    return (
        <text x={c[0]} y={c[1]} color="black" textAnchor="middle" fontSize={12}>
            {value}
        </text>
    );
};

const toArcObject = (
    { startAngle, endAngle, padAngle }: PieArcDatum<ChartEntry>,
    innerRadius: number,
    outerRadius: number
): DefaultArcObject => ({
    startAngle,
    endAngle,
    innerRadius,
    outerRadius,
    padAngle
});

const renderArc = (
    arc: Arc<any, DefaultArcObject>,
    a: PieArcDatum<ChartEntry>,
    idx: number,
    onClick?: (entry: ChartEntry) => void
) => {
    const o = toArcObject(a, 5, 5);
    return (
        <g key={idx}>
            <path
                d={arc(o)!}
                fill={a.data.color}
                onClick={onClick ? () => onClick(a.data) : undefined}
                style={onClick ? { cursor: 'pointer' } : undefined}
            />
            {renderLabel(arc, o, a.value)}
        </g>
    );
};

class DonutChart extends React.PureComponent<Props> {
    render() {
        const { innerRadius, outerRadius, cornerRadius = 0, padAngle, data, onClick } = this.props;

        const arc = d3Arc()
            .innerRadius(innerRadius)
            .outerRadius(outerRadius)
            .cornerRadius(cornerRadius)
            .padAngle(padAngle);

        const pie = d3Pie<ChartEntry>().value((d) => d.value);

        return pie(data).map((a, idx) => renderArc(arc, a, idx, onClick));
    }
}

export default DonutChart;
