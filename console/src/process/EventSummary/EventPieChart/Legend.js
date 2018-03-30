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
import * as React from 'react';
import * as constants from '../constants';

export class Legend extends React.Component {
    render() {
        const { x, y, size, statData, labelClick } = this.props;

        if (statData) {
            return (
                <g>
                    {statData.map((obj, index) => {
                        if (obj.data.length > 0) {
                            return (
                                <g
                                    key={`${index}-g`}
                                    style={{ cursor: 'pointer' }}
                                    onClick={() => labelClick(obj)}>
                                    <rect
                                        key={index}
                                        x={x}
                                        y={y + (index + 1) * size}
                                        width={size * 0.8}
                                        height={size * 0.8}
                                        fill={constants.statusColors[statData[index].value]}
                                        rx="7"
                                        ry="7"
                                    />
                                    <text
                                        key={`${index}-text`}
                                        x={x + size}
                                        y={y + size / 2 + (index + 1) * size + 3}
                                        fontFamily="Verdana"
                                        fontSize={size / 2}>
                                        {obj.value}: {obj.data.length}
                                    </text>
                                </g>
                            );
                        } else {
                            return null;
                        }
                    })}
                </g>
            );
        } else {
            return null;
        }
    }
}
