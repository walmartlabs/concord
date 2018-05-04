import * as React from 'react';
import ReactJson from 'react-json-view';
import { Header, Table } from 'semantic-ui-react';

import { AnsibleEvent, AnsibleStatus, ProcessEventEntry } from '../../../api/process/event';
import { LocalTimestamp } from '../../molecules';

interface Props {
    title?: string;
    showHosts?: boolean;
    events: Array<ProcessEventEntry<AnsibleEvent>>;
}

class AnsibleTaskList extends React.PureComponent<Props> {
    render() {
        const { title, showHosts, events } = this.props;

        return (
            <>
                {title && (
                    <Header as="h3" attached="top">
                        {title}
                    </Header>
                )}
                <Table celled={true} attached="bottom">
                    <Table.Header>
                        <Table.Row>
                            {showHosts && <Table.HeaderCell>Host</Table.HeaderCell>}
                            <Table.HeaderCell>Ansible Task</Table.HeaderCell>
                            <Table.HeaderCell>Status</Table.HeaderCell>
                            <Table.HeaderCell>Event Time</Table.HeaderCell>
                            <Table.HeaderCell>Results</Table.HeaderCell>
                            <Table.HeaderCell>Playbook</Table.HeaderCell>
                        </Table.Row>
                    </Table.Header>

                    <Table.Body>
                        {events &&
                            events.map((value, index) => {
                                return (
                                    <Table.Row
                                        key={index}
                                        error={value.data.status === AnsibleStatus.FAILED}
                                        positive={value.data.status === AnsibleStatus.OK}
                                        warning={value.data.status === AnsibleStatus.UNREACHABLE}>
                                        {showHosts && <Table.Cell>{value.data.host}</Table.Cell>}
                                        <Table.Cell>{value.data.task}</Table.Cell>
                                        <Table.Cell>{value.data.status}</Table.Cell>
                                        <Table.Cell>
                                            <LocalTimestamp value={value.eventDate} />
                                        </Table.Cell>
                                        <Table.Cell>
                                            <ReactJson
                                                src={value.data}
                                                collapsed={true}
                                                name={null}
                                                enableClipboard={false}
                                            />
                                        </Table.Cell>
                                        <Table.Cell>{value.data.playbook}</Table.Cell>
                                    </Table.Row>
                                );
                            })}
                    </Table.Body>
                </Table>
            </>
        );
    }
}

export default AnsibleTaskList;
