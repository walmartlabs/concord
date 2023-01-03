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
import { RouteComponentProps, withRouter } from 'react-router';
import { Container, Header, Segment } from 'semantic-ui-react';

import { ConcordKey } from '../../../api/common';
import { BreadcrumbSegment } from '../../molecules';

interface ExternalProps {
    title: string;
    breadcrumbs: (orgName: ConcordKey) => React.ReactNode;
    activity: (orgName: ConcordKey) => React.ReactNode;
}

interface RouteProps {
    orgName: ConcordKey;
}

type Props = ExternalProps & RouteComponentProps<RouteProps>;

// TODO: Make a generic layout rather than page specific?
class OrgActivityPage extends React.PureComponent<Props> {
    render() {
        const { title, breadcrumbs, activity } = this.props;
        const { orgName } = this.props.match.params;

        return (
            <>
                <BreadcrumbSegment>{breadcrumbs(orgName)}</BreadcrumbSegment>

                <Segment basic={true}>
                    <Container text={true}>
                        <Header>{title}</Header>
                        {activity(orgName)}
                    </Container>
                </Segment>
            </>
        );
    }
}

export default withRouter(OrgActivityPage);
