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
import { Table, Loader } from 'semantic-ui-react';

import { ConcordId } from '../../../api/common';
import { actions, selectors } from '../../../state/data/processes/attachments';
import { State } from '../../../state/data/processes/attachments/types';

interface ExternalProps {
    instanceId: ConcordId;
}

interface StateProps {
    loading: boolean;
    data: string[];
}

interface DispatchProps {
    load: (instanceId: ConcordId) => void;
}

type Props = ExternalProps & StateProps & DispatchProps;

class ProcessAttachmentsActivity extends React.Component<Props> {
    componentDidMount() {
        this.init();
    }

    init() {
        const { instanceId, load } = this.props;
        load(instanceId);
    }

    renderTableHeader = () => {
        return (
            <Table.Row>
                <Table.HeaderCell collapsing={true}>Attached File(s)</Table.HeaderCell>
            </Table.Row>
        );
    };

    renderTableRow = (attachment: string) => {
        const { instanceId } = this.props;
        const attachmentName = attachment.substring(attachment.lastIndexOf('/'));
        return (
            <Table.Row>
                <Table.Cell>
                    <a
                        href={'/api/v1/process/' + instanceId + '/attachment/' + attachment}
                        download={attachmentName}>
                        {attachment}
                    </a>
                </Table.Cell>
            </Table.Row>
        );
    };

    render() {
        const { loading, data } = this.props;

        if (loading) {
            return <Loader active={true} />;
        }

        return (
            <>
                <Table celled={true} attached="bottom">
                    <Table.Header>{this.renderTableHeader()}</Table.Header>
                    <Table.Body>
                        {data.length > 0 && data.map((p) => this.renderTableRow(p))}
                    </Table.Body>
                </Table>

                {data.length === 0 && <h3>No attachments found.</h3>}
            </>
        );
    }
}

interface StateType {
    processes: {
        attachments: State;
    };
}

export const mapStateToProps = ({ processes: { attachments } }: StateType): StateProps => ({
    loading: attachments.list.running,
    data: selectors.processAttachments(attachments)
});

export const mapDispatchToProps = (dispatch: Dispatch<{}>): DispatchProps => ({
    load: (instanceId: ConcordId) => {
        dispatch(actions.listProcessAttachments(instanceId));
    }
});

export default connect(
    mapStateToProps,
    mapDispatchToProps
)(ProcessAttachmentsActivity);
