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
import { Breadcrumb, Container, Header, Segment } from 'semantic-ui-react';

import { ConcordKey } from '../../../api/common';
import { BreadcrumbSegment } from '../../molecules';
import { EditRepositoryActivity } from '../../organisms';

interface RouteProps {
    orgName: ConcordKey;
    projectName: ConcordKey;
}

class AddRepositoryPage extends React.PureComponent<RouteComponentProps<RouteProps>> {
    render() {
        const { orgName, projectName } = this.props.match.params;

        return (
            <>
                <BreadcrumbSegment>
                    <Breadcrumb.Section>
                        <Link to={`/org/${orgName}/project/${projectName}/repository`}>
                            {projectName}
                        </Link>
                    </Breadcrumb.Section>
                    <Breadcrumb.Divider />
                    <Breadcrumb.Section active={true}>Add Repository</Breadcrumb.Section>
                </BreadcrumbSegment>

                <Segment basic={true}>
                    <Container text={true}>
                        <Header>
                            <Header.Content>Add a Repository</Header.Content>
                            <Header.Subheader>Register an existing GIT repository</Header.Subheader>
                        </Header>
                        <EditRepositoryActivity
                            orgName={orgName}
                            projectName={projectName}
                            forceRefresh={undefined}/>
                    </Container>
                </Segment>
            </>
        );
    }
}

export default withRouter(AddRepositoryPage);
