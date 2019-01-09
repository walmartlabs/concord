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
import { distanceInWords, distanceInWordsToNow } from 'date-fns';
import * as React from 'react';
import { Link } from 'react-router-dom';
import { Divider } from 'semantic-ui-react';

import { ProcessEntryEx } from '../../../../api/service/console';
import { Truncate } from '../../../atoms';
import { AppDrawer as LogDrawer, ProcessLogs as LogContainer } from '../../../molecules';
import { Label, Status } from '../shared/Labels';
import { ListItem } from './ListItem';
import { LeftWrap } from './styles';

interface Props {
    process: ProcessEntryEx;
}

export default ({ process }: Props) => (
    <LogContainer key={process.instanceId}>
        {({ setActiveProcess, fetchLog }) => (
            <LeftWrap maxWidth={245}>
                <ListItem>
                    <div>
                        <Label>
                            Process:{' '}
                            <Link to={`/process/${process.instanceId}`}>
                                <Truncate text={process.instanceId} />
                            </Link>
                        </Label>
                    </div>
                    <div>
                        <Label>Repo: </Label>
                        <Link
                            to={`/org/${process.orgName}/project/${
                                process.projectName
                            }/repository/${process.repoName}`}>
                            {process.repoName}
                        </Link>
                    </div>
                    <div>
                        <Label>
                            <a
                                style={{ cursor: 'pointer' }}
                                onClick={(ev) => {
                                    fetchLog(process.instanceId);
                                    setActiveProcess(process.instanceId);
                                }}>
                                <LogDrawer.Show>View Log</LogDrawer.Show>
                            </a>
                        </Label>
                    </div>
                    <Divider />
                    <div>
                        <Label>Current Status: </Label>
                        <Status>{process.status}</Status>
                    </div>
                    <div>
                        <Label>Last update: </Label>
                        <Status>{distanceInWordsToNow(new Date(process.lastUpdatedAt))}</Status>
                    </div>
                    <div>
                        <Label>Run duration: </Label>
                        {/* TODO: find last runtime start instead created at */}
                        {distanceInWords(
                            new Date(process.lastUpdatedAt),
                            new Date(process.createdAt)
                        )}
                    </div>
                </ListItem>
            </LeftWrap>
        )}
    </LogContainer>
);
