import React, {Component} from "react";
import {connect} from "react-redux";
import {Container, Loader} from "semantic-ui-react";
import {Link} from "react-router";
import ErrorMessage from "../../shared/ErrorMessage";
import * as actions from "./actions";
import * as selectors from "./reducers";
import reducers from "./reducers";
import sagas from "./sagas";

class ProcessWizard extends Component {

    componentDidMount() {
        const {startFn, instanceId} = this.props;
        startFn(instanceId);
    }

    render() {
        const {error, instanceId} = this.props;
        if (error) {
            return <ErrorMessage message={error}/>;
        }
        return <div>
            <Container textAlign="center">
                Please wait until the current process completes or <Link to={`/process/${instanceId}`}>click
                here</Link> to check the status.
            </Container>
            <div>
                <Loader active/>
            </div>
        </div>;
    }
}

const mapStateToProps = ({wizard}, {params}) => ({
    instanceId: params.instanceId,
    error: selectors.getError(wizard)
});

const mapDispatchToProps = (dispatch) => ({
    startFn: (instanceId) => dispatch(actions.showNextForm(instanceId))
});

export default connect(mapStateToProps, mapDispatchToProps)(ProcessWizard);

export {reducers, sagas};
