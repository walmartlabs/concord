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
import React, { Component } from 'react';
import PropTypes from 'prop-types';
import { connect } from 'react-redux';
import { Button, Header, Popup, Icon } from 'semantic-ui-react';
import KillProcessPopup from '../KillProcessPopup';
import RefreshButton from '../../shared/RefreshButton';
import ErrorMessage from '../../shared/ErrorMessage';
import { actions as modal } from '../../shared/Modal';
import DataItem from '../../shared/DataItem';
import * as api from './api';
import * as constants from '../constants';
import ProcessTable from '../ProcessTable';

const { actions, reducers, selectors, sagas } = DataItem('process/queue', [], api.loadData);

class QueuePage extends Component {
    componentDidMount() {
        this.load();
    }

    load() {
        const { loadData } = this.props;
        loadData();
    }

    render() {
        const { loading, error, data, killPopupFn } = this.props;

        if (error) {
            return <ErrorMessage message={error} retryFn={() => this.load()} />;
        } else {
            return (
                <div>
                    <Header as="h3">
                        <RefreshButton loading={loading} onClick={() => this.load()} /> Queue (all
                        available organizations)
                    </Header>
                    <ProcessTable
                        processes={data}
                        celled={false}
                        listProject={true}
                        renderActions={(status, instanceId) =>
                            constants.canBeKilledStatuses.includes(status) && (
                                <Popup
                                    trigger={
                                        <Icon name="ellipsis horizontal" size="large" link={true} />
                                    }
                                    content={
                                        <Button
                                            color="red"
                                            content="Cancel Process"
                                            onClick={() => killPopupFn(instanceId)}
                                        />
                                    }
                                    inverted={true}
                                    on="click"
                                    position="top left"
                                />
                            )
                        }
                    />
                </div>
            );
        }
    }
}

QueuePage.propTypes = {
    loading: PropTypes.bool,
    error: PropTypes.any,
    data: PropTypes.array,
    killPopupFn: PropTypes.func
};

const mapStateToProps = ({ queue }) => ({
    loading: selectors.isLoading(queue),
    error: selectors.getError(queue),
    data: selectors.getData(queue)
});

const mapDispatchToProps = (dispatch) => ({
    loadData: () => dispatch(actions.loadData()),
    killPopupFn: (instanceId) => {
        // reload the table when a process is killed
        const onSuccess = [actions.loadData()];
        dispatch(modal.open(KillProcessPopup.MODAL_TYPE, { instanceId, onSuccess }));
    }
});

export default connect(mapStateToProps, mapDispatchToProps)(QueuePage);

export { reducers, sagas };
