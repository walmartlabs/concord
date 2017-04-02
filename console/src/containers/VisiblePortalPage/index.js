import React, {Component} from "react";
import {connect} from "react-redux";
import {Header, Loader} from "semantic-ui-react";
import ErrorMessage from "../../components/ErrorMessage";
import {startProcess} from "./actions";
import {getError, getIsSubmitting} from "./reducers";
import {getPortalState} from "../../reducers";

class VisiblePortalPage extends Component {

    componentDidMount() {
        const {entryPoint, startFn} = this.props;
        startFn(entryPoint);
    }

    componentDidUpdate(prevProps) {
        const {entryPoint, startFn} = this.props;
        if (entryPoint !== prevProps.entryPoint) {
            startFn(entryPoint);
        }
    }

    render() {
        const {submitting, error} = this.props;

        if (error) {
            return <ErrorMessage message={error}/>;
        }

        return <div>
            <Loader active={submitting}/>
            <Header as="h3" textAlign="center">Starting the process...</Header>
        </div>;
    }
}

const mapStateToProps = (state, {location: {query}}) => ({
    entryPoint: query.entryPoint,
    error: getError(getPortalState(state)),
    submitting: getIsSubmitting(getPortalState(state))
});

const mapDispatchToProps = (dispatch) => ({
    startFn: (entryPoint) => dispatch(startProcess(entryPoint))
});

export default connect(mapStateToProps, mapDispatchToProps)(VisiblePortalPage);
