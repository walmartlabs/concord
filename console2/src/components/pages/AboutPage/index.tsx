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
import { Breadcrumb } from 'semantic-ui-react';

import { BreadcrumbSegment } from '../../molecules';
import { ServerVersion } from '../../organisms';

class AboutPage extends React.PureComponent {
    render() {
        return (
            <>
                <BreadcrumbSegment>
                    <BreadcrumbSegment>
                        <Breadcrumb.Section active={true}>About</Breadcrumb.Section>
                    </BreadcrumbSegment>
                </BreadcrumbSegment>

                <p>
                    Server version: <ServerVersion />
                </p>
                <p>Console version: {import.meta.env.VITE_CONCORD_VERSION || 'n/a'}</p>
                <p>Last updated: {window.concord.lastUpdated || 'n/a'}</p>
            </>
        );
    }
}

export default AboutPage;
