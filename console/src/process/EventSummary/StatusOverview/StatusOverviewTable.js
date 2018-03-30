import * as React from 'react';
import { Table } from 'semantic-ui-react';
import { formatTimestamp } from '../../util';
import * as _ from 'lodash';
import ReactJson from 'react-json-view';

export class StatusOverviewTable extends React.Component {
    render() {
        const { eventsByStatus } = this.props;

        const groupByTaskName = _.groupBy(eventsByStatus, (d) => d.data.task);

        if (eventsByStatus) {
            return (
                <div>
                    <Table celled definition={true}>
                        <Table.Header>
                            <Table.Row>
                                <Table.HeaderCell />
                                <Table.HeaderCell>Host</Table.HeaderCell>
                                <Table.HeaderCell>Event Time</Table.HeaderCell>
                                <Table.HeaderCell>Results</Table.HeaderCell>
                                <Table.HeaderCell>Playbook</Table.HeaderCell>
                            </Table.Row>
                        </Table.Header>
                        {Object.keys(groupByTaskName).map((key, index) => (
                            <Table.Body key={index}>
                                {groupByTaskName[key].map((value, index, arr) => {
                                    return (
                                        <Table.Row key={index}>
                                            <Table.Cell>
                                                {index !== 0 &&
                                                arr[index - 1].data.task ===
                                                    value.data.task ? null : (
                                                    <div>{key}</div>
                                                )}
                                            </Table.Cell>

                                            <Table.Cell>{value.data.host}</Table.Cell>
                                            <Table.Cell>
                                                {formatTimestamp(value.eventDate)}
                                            </Table.Cell>
                                            <Table.Cell>
                                                <ReactJson
                                                    src={value.data.result}
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
                        ))}
                    </Table>
                </div>
            );
        } else {
            return null;
        }
    }
}

export default StatusOverviewTable;
