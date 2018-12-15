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

import { ConcordId } from '../../../api/common';
import { ProcessWizard } from '../../organisms';

import './styles.css';

interface Props {
    instanceId: ConcordId;
}

class ProcessWizardPage extends React.PureComponent<RouteComponentProps<Props>> {
    render() {
        const { instanceId } = this.props.match.params;
        return (
            <div className="flexbox-container">
                <ProcessWizard processInstanceId={instanceId} />
            </div>
        );
    }
}

export default withRouter(ProcessWizardPage);
