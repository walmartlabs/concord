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

import * as React from 'react';
import { ConcordKey, queryParams } from '../../../api/common';
import { Link } from 'react-router-dom';
import { ProjectProcesses } from '../../../api/service/console/user';
import { RedirectButton } from '../../organisms';
import { ProcessStatus } from '../../../api/process';

export const MAX_CARD_ITEMS = 5;

export interface OrgProjects {
    orgName: ConcordKey;
    projects: ProjectProcesses[];
}

interface Props {
    items?: OrgProjects[];
}

const renderProject = (orgName: ConcordKey, project: ProjectProcesses) => {
    return (
        <div className="item" key={project.projectName}>
            <div className="right floated content">{project.running}</div>
            <div className="content ellipsis">
                <Link
                    to={`/org/${orgName}/project/${project.projectName}/process?${queryParams({
                        status: ProcessStatus.RUNNING
                    })}`}>
                    {project.projectName}
                </Link>
            </div>
        </div>
    );
};

const renderCard = (orgName: ConcordKey, projects: ProjectProcesses[]) => {
    return (
        <div className="card" key={orgName}>
            <div className="extra content">
                <div className="header ellipsis">
                    <Link
                        className="ui"
                        to={`/org/${orgName}/process?${queryParams({
                            status: ProcessStatus.RUNNING
                        })}`}>
                        {orgName}
                    </Link>
                </div>
            </div>
            <div className="content">
                <div className="ui relaxed divided list">
                    {projects.slice(0, MAX_CARD_ITEMS).map((p) => renderProject(orgName, p))}
                </div>
            </div>
            {projects.length > MAX_CARD_ITEMS && (
                <RedirectButton
                    className="ui bottom attached"
                    content="Show more"
                    location={`/org/${orgName}/process?${queryParams({
                        status: ProcessStatus.RUNNING
                    })}`}
                />
            )}
        </div>
    );
};

class UserProcessByOrgCards extends React.PureComponent<Props> {
    render() {
        const { items } = this.props;
        if (items === undefined) {
            return 'Loading';
        }

        if (items.length === 0) {
            return 'No processes found.';
        }

        return (
            <div className="ui cards">{items.map((i) => renderCard(i.orgName, i.projects))}</div>
        );
    }
}

export default UserProcessByOrgCards;
