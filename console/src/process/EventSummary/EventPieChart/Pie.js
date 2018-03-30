// @ts-check

// Adapted from VX lib's Pie Component
// https://github.com/hshoff/vx/blob/master/packages/vx-shape/src/shapes/Pie.js

import React from 'react';

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
import cx from 'classnames';
import { Group } from '@vx/group';
import { arc as d3Arc, pie as d3Pie } from 'd3-shape';

const callOrValue = (maybeFn, data) => {
    if (typeof maybeFn === 'function') {
        return maybeFn(data);
    }
    return maybeFn;
};

const additionalProps = (restProps, data) => {
    return Object.keys(restProps).reduce((ret, cur) => {
        ret[cur] = callOrValue(restProps[cur], data);
        return ret;
    }, {});
};

export default class Pie extends React.Component {
    render() {
        const {
            className = '',
            top = 0,
            left = 0,
            data,
            centroid,
            innerRadius = 0,
            outerRadius,
            cornerRadius,
            endAngle,
            padAngle,
            padRadius,
            pieSort,
            pieValue,
            arcClick,
            ...restProps
        } = this.props;

        const path = d3Arc();
        path.innerRadius(innerRadius);
        if (outerRadius) path.outerRadius(outerRadius);
        if (cornerRadius) path.cornerRadius(cornerRadius);
        if (padRadius) path.padRadius(padRadius);
        const pie = d3Pie();
        if (pieSort) pie.sort(null);
        if (pieValue) pie.value(pieValue);
        if (padAngle) pie.padAngle(padAngle);
        const arcs = pie(data);

        return (
            <Group className="vx-pie-arcs-group" top={top} left={left}>
                {arcs.map((arc, i) => {
                    let c;
                    if (centroid) c = path.centroid(arc);
                    return (
                        <g key={`pie-arc-${i}`}>
                            <path
                                style={{ cursor: 'pointer' }}
                                className={cx('vx-pie-arc', className)}
                                d={path(arc)}
                                onClick={() => arcClick(arc)}
                                {...additionalProps(restProps, {
                                    ...arc,
                                    index: i,
                                    centroid: c
                                })}
                            />
                            {centroid && centroid(c, arc)}
                        </g>
                    );
                })}
            </Group>
        );
    }
}
