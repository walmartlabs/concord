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
import { TeamActivity } from '../../organisms';
import { TabLink } from '../../organisms/TeamActivity';

interface RouteProps {
    orgName: ConcordId;
    teamName: ConcordId;
}

const pathToTab = (s: string): TabLink => {
    if (s.endsWith('/members')) {
        return 'members';
    } else if (s.endsWith('/settings')) {
        return 'settings';
    }

    return null;
};

class TeamPage extends React.PureComponent<RouteComponentProps<RouteProps>> {
    render() {
        const { orgName, teamName } = this.props.match.params;

        const activeTab = pathToTab(this.props.location.pathname);

        return (
            <>
                <BreadcrumbSegment>
                    <Breadcrumb.Section>
                        <Link to={`/org/${orgName}/team`}>{orgName}</Link>
                    </Breadcrumb.Section>
                    <Breadcrumb.Divider />
                    <Breadcrumb.Section active={true}>{teamName}</Breadcrumb.Section>
                </BreadcrumbSegment>

                <TeamActivity orgName={orgName} teamName={teamName} activeTab={activeTab} />
            </>
        );
    }
}

export default withRouter(TeamPage);
