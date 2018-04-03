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
import { connect } from 'react-redux';
import { selectors } from '../../reducers';
import { Icon, Table, Divider } from 'semantic-ui-react';
import { formatTimestamp } from '../../util';

export class ConnectedEventList extends React.Component {
    render() {
        const { events, eventsByType } = this.props;

        if (events.length > 0) {
            return (
                <div>
                    <Divider horizontal section>
                        Flow Events
                    </Divider>

                    <Table celled definition={true}>
                        <Table.Header>
                            <Table.Row>
                                <Table.HeaderCell />
                                <Table.HeaderCell>Step</Table.HeaderCell>
                                <Table.HeaderCell>
                                    <Icon name="time" />
                                    Timestamp
                                </Table.HeaderCell>
                                <Table.HeaderCell>Line</Table.HeaderCell>
                                <Table.HeaderCell>Col</Table.HeaderCell>
                            </Table.Row>
                        </Table.Header>
                        <Table.Body>
                            {eventsByType.ELEMENT &&
                                eventsByType.ELEMENT.map((value, index, arr) => {
                                    return (
                                        <Table.Row key={index}>
                                            <Table.Cell>
                                                {index !== 0 &&
                                                arr[index - 1].data.processDefinitionId ===
                                                    value.data.processDefinitionId ? null : (
                                                    <div>{value.data.processDefinitionId}</div>
                                                )}
                                            </Table.Cell>
                                            <Table.Cell>{value.data.description}</Table.Cell>
                                            <Table.Cell>
                                                {index !== 0 &&
                                                formatTimestamp(arr[index - 1].eventDate) ===
                                                    formatTimestamp(value.eventDate) ? (
                                                    <div>(same)</div>
                                                ) : (
                                                    <div>{formatTimestamp(value.eventDate)}</div>
                                                )}
                                            </Table.Cell>
                                            <Table.Cell>{value.data.line}</Table.Cell>
                                            <Table.Cell>{value.data.column}</Table.Cell>
                                        </Table.Row>
                                    );
                                })}
                        </Table.Body>
                    </Table>
                </div>
            );
        } else {
            return null;
        }
    }
}

const mapStateToProps = ({ process }) => ({
    events: selectors.getEvents(process),
    eventsByType: selectors.getEventsByType(process)
});

const mapDispatchToProps = (dispatch) => ({});

export default connect(mapStateToProps, mapDispatchToProps)(ConnectedEventList);
