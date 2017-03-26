import React, {Component} from "react";
import {connect} from "react-redux";
import {Loader} from "semantic-ui-react";
import * as actions from "./actions";
import {getProcessWizardState} from "../../reducers";
import {getError} from "./reducers";
import ErrorMessage from "../../components/ErrorMessage";

class VisibleProcessWizard extends Component {

    componentDidMount() {
        const {startFn, processInstanceId} = this.props;
        startFn(processInstanceId);
    }

    render() {
        const {error} = this.props;
        if (error) {
            return <ErrorMessage message={error}/>;
        }
        return <Loader active/>;
    }
}

const mapStateToProps = (state, {params}) => ({
    processInstanceId: params.processId,
    error: getError(getProcessWizardState(state))
});

const mapDispatchToProps = (dispatch) => ({
    startFn: (processInstanceId) => dispatch(actions.showNextForm(processInstanceId))
});

export default connect(mapStateToProps, mapDispatchToProps)(VisibleProcessWizard);
