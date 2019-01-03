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

import LogContainer from '../../molecules/ProcessLogs';
import CheckpointLogDrawer from './LogDrawer';

import { Row } from './shared/Layout';

import ActionBar from './ActionBar';
import LeftContent from './ProcessList/LeftContent';
import RightContent from './ProcessList/RightContent';

interface Props {
    processes: any[];
    checkpointGroups: any;
    pollDataFn?: () => void;
    pollInterval?: number;
}

class CheckpointView extends React.Component<Props, { activeId: string }> {
    onPollInterval: any = undefined;

    constructor(props: Props) {
        super(props);
    }

    componentDidMount() {
        const { pollDataFn, pollInterval = 5000 } = this.props;

        if (pollDataFn) {
            pollDataFn(); // Fetch Initial Dataset
            this.onPollInterval = setInterval(() => {
                pollDataFn(); // Refresh Data
            }, pollInterval);
        }
    }

    componentWillUnmount() {
        clearInterval(this.onPollInterval);
    }

    // TODO: Replace with something better
    UNSAFE_componentWillReceiveProps(nextProps: Props) {
        const { pollDataFn, pollInterval } = nextProps;

        clearInterval(this.onPollInterval);

        if (pollDataFn) {
            this.onPollInterval = setInterval(() => {
                pollDataFn();
            }, pollInterval);
        }
    }

    render() {
        const { processes, checkpointGroups } = this.props;
        if (processes) {
            const processArray: any[] = Object.keys(processes).map((key) => {
                return processes[key];
            });

            return (
                <>
                    <CheckpointLogDrawer />
                    <ActionBar />
                    <div>
                        {processArray.map((process) => {
                            return (
                                <LogContainer key={process.instanceId}>
                                    {() => (
                                        <Row>
                                            <LeftContent process={process} />
                                            <RightContent
                                                process={process}
                                                checkpointGroups={checkpointGroups}
                                            />
                                        </Row>
                                    )}
                                </LogContainer>
                            );
                        })}
                    </div>
                </>
            );
        } else {
            // Loading state
            return null;
        }
    }
}

export default CheckpointView;
