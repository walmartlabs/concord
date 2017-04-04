import React, {Component} from "react";
import {connect} from "react-redux";
import KeypairTable from "../../components/KeypairTable";
import ErrorMessage from "../../components/ErrorMessage";
import {getKeypairListState as getState} from "../../reducers";
import {getRows, getIsLoading, getError, isInFlight} from "./reducers";
import * as constants from "../../components/KeypairTable/constants";
import * as actions from "./actions";

class VisibleKeypairTable extends Component {
    componentDidMount() {
        this.update();
    }

    componentDidUpdate(prevProps) {
        const {sortBy, sortDir} = this.props;
        if (sortBy !== prevProps.sortBy || sortDir !== prevProps.sortDir) {
            this.update();
        }
    }

    update() {
        const {sortBy, sortDir, fetchData} = this.props;
        fetchData(sortBy, sortDir);
    }

    render() {
        const {error, ...rest} = this.props;
        if (error) {
            return <ErrorMessage message={error} retryFn={() => this.update()}/>;
        }
        return <KeypairTable {...rest} onRefreshFn={() => this.update()}/>;
    }
}

const mapStateToProps = (state, {location}) => {
    const sortBy = location.query.sortBy || constants.defaultSortKey;
    const sortDir = location.query.sortDir || constants.defaultSortDir;

    return {
        cols: constants.columns,
        rows: getRows(getState(state)),
        loading: getIsLoading(getState(state)),
        error: getError(getState(state)),
        inFlightFn: (name) => isInFlight(getState(state), name),
        sortBy,
        sortDir
    }
};

const mapDispatchToProps = (dispatch) => ({
    fetchData: (sortBy, sortDir) => dispatch(actions.fetchKeypairList(sortBy, sortDir)),
    onDeleteFn: (name) => dispatch(actions.deleteKeypair(name))
});

export default connect(mapStateToProps, mapDispatchToProps)(VisibleKeypairTable);