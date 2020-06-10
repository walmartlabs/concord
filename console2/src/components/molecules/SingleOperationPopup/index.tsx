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
import { Button, Header, Modal, Icon, SemanticICONS, SemanticCOLORS } from 'semantic-ui-react';

import { RequestError, RequestErrorData } from '../../../api/common';
import { RequestErrorMessage } from '../../molecules';

interface State {
    open: boolean;
}

export type OnClickFn = () => void;

interface Props {
    trigger: (onClick: OnClickFn) => React.ReactNode;

    title: string;
    introMsg: React.ReactNode;
    icon?: SemanticICONS;
    iconColor?: SemanticCOLORS;
    customStyle?: object; // CSS Object

    customNo?: string;
    customYes?: string;

    running: boolean;
    runningMsg?: React.ReactNode;

    success: boolean;
    successMsg?: React.ReactNode;

    error?: RequestError;
    errorRenderer?: (error: RequestErrorData) => React.ReactNode;

    reset?: () => void;
    onConfirm: () => void;
    onDone?: () => void;
    onOpen?: () => void;

    onDoneElements?: () => React.ReactNode;

    disableYes?: boolean;
}

class SingleOperationPopup extends React.Component<Props, State> {
    constructor(props: Props) {
        super(props);
        this.state = { open: false };

        this.handleOpen = this.handleOpen.bind(this);
        this.handleClose = this.handleClose.bind(this);
        this.handleConfirm = this.handleConfirm.bind(this);
        this.renderContent = this.renderContent.bind(this);
        this.renderActions = this.renderActions.bind(this);
        this.stopPropagation = this.stopPropagation.bind(this);
    }

    handleOpen(event?: React.SyntheticEvent) {
        this.stopPropagation(event);
        this.props.reset && this.props.reset();
        this.setState({ open: true });
    }

    handleClose() {
        this.setState({ open: false });
    }

    handleConfirm() {
        this.props.onConfirm();
    }

    renderContent() {
        const {
            success,
            successMsg,
            error,
            errorRenderer,
            running,
            runningMsg,
            introMsg
        } = this.props;

        if (success) {
            return successMsg ? successMsg : <p>The operation was completed successfully.</p>;
        }

        if (error) {
            if (errorRenderer) {
                return errorRenderer(error);
            }
            return <RequestErrorMessage error={error} />;
        }

        if (running) {
            return runningMsg ? runningMsg : <p>Processing the request...</p>;
        }

        return introMsg;
    }

    renderActions() {
        const {
            success,
            error,
            running,
            onDone,
            onDoneElements,
            customNo,
            customYes,
            disableYes = false
        } = this.props;

        if (success) {
            return (
                <>
                    {onDoneElements && onDoneElements()}
                    <Button
                        color="green"
                        onClick={() => {
                            this.handleClose();
                            if (onDone) {
                                onDone();
                            }
                        }}>
                        Done
                    </Button>
                </>
            );
        }

        if (error) {
            return (
                <>
                    <Button onClick={() => this.handleClose()}>Close</Button>
                    <Button onClick={() => this.handleConfirm()}>Retry</Button>
                </>
            );
        }

        return (
            <>
                <Button basic={true} disabled={running} onClick={() => this.handleClose()}>
                    {customNo || 'No'}
                </Button>
                <Button
                    color="blue"
                    loading={running}
                    onClick={() => this.handleConfirm()}
                    disabled={disableYes}>
                    {customYes || 'Yes'}
                </Button>
            </>
        );
    }

    stopPropagation(event?: React.SyntheticEvent) {
        if (event) {
            event.stopPropagation();
        }
    }

    render() {
        const { onOpen, trigger, title, icon, iconColor, customStyle = {} } = this.props;

        return (
            <Modal
                onClick={this.stopPropagation}
                onClose={this.stopPropagation}
                onOpen={onOpen}
                style={customStyle}
                open={this.state.open}
                dimmer="inverted"
                trigger={trigger(() => this.handleOpen())}>
                {/* TODO: Header padding CSS */}
                <Header>
                    <Icon name={icon} color={iconColor || 'black'} />
                    <Header.Content>{title}</Header.Content>
                </Header>

                <Modal.Content>{this.renderContent()}</Modal.Content>
                <Modal.Actions>{this.renderActions()}</Modal.Actions>
            </Modal>
        );
    }
}

export default SingleOperationPopup;
