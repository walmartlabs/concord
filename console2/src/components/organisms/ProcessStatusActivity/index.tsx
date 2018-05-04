import * as React from 'react';
import { connect, Dispatch } from 'react-redux';
import { push as pushHistory } from 'react-router-redux';
import { Divider, Header, Icon } from 'semantic-ui-react';

import { ConcordId } from '../../../api/common';
import { hasState, ProcessEntry } from '../../../api/process';
import {
    AnsibleEvent,
    AnsibleStatus,
    ProcessEventEntry,
    ProcessEventType
} from '../../../api/process/event';
import { FormListEntry } from '../../../api/process/form';
import { actions } from '../../../state/data/processes/poll';
import { ProcessEvents, State } from '../../../state/data/processes/poll/types';
import {
    AnsibleStats,
    ProcessActionList,
    ProcessElementList,
    ProcessStatusTable
} from '../../molecules';

interface OwnState {
    selectedStatus?: AnsibleStatus;
}

interface ExternalProps {
    instanceId: ConcordId;
}

interface StateProps {
    loading: boolean;
    process?: ProcessEntry;
    forms: FormListEntry[];
    events: Array<ProcessEventEntry<{}>>;
    ansibleEvents: Array<ProcessEventEntry<AnsibleEvent>>;
}

interface DispatchProps {
    startPolling: () => void;
    stopPolling: () => void;
    refresh: () => void;
    startWizard: () => void;
}

type Props = ExternalProps & StateProps & DispatchProps;

// TODO extract the AnsibleEventBrowser component
class ProcessStatusActivity extends React.Component<Props, OwnState> {
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

    render() {
        const {
            loading,
            refresh,
            startWizard,
            instanceId,
            process,
            forms,
            events,
            ansibleEvents
        } = this.props;

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

                {process && (
                    <>
                        <Divider content="Process Details" horizontal={true} />
                        <ProcessStatusTable
                            data={process}
                            onOpenWizard={forms.length > 0 ? startWizard : undefined}
                            showStateDownload={hasState(process.status)}
                        />
                    </>
                )}

                {forms.length > 0 && (
                    <>
                        <Divider content="Required Actions" horizontal={true} />
                        <ProcessActionList instanceId={instanceId} forms={forms} />
                    </>
                )}

                {events.length > 0 && (
                    <>
                        <Divider content="Flow Events" horizontal={true} />
                        <ProcessElementList events={events} />
                    </>
                )}

                {ansibleEvents.length > 0 && (
                    <>
                        <Divider content="Ansible Stats" horizontal={true} />
                        <AnsibleStats events={ansibleEvents} />
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
const makeEventList = (eventById: ProcessEvents): Array<ProcessEventEntry<{}>> =>
    Object.keys(eventById)
        .map((k) => eventById[k])
        .sort((a, b) => (a.eventDate > b.eventDate ? 1 : a.eventDate < b.eventDate ? -1 : 0));

const filterAnsibleEvents = (eventById: ProcessEvents): Array<ProcessEventEntry<AnsibleEvent>> =>
    Object.keys(eventById)
        .map((k) => eventById[k])
        .filter((e) => e.eventType === ProcessEventType.ANSIBLE)
        .map((e) => e as ProcessEventEntry<AnsibleEvent>)
        .filter(({ data }) => !!data.host);

export const mapStateToProps = ({ processes: { poll } }: StateType): StateProps => ({
    loading: poll.currentRequest.running,
    process: poll.currentRequest.response ? poll.currentRequest.response.process : undefined,
    forms: poll.forms,
    events: makeEventList(poll.eventById),
    ansibleEvents: filterAnsibleEvents(poll.eventById)
});

export const mapDispatchToProps = (
    dispatch: Dispatch<{}>,
    { instanceId }: ExternalProps
): DispatchProps => ({
    startPolling: () => dispatch(actions.startProcessPolling(instanceId)),
    stopPolling: () => dispatch(actions.stopProcessPolling()),
    refresh: () => dispatch(actions.forcePoll()),
    startWizard: () => dispatch(pushHistory(`/process/${instanceId}/wizard?fullScreen=true`))
});

export default connect(mapStateToProps, mapDispatchToProps)(ProcessStatusActivity);
