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
import { Button, Divider, Header, Icon, Message } from 'semantic-ui-react';

import { ConcordId } from '../../../api/common';
import { canBeCancelled, ProcessEntry } from '../../../api/process';
import {
    ProcessElementEvent,
    ProcessEventEntry,
    ProcessEventType
} from '../../../api/process/event';
import { AnsibleEvent } from '../../../api/process/ansible';
import { actions, MAX_EVENT_COUNT } from '../../../state/data/processes/poll';
import { ProcessEvents, State } from '../../../state/data/processes/poll/types';
import { timestampDiffMs } from '../../../utils';
import { ProcessElementList } from '../../molecules';
import { CancelProcessPopup } from '../../organisms';

interface ExternalProps {
    instanceId: ConcordId;
}

interface StateProps {
    loading: boolean;
    process?: ProcessEntry;
    elementEvents: Array<ProcessEventEntry<ProcessElementEvent>>;
    tooMuchData?: boolean;
}

interface DispatchProps {
    startPolling: (forceLoadAll?: boolean) => void;
    stopPolling: () => void;
    refresh: () => void;
}

type Props = ExternalProps & StateProps & DispatchProps;

// TODO extract the AnsibleEventBrowser component
class ProcessEventsActivity extends React.Component<Props> {
    constructor(props: Props) {
        super(props);
        this.state = {};
    }

    componentDidMount() {
        this.props.startPolling();
    }

    componentWillUnmount() {
        this.props.stopPolling();
    }

    componentDidUpdate(prevProps: Props) {
        const { instanceId, startPolling, stopPolling } = this.props;
        if (instanceId !== prevProps.instanceId) {
            stopPolling();
            startPolling();
        }
    }

    createAdditionalAction() {
        const { process, refresh } = this.props;

        if (!process) {
            return;
        }

        if (!canBeCancelled(process.status)) {
            return;
        }

        return (
            <CancelProcessPopup
                instanceId={process.instanceId}
                refresh={refresh}
                trigger={(onClick: any) => (
                    <Button negative={true} icon="delete" content="Cancel" onClick={onClick} />
                )}
            />
        );
    }

    handleForceLoadAll(ev: React.SyntheticEvent) {
        ev.preventDefault();
        this.props.startPolling(true);
    }

    render() {
        const { loading, refresh, instanceId, process, elementEvents, tooMuchData } = this.props;

        // TODO replace the loading icon with something more visually pleasing
        return (
            <>
                <Header as="h3">
                    <Icon
                        disabled={loading}
                        name="refresh"
                        loading={loading}
                        onClick={() => refresh()}
                    />
                    {process && process.status}
                </Header>

                {tooMuchData && (
                    <Message warning={true}>
                        Looks like there's a lot of data. Only the first {MAX_EVENT_COUNT} events
                        were loaded. Click {/* eslint-disable-next-line jsx-a11y/anchor-is-valid */}
                        <a href="#" onClick={(ev) => this.handleForceLoadAll(ev)}>
                            here
                        </a>{' '}
                        to load all events. Note: it may take a while.
                    </Message>
                )}

                {process && elementEvents.length > 0 && (
                    <>
                        <Divider content="Process Events" horizontal={true} />
                        <ProcessElementList
                            instanceId={instanceId}
                            events={elementEvents}
                            processStatus={process.status}
                            tooMuchData={tooMuchData}
                        />
                    </>
                )}
            </>
        );
    }
}

interface StateType {
    processes: {
        poll: State;
    };
}

// TODO move to selectors?

// assumes that "eventDate" can be sorted lexicographically
const makeElementEvents = (
    eventById: ProcessEvents
): Array<ProcessEventEntry<ProcessElementEvent>> =>
    Object.keys(eventById)
        .map((k) => eventById[k])
        .filter((e) => e.eventType === ProcessEventType.ELEMENT)
        .map((e) => e as ProcessEventEntry<ProcessElementEvent>)
        .sort((a, b) => (a.eventDate > b.eventDate ? 1 : a.eventDate < b.eventDate ? -1 : 0));

export const combinePrePostEvents = (
    events: Array<ProcessEventEntry<AnsibleEvent | ProcessElementEvent>>
): Array<ProcessEventEntry<AnsibleEvent | ProcessElementEvent>> => {
    function findEvent(
        phase: string,
        correlationId: string
    ): ProcessEventEntry<AnsibleEvent | ProcessElementEvent> | undefined {
        return events.find(
            (value) => value.data.phase === phase && value.data.correlationId === correlationId
        );
    }

    const processed = {};
    const result = new Array<ProcessEventEntry<AnsibleEvent | ProcessElementEvent>>();
    events.forEach((event) => {
        const data = event.data;
        if (!data.correlationId) {
            result.push(event);
            return;
        }

        if (processed[data.correlationId]) {
            return;
        }

        processed[data.correlationId] = true;

        if (data.phase === 'pre' && data.correlationId) {
            const postEvent = findEvent('post', data.correlationId);
            if (postEvent) {
                const clone = { ...event };
                clone.data = postEvent.data;
                clone.duration = timestampDiffMs(postEvent.eventDate, event.eventDate);
                result.push(clone);
            } else {
                result.push(event);
            }
        } else if (data.phase === 'post' && data.correlationId) {
            const preEvent = findEvent('pre', data.correlationId);
            if (preEvent) {
                const clone = { ...preEvent };
                clone.data = event.data;
                clone.duration = timestampDiffMs(event.eventDate, preEvent.eventDate);
                result.push(clone);
            } else {
                result.push(event);
            }
        } else {
            result.push(event);
        }
    });

    return result;
};

export const mapStateToProps = ({ processes: { poll } }: StateType): StateProps => ({
    loading: poll.currentRequest.running,
    process: poll.currentRequest.response ? poll.currentRequest.response.process : undefined,
    elementEvents: combinePrePostEvents(makeElementEvents(poll.eventById)) as Array<
        ProcessEventEntry<ProcessElementEvent>
    >,
    tooMuchData: poll.currentRequest.response ? poll.currentRequest.response.tooMuchData : false
});

export const mapDispatchToProps = (
    dispatch: Dispatch<AnyAction>,
    { instanceId }: ExternalProps
): DispatchProps => ({
    startPolling: (forceLoadAll?: boolean) =>
        dispatch(actions.startProcessPolling(instanceId, forceLoadAll)),
    stopPolling: () => dispatch(actions.stopProcessPolling()),
    refresh: () => dispatch(actions.forcePoll())
});

export default connect(
    mapStateToProps,
    mapDispatchToProps
)(ProcessEventsActivity);
