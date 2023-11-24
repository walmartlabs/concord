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
import { ProcessActivity } from '../../organisms';
import { TabLink } from '../../organisms/ProcessActivity';

interface Props {
    instanceId: ConcordId;
}

const pathToTab = (s: string): TabLink => {
    if (s.endsWith('/status')) {
        return 'status';
    } else if (s.endsWith('/ansible')) {
        return 'ansible';
    } else if (s.endsWith('/log')) {
        return 'log';
    } else if (s.endsWith('/log2')) {
        return 'log2';
    } else if (s.endsWith('/log3')) {
        return 'log3';
    } else if (s.endsWith('/history')) {
        return 'history';
    } else if (s.endsWith('/children')) {
        return 'children';
    } else if (s.endsWith('/attachments')) {
        return 'attachments';
    } else if (s.endsWith('/events')) {
        return 'events';
    } else if (s.endsWith('/wait')) {
        return 'wait';
    }

    return null;
};

class ProcessPage extends React.PureComponent<RouteComponentProps<Props>> {
    render() {
        const { instanceId } = this.props.match.params;

        const activeTab = pathToTab(this.props.location.pathname);

        return (
            <>
                <ProcessActivity instanceId={instanceId} activeTab={activeTab} />
            </>
        );
    }
}

export default withRouter(ProcessPage);
