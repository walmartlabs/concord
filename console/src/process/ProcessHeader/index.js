/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2018 Wal-Mart Store, Inc.
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
import { Header, Breadcrumb } from 'semantic-ui-react';
import { Link } from 'react-router';

export class ProcessHeader extends React.Component {
    render() {
        const { instanceId, data } = this.props;

        return (
            <div>
                <Breadcrumb size="massive">
                    <Breadcrumb.Section>{data.orgName ? data.orgName : '-'}</Breadcrumb.Section>
                    <Breadcrumb.Divider icon="right chevron" />
                    <Breadcrumb.Section>
                        {data.projectName ? (
                            data.projectName === 'concordTriggers' ? (
                                <span>{data.projectName}</span>
                            ) : (
                                <Link to={`/project/${data.projectName}`}>{data.projectName}</Link>
                            )
                        ) : (
                            '-'
                        )}
                    </Breadcrumb.Section>
                    <Breadcrumb.Divider icon="right chevron" />
                    <Breadcrumb.Section active> {instanceId}</Breadcrumb.Section>
                </Breadcrumb>

                <Header as="h5">{data.tags ? data.tags.sort().join(', ') : ''}</Header>
            </div>
        );
    }
}
