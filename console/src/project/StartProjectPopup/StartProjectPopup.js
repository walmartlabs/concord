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
import { connect } from 'react-redux';
import { push as pushHistory } from 'react-router-redux';
import { Button, Form, Modal } from 'semantic-ui-react';
import { actions as modal } from '../../shared/Modal';
import {
  actions as projectActions,
  selectors as projectStartSelectors
} from '../StartProjectPopup';
import './styles.css';

export const MODAL_TYPE = 'START_PROJECT_POPUP';

class StartProjectPopup extends Component {
  render() {
    const {
      open,
      onCloseFn,
      onConfirmFn,
      onProcessStateFn,
      repositoryId,
      repositoryName
    } = this.props;
    const { startResult: result, startLoading } = this.props;

    const startProjectFn = (ev) => {
      ev.preventDefault();
      onConfirmFn(repositoryId);
    };

    const showProcessStatusButton = result && result.instanceId;
    const showCloseButton = result && true;
    const closeButtonColor = result && result.error ? 'grey' : 'green';

    return (
      <Modal open={open} dimmer="inverted">
        <Modal.Header>{result ? 'Process result' : 'Start a new process?'}</Modal.Header>
        <Modal.Content>
          <Modal.Description>
            {result ? (
              <Form>
                <div className="projectStartResultBox">{JSON.stringify(result, null, 2)}</div>
              </Form>
            ) : (
              <p>
                Are you sure you want to start a new process using '<b>{repositoryName}</b>'
                repository?
              </p>
            )}
          </Modal.Description>
        </Modal.Content>
        <Modal.Actions>
          <Button
            color="red"
            disabled={startLoading}
            onClick={onCloseFn}
            className={result !== null ? 'hidden' : ''}>
            No
          </Button>
          <Button
            color="green"
            loading={startLoading}
            onClick={startProjectFn}
            className={result !== null ? 'hidden' : ''}>
            Yes
          </Button>

          {showProcessStatusButton && (
            <Button color="grey" onClick={() => onProcessStateFn(result.instanceId)}>
              Open the process status
            </Button>
          )}
          {showCloseButton && (
            <Button color={closeButtonColor} onClick={onCloseFn}>
              Close
            </Button>
          )}
        </Modal.Actions>
      </Modal>
    );
  }
}

StartProjectPopup.MODAL_TYPE = MODAL_TYPE;

const mapStateToProps = (state) => ({
  startResult: projectStartSelectors.getResult(state.projectStart),
  startLoading: projectStartSelectors.isLoading(state.projectStart)
});

const mapDispatchToProps = (dispatch) => ({
  onCloseFn: () => {
    dispatch(modal.close());
    dispatch(projectActions.resetStart());
  },
  onConfirmFn: (repositoryId) => dispatch(projectActions.startProject(repositoryId)),
  onProcessStateFn: (instanceId) => {
    dispatch(modal.close());
    dispatch(projectActions.resetStart());
    dispatch(pushHistory(`/process/${instanceId}`));
  }
});

export default connect(mapStateToProps, mapDispatchToProps)(StartProjectPopup);
