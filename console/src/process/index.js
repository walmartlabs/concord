/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 Wal-Mart Store, Inc.
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
import React from 'react';
import PropTypes from 'prop-types';
import { connect } from 'react-redux';
import { Link } from 'react-router';
import { Button, Container, Divider, Loader, Table } from 'semantic-ui-react';
import { push as pushHistory } from 'react-router-redux';
import ErrorMessage from '../shared/ErrorMessage';
import RefreshButton from '../shared/RefreshButton';
import KillProcessPopup from './KillProcessPopup/index';
import { actions as modal } from '../shared/Modal';
import * as constants from './constants';
import * as actions from './actions';
import reducers, { selectors } from './reducers';
import sagas from './sagas';

import { ProcessHeader } from './ProcessHeader';
import { ProcessDetailTable } from './ProcessDetailTable';
import EventSummary from './EventSummary';

const enableFormsStatuses = [constants.status.suspendedStatus];

const renderRunAs = (v) => {
    if (!v) {
        return;
    }

    if (v.username) {
        return (
            <p>
                <b>Expects user:</b> {v.username}
            </p>
        );
    }

    if (v.ldap && v.ldap.group) {
        return (
            <p>
                <b>Expects group:</b> {v.ldap.group}
            </p>
        );
    }
};

class ProcessStatusPage extends React.Component {
    componentDidMount() {
        const { instanceId, startPollingWatcherFn } = this.props;
        this.load();
        startPollingWatcherFn(instanceId);
    }

    componentDidUpdate(prevProps) {
        const { instanceId } = this.props;
        if (prevProps.instanceId !== instanceId) {
            this.load();
        }
    }

    load() {
        const { instanceId, loadData } = this.props;
        loadData(instanceId);
    }

    openLog() {
        const { instanceId, openLog } = this.props;
        openLog(instanceId);
    }

    openWizard() {
        const { instanceId, openWizard } = this.props;
        openWizard(instanceId);
    }

    render() {
        const { instanceId, data, loading, error, openKillPopup } = this.props;

        if (loading || !data) {
            return <Loader active />;
        }

        if (error) {
            return <ErrorMessage message={error} retryFn={() => this.load()} />;
        }

        const enableLogButton = constants.hasLogStatuses.includes(data.status) && data.logFileName;
        const showKillButton = constants.canBeKilledStatuses.includes(data.status);
        const showStateDownload = constants.hasProcessState.includes(data.status);
        const showForms =
            data.forms && data.forms.length > 0 && enableFormsStatuses.includes(data.status);
        const showWizard = showForms;

        return (
            <Container>
                <ProcessHeader
                    instanceId={instanceId}
                    data={data}
                    renderRefreshButton={() => (
                        <RefreshButton
                            loading={loading}
                            onClick={() => this.load()}
                            style={{ float: 'right' }}
                        />
                    )}
                />

                <Divider horizontal section>
                    Process Details
                </Divider>

                <ProcessDetailTable
                    attached="top"
                    data={data}
                    renderActionButtons={() => (
                        <Button.Group>
                            <Button disabled={!enableLogButton} onClick={() => this.openLog()}>
                                View Log
                            </Button>
                            {showWizard && (
                                <Button onClick={() => this.openWizard()}>Wizard</Button>
                            )}
                            {showStateDownload && (
                                <Button
                                    icon="download"
                                    color="blue"
                                    content="State"
                                    href={`/api/v1/process/${instanceId}/state/snapshot`}
                                    download={`Concord_${data.status}_${instanceId}.zip`}
                                />
                            )}
                            {showKillButton && (
                                <Button
                                    icon="delete"
                                    color="red"
                                    content="Cancel"
                                    onClick={() => openKillPopup(instanceId)}
                                />
                            )}
                        </Button.Group>
                    )}
                />

                {showForms && (
                    <div>
                        <Divider horizontal section>
                            Required Actions
                        </Divider>
                        <Table>
                            <Table.Header>
                                <Table.Row>
                                    <Table.HeaderCell>Action</Table.HeaderCell>
                                    <Table.HeaderCell>Description</Table.HeaderCell>
                                </Table.Row>
                            </Table.Header>
                            <Table.Body>
                                {data.forms.map(({ formInstanceId, name, runAs }) => (
                                    <Table.Row key={formInstanceId}>
                                        <Table.Cell>
                                            <Link
                                                to={`/process/${instanceId}/form/${formInstanceId}`}>
                                                {name}
                                            </Link>
                                        </Table.Cell>
                                        <Table.Cell>
                                            Form
                                            {renderRunAs(runAs)}
                                        </Table.Cell>
                                    </Table.Row>
                                ))}
                            </Table.Body>
                        </Table>
                    </div>
                )}

                <EventSummary instanceId={instanceId} />
            </Container>
        );
    }
}

ProcessStatusPage.propTypes = {
    instanceId: PropTypes.string.isRequired,
    data: PropTypes.object,
    loading: PropTypes.bool,
    error: PropTypes.string,
    loadData: PropTypes.func.isRequired,
    openLog: PropTypes.func.isRequired,
    openKillPopup: PropTypes.func.isRequired,
    openWizard: PropTypes.func.isRequired
};

const mapStateToProps = ({ process }, { params: { instanceId } }) => ({
    instanceId,
    data: selectors.getData(process),
    loading: selectors.isLoading(process),
    error: selectors.getError(process)
});

const mapDispatchToProps = (dispatch) => ({
    loadData: (instanceId) => dispatch(actions.load(instanceId)),
    openLog: (instanceId) => dispatch(pushHistory(`/process/${instanceId}/log`)),
    openKillPopup: (instanceId) => {
        // reload the data when a process is killed
        const onSuccess = [actions.load(instanceId)];
        dispatch(modal.open(KillProcessPopup.MODAL_TYPE, { instanceId, onSuccess }));
    },
    openWizard: (instanceId) => dispatch(pushHistory(`/process/${instanceId}/wizard`)),
    startPollingWatcherFn: (instanceId) => dispatch(actions.startPollingWatcher(instanceId))
});

export default connect(mapStateToProps, mapDispatchToProps)(ProcessStatusPage);

export { actions, reducers, sagas };
