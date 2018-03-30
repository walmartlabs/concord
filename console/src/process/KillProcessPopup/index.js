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
import { connect } from 'react-redux';
import { Button, Modal } from 'semantic-ui-react';
import { actions as modal } from '../../shared/Modal';
import * as actions from '../actions';
import { selectors } from '../reducers';

export const MODAL_TYPE = 'KILL_PROCESS_POPUP';

export const KillProcessPopup = ({
    open,
    instanceId,
    onSuccess,
    onCloseFn,
    onConfirmFn,
    inFlightFn
}) => {
    const inFlight = inFlightFn(instanceId);

    return (
        <Modal open={open}>
            <Modal.Header>Cancel the selected process?</Modal.Header>
            <Modal.Content>Are you sure you want to cancel the selected process?</Modal.Content>
            <Modal.Actions>
                <Button color="green" disabled={inFlight} onClick={onCloseFn}>
                    No
                </Button>
                <Button
                    color="red"
                    loading={inFlight}
                    onClick={() => onConfirmFn(instanceId, onSuccess)}>
                    Yes
                </Button>
            </Modal.Actions>
        </Modal>
    );
};

KillProcessPopup.MODAL_TYPE = MODAL_TYPE;

const mapStateToProps = (state) => ({
    inFlightFn: (instanceId) => selectors.isInFlight(state.process, instanceId)
});

const mapDispatchToProps = (dispatch) => ({
    onCloseFn: () => dispatch(modal.close()),
    onConfirmFn: (instanceId, onSuccess) => {
        if (!onSuccess) {
            onSuccess = [];
        }

        // first, we need to close the dialog
        onSuccess.unshift(modal.close());

        dispatch(actions.kill(instanceId, onSuccess));
    }
});

export default connect(mapStateToProps, mapDispatchToProps)(KillProcessPopup);
