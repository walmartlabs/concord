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
import React, {Component} from "react";
import {connect} from "react-redux";
import {Button, Message, Modal} from "semantic-ui-react";
import {actions as modal} from "../../shared/Modal";

import * as actions from "./actions";
import reducers, * as selectors from "./reducers";
import sagas from "./sagas";

export const MODAL_TYPE = "REFRESH_REPOSITORY_POPUP";

class RefreshRepositoryPopup extends Component {

    render() {
        const {open, onCloseFn, onConfirmFn} = this.props;
        const {orgName, projectName, repositoryName} = this.props;
        const {refreshError, refreshResult, refreshing} = this.props;

        const refreshFn = (ev) => {
            ev.preventDefault();
            onConfirmFn(orgName, projectName, repositoryName);
        };

        return <Modal open={open} dimmer="inverted">
            <Modal.Header>Refreshing {repositoryName}...</Modal.Header>
            <Modal.Content>
                <Modal.Description>
                    {refreshError ?
                        <Message negative>{refreshError}</Message> :
                        (refreshResult ?
                            <p>The repository will be refreshed momentarily.</p>
                            :
                            <p>Refreshing the repository will update the Concord's cache and reload the project's
                                trigger definitions.</p>)}
                </Modal.Description>
            </Modal.Content>
            <Modal.Actions>
                <Button onClick={onCloseFn}>Close</Button>
                {!refreshResult && <Button color="green" loading={refreshing} onClick={refreshFn}>Refresh</Button>}
            </Modal.Actions>
        </Modal>;
    }
}

RefreshRepositoryPopup.MODAL_TYPE = MODAL_TYPE;

const mapStateToProps = ({repositoryRefresh}) => ({
    refreshError: selectors.getRefreshError(repositoryRefresh),
    refreshResult: selectors.getRefreshResult(repositoryRefresh),
    refreshing: selectors.isRefreshing(repositoryRefresh)
});

const mapDispatchToProps = (dispatch) => ({
    onCloseFn: () => {
        dispatch(modal.close());
        dispatch(actions.resetRefresh());
    },
    onConfirmFn: (orgName, projectName, repositoryName) => {
        dispatch(actions.refreshRepository(orgName, projectName, repositoryName));
    }
});

export default connect(mapStateToProps, mapDispatchToProps)(RefreshRepositoryPopup);

export {actions, selectors, reducers, sagas};
