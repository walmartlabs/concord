import * as React from 'react';
import { Button, Header, Modal } from 'semantic-ui-react';

import { RequestError } from '../../../api/common';
import { RequestErrorMessage } from '../../molecules';

interface State {
    open: boolean;
}

export type OnClickFn = () => void;

interface Props {
    trigger: (onClick: OnClickFn) => React.ReactNode;

    title: string;
    introMsg: React.ReactNode;

    running: boolean;
    runningMsg?: React.ReactNode;

    success: boolean;
    successMsg?: React.ReactNode;

    error: RequestError;

    reset: () => void;
    onConfirm: () => void;
    onDone?: () => void;

    onDoneElements?: () => React.ReactNode;
}

class SingleOperationPopup extends React.Component<Props, State> {
    constructor(props: Props) {
        super(props);
        this.state = { open: false };
    }

    handleOpen() {
        this.props.reset();
        this.setState({ open: true });
    }

    handleClose() {
        this.setState({ open: false });
    }

    handleConfirm() {
        this.props.onConfirm();
    }

    renderContent() {
        const { success, successMsg, error, running, runningMsg, introMsg } = this.props;

        if (success) {
            return successMsg ? successMsg : <p>The operation was completed successfully.</p>;
        }

        if (error) {
            return <RequestErrorMessage error={error} />;
        }

        if (running) {
            return runningMsg ? runningMsg : <p>Processing the request...</p>;
        }

        return introMsg;
    }

    renderActions() {
        const { success, error, running, onDone, onDoneElements } = this.props;

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
            return <Button onClick={() => this.handleConfirm()}>Retry</Button>;
        }

        return (
            <>
                <Button color="green" disabled={running} onClick={() => this.handleClose()}>
                    No
                </Button>
                <Button color="red" loading={running} onClick={() => this.handleConfirm()}>
                    Yes
                </Button>
            </>
        );
    }

    render() {
        const { trigger, title } = this.props;

        return (
            <Modal
                open={this.state.open}
                dimmer="inverted"
                trigger={trigger(() => this.handleOpen())}>
                <Header icon="warning sign" content={title} />
                <Modal.Content>{this.renderContent()}</Modal.Content>
                <Modal.Actions>{this.renderActions()}</Modal.Actions>
            </Modal>
        );
    }
}

export default SingleOperationPopup;
