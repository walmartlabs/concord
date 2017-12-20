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
import React from "react";
import {connect} from "react-redux";
import {Button, Modal, TextArea} from "semantic-ui-react";
import {CopyToClipboard} from "react-copy-to-clipboard";

import ErrorMessage from "../../../shared/ErrorMessage";
import {actions as modal} from "../../../shared/Modal";
import {selectors} from "./effects";

import "./styles.css";

export const MODAL_TYPE = "SHOW_SECRET_PUBLIC_KEY_POPUP";

const showSecretPublicKey = ({open, orgName, name, error, publicKey, onCloseFn, inFlightFn}) => {
    const inFlight = inFlightFn(orgName, name);

    return <Modal open={open} dimmer="inverted">
        <Modal.Header>{name}</Modal.Header>
        <Modal.Content>
            {error && <ErrorMessage message={error}/>}
            {publicKey && <div>
                <b>Public key: </b>
                <CopyToClipboard text={publicKey}>
                    <Button icon="copy" size="mini" basic/>
                </CopyToClipboard>
                <TextArea id="publicKeyValue" className="secretData">{publicKey}</TextArea>
            </div>}
        </Modal.Content>
        <Modal.Actions>
            <Button disabled={inFlight} onClick={onCloseFn}>Close</Button>
        </Modal.Actions>
    </Modal>;
};

showSecretPublicKey.MODAL_TYPE = MODAL_TYPE;

const mapStateToProps = ({secretList}) => ({
    inFlightFn: (orgName, name) => selectors.isInFlight(secretList, orgName, name),
    error: selectors.getPublicKeyError(secretList),
    publicKey: selectors.getPublicKey(secretList)
});

const mapDispatchToProps = (dispatch) => ({
    onCloseFn: () => dispatch(modal.close())
});

export default connect(mapStateToProps, mapDispatchToProps)(showSecretPublicKey);
