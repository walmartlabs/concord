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

import { UserProcessActivity } from '../../organisms';
import { BreadcrumbsToolbar } from '../../organisms';
import { Breadcrumb } from 'semantic-ui-react';
import { useCallback, useState } from 'react';
import { LoadingState } from '../../../App';

const UserActivityPage = () => {
    const loading = React.useContext(LoadingState);

    const [refresh, toggleRefresh] = useState<boolean>(false);

    const refreshHandler = useCallback(() => {
        toggleRefresh((prevState) => !prevState);
    }, []);

    return (
        <>
            <BreadcrumbsToolbar loading={loading} refreshHandler={refreshHandler}>
                <Breadcrumb.Section active={true}>Activity</Breadcrumb.Section>
            </BreadcrumbsToolbar>

            <UserProcessActivity forceRefresh={refresh} />
        </>
    );
};

export default UserActivityPage;
