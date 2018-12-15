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

import { BreadcrumbSegment } from '../../molecules';
import { TabLink } from '../../organisms/OrganizationActivity';
import { OrganizationActivity } from '../../organisms';

interface RouteProps {
    orgName: string;
}

const pathToTab = (s: string): TabLink => {
    if (s.endsWith('/process')) {
        return 'process';
    } else if (s.endsWith('/project')) {
        return 'project';
    } else if (s.endsWith('/secret')) {
        return 'secret';
    } else if (s.endsWith('/team')) {
        return 'team';
    } else if (s.endsWith('/settings')) {
        return 'settings';
    }

    return null;
};

class OrganizationPage extends React.PureComponent<RouteComponentProps<RouteProps>> {
    render() {
        const { orgName } = this.props.match.params;

        const activeTab = pathToTab(this.props.location.pathname);

        return (
            <>
                <BreadcrumbSegment>
                    <Breadcrumb.Section>
                        <Link to="/org">Organizations</Link>
                    </Breadcrumb.Section>
                    <Breadcrumb.Divider />
                    <Breadcrumb.Section active={true}>{orgName}</Breadcrumb.Section>
                </BreadcrumbSegment>

                <OrganizationActivity activeTab={activeTab} orgName={orgName} />
            </>
        );
    }
}

export default withRouter(OrganizationPage);
