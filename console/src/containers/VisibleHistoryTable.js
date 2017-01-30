import React, {Component} from "react";
import {connect} from "react-redux";
import HistoryTable from "../components/HistoryTable";
import ErrorMessage from "../components/ErrorMessage";
import {getHistoryRows, getIsHistoryLoading, getHistoryLoadingError} from "../reducers";
import * as constants from "../constants";
import * as actions from "../actions";

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

    kill(id) {
        const {killFn} = this.props;
        killFn(id);
    };

    render() {
        const {error, ...rest} = this.props;
        if (error) {
            return <ErrorMessage message={error} retryFn={() => this.update()}/>;
        }

        return <HistoryTable {...rest} onRefreshFn={() => this.update()} onKillFn={(id) => this.kill(id)}/>;
    }
}

const mapStateToProps = (state, {location}) => {
    const sortBy = location.query.sortBy || constants.history.defaultSortKey;
    const sortDir = location.query.sortDir || constants.history.defaultSortDir;

    return {
        cols: constants.history.columns,
        rows: getHistoryRows(state),
        loading: getIsHistoryLoading(state),
        error: getHistoryLoadingError(state),
        sortBy,
        sortDir
    }
};

const mapDispatchToProps = (dispatch) => ({
    fetchData: (sortBy, sortDir) => dispatch(actions.fetchHistoryData(sortBy, sortDir)),
    killFn: (id) => dispatch(actions.killProc(id))
});

export default connect(mapStateToProps, mapDispatchToProps)(VisibleHistoryTable);