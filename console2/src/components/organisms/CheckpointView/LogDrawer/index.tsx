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
import LogContainer from '../../../molecules/ProcessLogs';
import { CheckpointName } from '../shared/Labels';
import { Link } from 'react-router-dom';
import Truncate from '../../../atoms/Truncate';
import FileFromBlob from '../../../atoms/LogFileFromBlob';
import AppDrawer from '../../../molecules/AppDrawer';
import ClassIcon from '../../../atoms/ClassIcon';

export default () => (
    <AppDrawer align="right" id="AppDrawer">
        {/* // TODO: Log Container needs to refresh / stream the latest log details in */}
        <LogContainer>
            {({ getActiveProcess, getActiveAnchor, logsByProcessId, queueScrollTo }) => (
                <div style={{ width: '100%', height: '100%' }}>
                    <AppDrawer.Toggle>
                        <div style={{ cursor: 'pointer' }}>
                            <ClassIcon classes="large grey close icon" />
                            Close
                        </div>
                    </AppDrawer.Toggle>
                    <br />
                    <div>
                        <CheckpointName>Process:</CheckpointName>{' '}
                        <Link to={`/process/${getActiveProcess()}`}>
                            <Truncate text={getActiveProcess()} />
                        </Link>
                    </div>
                    <div>
                        <CheckpointName>Checkpoint: </CheckpointName>
                        <a
                            onClick={() => {
                                queueScrollTo(getActiveAnchor());
                            }}
                            style={{
                                cursor: 'pointer',
                                textAlign: 'right'
                            }}>
                            {getActiveAnchor()}
                        </a>
                    </div>

                    {logsByProcessId[getActiveProcess()] ? (
                        <FileFromBlob
                            blobUrl={logsByProcessId[getActiveProcess()].url!}
                            activeHighlight={getActiveAnchor()}
                        />
                    ) : null}
                </div>
            )}
        </LogContainer>
    </AppDrawer>
);
