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
import { RouteComponentProps } from 'react-router';

import { TabLink } from '../../organisms/OrganizationActivity';
import { OrganizationActivity } from '../../organisms';
import { LoadingState } from '../../../App';
import { useCallback, useState } from 'react';
import { BreadcrumbsToolbar } from '../../organisms';
import { Breadcrumb } from 'semantic-ui-react';
import { Link } from 'react-router-dom';

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
    } else if (s.endsWith('/jsonstore')) {
        return 'jsonstore';
    } else if (s.endsWith('/settings')) {
        return 'settings';
    } else if (s.endsWith('/audit')) {
        return 'audit';
    }

    return null;
};

const OrganizationPage = (props: RouteComponentProps<RouteProps>) => {
    const activeTab = pathToTab(props.location.pathname);

    const { orgName } = props.match.params;

    const loading = React.useContext(LoadingState);

    const [refresh, toggleRefresh] = useState<boolean>(false);

    const refreshHandler = useCallback(() => {
        toggleRefresh((prevState) => !prevState);
    }, []);

    return (
        <>
            <BreadcrumbsToolbar loading={loading} refreshHandler={refreshHandler}>
                <Breadcrumb.Section>
                    <Link to="/org">Organizations</Link>
                </Breadcrumb.Section>
                <Breadcrumb.Divider />
                <Breadcrumb.Section active={true}>{orgName}</Breadcrumb.Section>
            </BreadcrumbsToolbar>

            <OrganizationActivity activeTab={activeTab} orgName={orgName} forceRefresh={refresh} />
        </>
    );
};

export default OrganizationPage;
