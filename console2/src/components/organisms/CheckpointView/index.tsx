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
import React, { useContext, FunctionComponent } from 'react';
import CheckpointErrorBoundary from './CheckpointErrorBoundry';
import CheckpointContainer from './Container';
import ActionBar from './ActionBar';
import LeftContent from './ProcessList/LeftContent';
import { Row } from './shared/Layout';
import RightContent from './ProcessList/RightContent';
import { ProjectEntry } from '../../../api/org/project';

/**
 * This View renders the two bigger components that make up the this checkpoint view
 *
 * @Component ActionBar contains refresh, filter, and pagination elements
 * @Component Map over process details to create list of process items and details
 */
export const View = () => {
    const { project, processes } = useContext(CheckpointContainer.Context);

    return (
        <CheckpointErrorBoundary>
            <ActionBar />

            {processes &&
                processes.map((process) => {
                    return (
                        <Row key={process.instanceId}>
                            <LeftContent project={project} process={process} />
                            <RightContent process={process} />
                        </Row>
                    );
                })}
        </CheckpointErrorBoundary>
    );
};

/**
 * Renders Context Providers for the Checkpoint View to consume
 * @param project the Concord Project
 */
export const CheckpointView: FunctionComponent<{ project: ProjectEntry }> = ({ project }) => (
    <CheckpointContainer.Provider project={project} refreshInterval={5000}>
        <View />
    </CheckpointContainer.Provider>
);

export default CheckpointView;
