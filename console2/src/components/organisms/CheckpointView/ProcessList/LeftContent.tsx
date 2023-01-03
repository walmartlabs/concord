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
import { formatDistanceToNow, parseISO as parseDate } from 'date-fns';
import React from 'react';
import { Link } from 'react-router-dom';
import { Divider, Icon } from 'semantic-ui-react';
import { ProjectEntry, ProjectEntryMeta } from '../../../../api/org/project';

import { ProcessEntry } from '../../../../api/process';
import { Truncate } from '../../../atoms';
import { Label, Status } from '../shared/Labels';
import { LeftWrap, ListItem } from './styles';

interface Props {
    project: ProjectEntry;
    process: ProcessEntry;
}

const renderMeta = (projectMeta?: ProjectEntryMeta, processMeta?: {}) => {
    if (!projectMeta || !projectMeta.ui || !projectMeta.ui.processList) {
        return;
    }

    // here we're going to render only `meta.` variables
    return projectMeta.ui.processList
        .filter((i) => i.source && i.source.startsWith('meta.'))
        .map((i, idx) => {
            const k = i.source.substr(5); // cut off the `meta.` prefix
            const v = processMeta ? (processMeta[k] ? processMeta[k] : 'n/a') : 'n/a';

            return (
                <div key={idx}>
                    <Label>{i.caption ? i.caption : i.source}:</Label> {v}
                </div>
            );
        });
};

export default ({ project, process }: Props) => {
    return (
        <LeftWrap maxWidth={300}>
            <ListItem>
                <div>
                    <Label>
                        Process:{' '}
                        <Link to={`/process/${process.instanceId}`}>
                            <Truncate text={process.instanceId} />
                        </Link>
                    </Label>
                </div>

                {/* Show repository name if it exists */}
                {process.repoName && (
                    <div>
                        <Label>Repo: </Label>
                        <Link
                            to={`/org/${process.orgName}/project/${process.projectName}/repository/${process.repoName}`}>
                            {process.repoName}
                        </Link>
                    </div>
                )}

                {renderMeta(project.meta, process.meta)}

                <Divider />
                <div>
                    <Label>Current Status: </Label>
                    <Status>{process.status}</Status>
                </div>

                <div>
                    <Label>Enable: </Label>
                    <Icon name="power" color={process.disabled ? 'grey' : 'green'} />
                </div>

                {/* Last update time, tooltip on mouse hover */}
                <div title={process.lastUpdatedAt}>
                    <Label>Last update: </Label>
                    <Status>{formatDistanceToNow(parseDate(process.lastUpdatedAt))}</Status>
                </div>
            </ListItem>
        </LeftWrap>
    );
};
