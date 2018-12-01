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
import { connect, Dispatch } from 'react-redux';
import { Divider } from 'semantic-ui-react';

import { ConcordId } from '../../../api/common';
import { AnsibleHost, AnsibleStatsEntry, SearchFilter } from '../../../api/process/ansible';
import { AnsibleStats } from '../../molecules';
import { State as AnsibleState } from '../../../state/data/processes/ansible/types';
import { actions, selectors } from '../../../state/data/processes/ansible';

interface ExternalProps {
    instanceId: ConcordId;
}

interface StateProps {
    ansibleStats: AnsibleStatsEntry;
    ansibleHosts: AnsibleHost[];
    next?: number;
    prev?: number;
}

interface DispatchProps {
    refreshHosts: (instanceId: ConcordId, filters: SearchFilter) => void;
}

type Props = ExternalProps & StateProps & DispatchProps;

class AnsibleStatsActivity extends React.Component<Props> {
    constructor(props: Props) {
        super(props);
        this.state = {};
    }

    refresh(filter: SearchFilter) {
        const { refreshHosts, instanceId } = this.props;
        refreshHosts(instanceId, filter);
    }

    render() {
        const { instanceId, ansibleStats, ansibleHosts, next, prev } = this.props;

        return (
            <>
                {ansibleStats.uniqueHosts > 0 && (
                    <>
                        <Divider content="Ansible Stats" horizontal={true} />
                        <AnsibleStats
                            instanceId={instanceId}
                            hosts={ansibleHosts}
                            stats={ansibleStats}
                            next={next}
                            prev={prev}
                            refresh={(filter) => this.refresh(filter)}
                        />
                    </>
                )}
            </>
        );
    }
}

interface StateType {
    processes: {
        ansible: AnsibleState;
    };
}

export const mapStateToProps = ({ processes: { ansible } }: StateType): StateProps => ({
    ansibleStats: selectors.ansibleStats(ansible),
    ansibleHosts: selectors.ansibleHosts(ansible),
    next: selectors.ansibleHostsNext(ansible),
    prev: selectors.ansibleHostsPrev(ansible)
});

const mapDispatchToProps = (dispatch: Dispatch<{}>): DispatchProps => ({
    refreshHosts: (instanceId, filter) => {
        dispatch(actions.listAnsibleHosts(instanceId, filter));
    }
});

export default connect(
    mapStateToProps,
    mapDispatchToProps
)(AnsibleStatsActivity);
