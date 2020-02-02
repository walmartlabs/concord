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
import { RouteComponentProps, withRouter } from 'react-router';
import { Link } from 'react-router-dom';
import { Breadcrumb } from 'semantic-ui-react';

import { ConcordId } from '../../../api/common';
import { BreadcrumbSegment } from '../../molecules';
import { ProjectActivity } from '../../organisms';
import { TabLink } from '../../organisms/ProjectActivity';

interface RouteProps {
    orgName: ConcordId;
    projectName: ConcordId;
}

const pathToTab = (s: string): TabLink => {
    if (s.endsWith('/process')) {
        return 'process';
    } else if (s.endsWith('/checkpoint')) {
        return 'checkpoint';
    } else if (s.endsWith('/repository')) {
        return 'repository';
    } else if (s.endsWith('/settings')) {
        return 'settings';
    } else if (s.endsWith('/access')) {
        return 'access';
    } else if (s.endsWith('/configuration')) {
        return 'configuration';
    } else if (s.endsWith('/audit')) {
        return 'audit';
    }

    return null;
};

class ProjectPage extends React.PureComponent<RouteComponentProps<RouteProps>> {
    render() {
        const { orgName, projectName } = this.props.match.params;

        const activeTab = pathToTab(this.props.location.pathname);

        return (
            <>
                <BreadcrumbSegment>
                    <Breadcrumb.Section>
                        <Link to={`/org/${orgName}`}>{orgName}</Link>
                    </Breadcrumb.Section>
                    <Breadcrumb.Divider />
                    <Breadcrumb.Section active={true}>{projectName}</Breadcrumb.Section>
                </BreadcrumbSegment>

                <ProjectActivity
                    activeTab={activeTab}
                    orgName={orgName}
                    projectName={projectName}
                />
            </>
        );
    }
}

export default withRouter(ProjectPage);
