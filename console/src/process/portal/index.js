import React, {Component} from "react";
import {connect} from "react-redux";
import {Loader} from "semantic-ui-react";
import ErrorMessage from "../../shared/ErrorMessage";
import * as actions from "./actions";
import * as selectors from "./reducers";
import reducers from "./reducers";
import sagas from "./sagas";

class PortalPage extends Component {

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
        </div>;
    }
}

const mapStateToProps = ({portal}, {location: {query}}) => ({
    entryPoint: query.entryPoint,
    error: selectors.getError(portal),
    submitting: selectors.getIsSubmitting(portal)
});

const mapDispatchToProps = (dispatch) => ({
    startFn: (entryPoint) => dispatch(actions.startProcess(entryPoint))
});

export default connect(mapStateToProps, mapDispatchToProps)(PortalPage);

export {reducers, sagas};