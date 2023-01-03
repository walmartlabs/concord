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
import { Link } from 'react-router-dom';
import { Breadcrumb } from 'semantic-ui-react';
import { NewTeamActivity } from '../../organisms';
import { OrgActivityPage } from '../../templates';

export default () => (
    <OrgActivityPage
        title="Create a New Team"
        breadcrumbs={(orgName) => (
            <>
                <Breadcrumb.Section>
                    <Link to={`/org/${orgName}/team`}>{orgName}</Link>
                </Breadcrumb.Section>
                <Breadcrumb.Divider />
                <Breadcrumb.Section active={true}>New Team</Breadcrumb.Section>
            </>
        )}
        activity={(orgName) => <NewTeamActivity orgName={orgName} />}
    />
);
