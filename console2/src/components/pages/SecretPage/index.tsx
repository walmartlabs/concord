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
import { RouteComponentProps } from 'react-router';
import { Link } from 'react-router-dom';
import { Breadcrumb } from 'semantic-ui-react';

import { ConcordKey } from '../../../api/common';
import { SecretActivity } from '../../organisms';
import { TabLink } from '../../organisms/SecretActivity';
import { BreadcrumbsToolbar } from '../../organisms';
import { LoadingState } from '../../../App';
import { useCallback, useState } from 'react';

interface RouteProps {
    orgName: ConcordKey;
    secretName: ConcordKey;
}

const pathToTab = (s: string): TabLink => {
    if (s.endsWith('/info')) {
        return 'info';
    } else if (s.endsWith('/settings')) {
        return 'settings';
    } else if (s.endsWith('/access')) {
        return 'access';
    } else if (s.endsWith('/audit')) {
        return 'audit';
    }

    return null;
};

const SecretPage = (props: RouteComponentProps<RouteProps>) => {
    const loading = React.useContext(LoadingState);

    const { orgName, secretName } = props.match.params;
    const activeTab = pathToTab(props.location.pathname);

    const [refresh, toggleRefresh] = useState<boolean>(false);

    const refreshHandler = useCallback(() => {
        toggleRefresh((prevState) => !prevState);
    }, []);

    return (
        <>
            <BreadcrumbsToolbar loading={loading} refreshHandler={refreshHandler}>
                <Breadcrumb.Section>
                    <Link to={`/org/${orgName}/secret`}>{orgName}</Link>
                </Breadcrumb.Section>
                <Breadcrumb.Divider />
                <Breadcrumb.Section active={true}>{secretName}</Breadcrumb.Section>
            </BreadcrumbsToolbar>

            <SecretActivity
                orgName={orgName}
                secretName={secretName}
                activeTab={activeTab}
                forceRefresh={refresh}
            />
        </>
    );
};

export default SecretPage;
