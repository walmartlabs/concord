/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2023 Walmart Inc.
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

import './styles.css';
import {
    FAILED_COLOR_HEX,
    OK_COLOR_HEX,
    RUNNING_COLOR_HEX,
    SKIPPED_COLOR_HEX,
    UNREACHABLE_COLOR_HEX
} from './types';

export interface ProgressStats {
    failed: number;
    ok: number;
    unreachable: number;
    skipped: number;
    running: number;
}

export interface ProgressProps {
    total: number;
    stats: ProgressStats;
}

class TaskProgress extends React.PureComponent<ProgressProps> {
    render() {
        const { failed, ok, unreachable, skipped, running } = this.props.stats;
        const { total } = this.props;

        if (total <= 0) {
            return <div />;
        }

        const t = 100 / total;
        const current = failed + ok + unreachable + skipped + running;
        const unknown = total > current ? total - current : 0;
        return (
            <div
                className="progress"
                style={{
                    width: '400px',
                    height: '20px',
                    position: 'relative',
                    overflow: 'hidden'
                }}>
                <div
                    className="ok"
                    style={{
                        backgroundColor: OK_COLOR_HEX,
                        position: 'absolute',
                        left: 0,
                        width: '100%',
                        paddingLeft: '5px',
                        color: '#FFFFFF'
                    }}>
                    {ok}
                </div>
                <div
                    className="failed"
                    style={{
                        backgroundColor: FAILED_COLOR_HEX,
                        position: 'absolute',
                        left: t * ok + '%',
                        width: '100%',
                        paddingLeft: '5px',
                        color: '#FFFFFF'
                    }}>
                    {failed}
                </div>
                <div
                    className="unreachable"
                    style={{
                        backgroundColor: UNREACHABLE_COLOR_HEX,
                        position: 'absolute',
                        left: t * ok + t * failed + '%',
                        width: '100%',
                        paddingLeft: '5px',
                        color: '#FFFFFF'
                    }}>
                    {unreachable}
                </div>
                <div
                    className="skipped"
                    style={{
                        backgroundColor: SKIPPED_COLOR_HEX,
                        position: 'absolute',
                        left: t * ok + t * failed + t * unreachable + '%',
                        width: '100%',
                        paddingLeft: '5px',
                        color: '#FFFFFF'
                    }}>
                    {skipped}
                </div>
                <div
                    className="running"
                    style={{
                        backgroundColor: RUNNING_COLOR_HEX,
                        position: 'absolute',
                        left: t * ok + t * failed + t * unreachable + t * skipped + '%',
                        width: '100%',
                        paddingLeft: '5px',
                        color: '#00000099',
                        textShadow: '1px 1px #FFFFFF'
                    }}>
                    {running}
                </div>
                {unknown > 0 && (
                    <div
                        className="unknown"
                        style={{
                            backgroundColor: 'white',
                            position: 'absolute',
                            left:
                                t * ok +
                                t * failed +
                                t * unreachable +
                                t * skipped +
                                t * running +
                                '%',
                            width: '100%',
                            paddingLeft: '5px',
                            color: '#656565'
                        }}>
                        &nbsp;
                    </div>
                )}
            </div>
        );
    }
}

export default TaskProgress;
