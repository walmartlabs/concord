import React, {Component} from "react";
import {connect} from "react-redux";
import HistoryTable from "../../components/HistoryTable";
import ErrorMessage from "../../components/ErrorMessage";
import {getRows, getIsLoading, getError, isInFlight} from "./reducers";
import {getHistoryState as getState} from "../../reducers";
import * as constants from "../../components/HistoryTable/constants";
import * as actions from "./actions";

class VisibleHistoryTable extends Component {
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

        return <HistoryTable {...rest} onRefreshFn={() => this.update()}/>;
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
        inFlightFn: (id) => isInFlight(getState(state), id),
        sortBy,
        sortDir
    }
};

const mapDispatchToProps = (dispatch) => ({
    fetchData: (sortBy, sortDir) => dispatch(actions.fetchHistoryData(sortBy, sortDir)),
    onKillFn: (id) => dispatch(actions.killProc(id))
});

export default connect(mapStateToProps, mapDispatchToProps)(VisibleHistoryTable);