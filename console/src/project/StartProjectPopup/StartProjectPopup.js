import React, {Component} from "react";
import {connect} from "react-redux";
import {push as pushHistory} from "react-router-redux";
import {Button, Form, Modal} from "semantic-ui-react";
import {actions as modal} from "../../shared/Modal";
import {actions as projectActions, selectors as projectStartSelectors} from "../StartProjectPopup";
import "./styles.css";

export const MODAL_TYPE = "START_PROJECT_POPUP";

class StartProjectPopup extends Component {

    render() {
        const {open, onCloseFn, onConfirmFn, onProcessStateFn, projectName, repositoryName} = this.props;
        const {startResult: result, startLoading} = this.props;

        const startProjectFn = (ev) => {
            ev.preventDefault();
            onConfirmFn({projectName, repositoryName});
        };

        const showProcessStatusButton = result && result.instanceId;
        const showCloseButton = result && true;
        const closeButtonColor = result && result.error ? "grey" : "green";

        return <Modal open={open} dimmer="inverted">
            <Modal.Header>{result ? "Process result" : "Start a new process?"}</Modal.Header>
            <Modal.Content>
                <Modal.Description>
                    {result ?
                        <Form>
                            <div className="projectStartResultBox">
                                {JSON.stringify(result, null, 2)}
                            </div>
                        </Form>
                        :
                        <p>Are you sure you want to start a new process using '<b>{repositoryName}</b>' repository?</p>
                    }
                </Modal.Description>
            </Modal.Content>
            <Modal.Actions>
                <Button color="red" disabled={startLoading} onClick={onCloseFn}
                        className={result !== null ? 'hidden' : ''}>No</Button>
                <Button color="green" loading={startLoading} onClick={startProjectFn}
                        className={result !== null ? 'hidden' : ''}>Yes</Button>

                {showProcessStatusButton && <Button color="grey" onClick={() => onProcessStateFn(result.instanceId)}>Open the process status</Button>}
                {showCloseButton && <Button color={closeButtonColor} onClick={onCloseFn}>Close</Button>}
            </Modal.Actions>
        </Modal>;
    }
}

StartProjectPopup.MODAL_TYPE = MODAL_TYPE;

const mapStateToProps = (state) => ({
    startResult: projectStartSelectors.getResult(state.projectStart),
    startLoading: projectStartSelectors.isLoading(state.projectStart)
});

const mapDispatchToProps = (dispatch) => ({
    onCloseFn: () => {
        dispatch(modal.close())
        dispatch(projectActions.resetStart());
    },
    onConfirmFn: (data) => dispatch(projectActions.startProject(data)),
    onProcessStateFn: (data) => {
        dispatch(modal.close())
        dispatch(projectActions.resetStart());
        dispatch(pushHistory(`/process/${data}`));
    }
});

export default connect(mapStateToProps, mapDispatchToProps)(StartProjectPopup);