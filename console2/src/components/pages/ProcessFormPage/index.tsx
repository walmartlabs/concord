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
import { RouteComponentProps, withRouter } from 'react-router';
import { Link } from 'react-router-dom';
import { Breadcrumb } from 'semantic-ui-react';

import { ConcordId } from '../../../api/common';
import { BreadcrumbSegment } from '../../molecules';
import { ProcessFormActivity } from '../../organisms';

interface Props {
    processInstanceId: ConcordId;
    formInstanceId: string;
    mode?: 'step' | 'wizard';
}

class ProcessFormPage extends React.PureComponent<RouteComponentProps<Props>> {
    render() {
        const { processInstanceId, formInstanceId, mode } = this.props.match.params;

        return (
            <>
                <BreadcrumbSegment>
                    <Breadcrumb.Section>
                        <Link to={`/process`}>Processes</Link>
                    </Breadcrumb.Section>
                    <Breadcrumb.Divider />
                    <Breadcrumb.Section>
                        <Link to={`/process/${processInstanceId}`}>{processInstanceId}</Link>
                    </Breadcrumb.Section>
                    <Breadcrumb.Divider />
                    <Breadcrumb.Section active={true}>Form</Breadcrumb.Section>
                </BreadcrumbSegment>

                <ProcessFormActivity
                    processInstanceId={processInstanceId}
                    formInstanceId={formInstanceId}
                    wizard={mode === 'wizard'}
                />
            </>
        );
    }
}

export default withRouter(ProcessFormPage);
