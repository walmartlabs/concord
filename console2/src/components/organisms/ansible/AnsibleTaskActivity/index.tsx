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
import { connect } from 'react-redux';
import { AnyAction, Dispatch } from 'redux';
import { Loader } from 'semantic-ui-react';

import { AnsibleEvent, AnsibleStatus } from '../../../../api/process/ansible';
import { ProcessEventEntry } from '../../../../api/process/event';
import { AnsibleTaskList, RequestErrorMessage } from '../../../molecules';
import { ConcordId, RequestError } from '../../../../api/common';
import { actions, AnsibleEvents, State } from '../../../../state/data/processes/events';
import { combinePrePostEvents } from '../../ProcessEventsActivity';

interface ExternalProps {
    instanceId: ConcordId;
    playbookId?: ConcordId;
    host?: string;
    hostGroup?: string;
    status?: AnsibleStatus;
    showHosts?: boolean;
}

interface StateProps {
    loading: boolean;
    events: Array<ProcessEventEntry<AnsibleEvent>>;
    error: RequestError;
}

interface DispatchProps {
    load: (
        instanceId: ConcordId,
        host?: string,
        hostGroup?: string,
        status?: AnsibleStatus,
        playbookId?: ConcordId
    ) => void;
}

type Props = ExternalProps & StateProps & DispatchProps;

class AnsibleTaskListActivity extends React.Component<Props> {
    componentDidMount() {
        this.init();
    }

    init() {
        const { instanceId, playbookId, host, hostGroup, status, load } = this.props;
        load(instanceId, host, hostGroup, status, playbookId);
    }

    render() {
        const { loading, error, host, events, showHosts } = this.props;

        if (error) {
            return <RequestErrorMessage error={error} />;
        }

        if (loading || !events) {
            return <Loader active={true} />;
        }

        if (events.length === 0) {
            return <h4>No failures detected.</h4>;
        }

        return <AnsibleTaskList title={host} showHosts={showHosts} tasks={events} />;
    }
}

const makeAnsibleEvents = (eventById: AnsibleEvents): Array<ProcessEventEntry<AnsibleEvent>> =>
    Object.keys(eventById)
        .map((k) => eventById[k])
        .filter((value) => value.data.status !== undefined)
        .sort((a, b) => (a.eventDate > b.eventDate ? 1 : a.eventDate < b.eventDate ? -1 : 0));

interface StateType {
    processes: {
        events: State;
    };
}

export const mapStateToProps = ({ processes: { events } }: StateType): StateProps => ({
    events: combinePrePostEvents(makeAnsibleEvents(events.ansibleEvents)) as Array<
        ProcessEventEntry<AnsibleEvent>
    >,
    loading: events.loading,
    error: events.error
});

const mapDispatchToProps = (dispatch: Dispatch<AnyAction>): DispatchProps => ({
    load: (instanceId, host, hostGroup, status, playbookId) =>
        dispatch(actions.listAnsibleEvents(instanceId, host, hostGroup, status, playbookId))
});

export default connect(mapStateToProps, mapDispatchToProps)(AnsibleTaskListActivity);
