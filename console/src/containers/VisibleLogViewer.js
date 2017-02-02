import React, {Component} from "react";
import {connect} from "react-redux";
import LogViewer from "../components/LogViewer";
import ErrorMessage from "../components/ErrorMessage";
import {getLogData, getIsLogLoading, getLogLoadingError, getLoadedLogRange, getLoadedLogStatus} from "../reducers";
import * as actions from "../actions";
import * as constants from "../constants";

const shiftRange = ({low, high, length}) => ({
    low: high,
    // high: Math.max(length, high + constants.log.fetchIncrement)
    high: undefined
});

class VisibleLogViewer extends Component {

    constructor(props) {
        super(props);
        this.timer = undefined;
    }

    componentDidMount() {
        this.update(true);
    }

    componentWillUnmount() {
        this.stopTimer();
    }

    componentDidUpdate(prevProps) {
        const {fileName} = this.props;
        if (fileName !== prevProps.fileName) {
            this.update(true);
        }

        this.handleTimer();
    }

    update(resetRange) {
        this._update(resetRange);
        this.handleTimer();
    }

    _update(resetRange) {
        const {fileName, fetchData, range} = this.props;

        let nextRange;
        if (!resetRange && range) {
            nextRange = shiftRange(range);
        }

        const fresh = resetRange;
        fetchData(fileName, nextRange, fresh);
    }

    handleTimer() {
        const {status} = this.props;
        if (status === constants.process.runningStatus) {
            if (!this.isTimerRunning()) {
                this.startTimer();
            }
        } else {
            this.stopTimer();
        }
    }

    stopTimer() {
        if (this.timer) {
            clearInterval(this.timer);
            this.timer = undefined;
        }
    }

    startTimer() {
        const f = () => {
            this._update();
        };
        this.timer = setInterval(f, constants.log.fetchDelay);
    }

    isTimerRunning() {
        return this.timer !== undefined;
    }

    loadWholeLog() {
        this.stopTimer();

        const {fileName, fetchData} = this.props;
        fetchData(fileName, {low: 0, high: undefined}, true);
    }

    render() {
        const {error, range, ...rest} = this.props;
        if (error) {
            return <ErrorMessage message={error} retryFn={() => this.update()}/>;
        }

        return <LogViewer {...rest}
                          onRefreshFn={() => this.update()}
                          onLoadWholeLogFn={(range && range.min > 0) ? () => this.loadWholeLog() : undefined}
                          fullSize={range && range.length}/>
    }
}

const mapStateToProps = (state, {params}) => ({
    loading: getIsLogLoading(state),
    error: getLogLoadingError(state),
    fileName: params.n,
    data: getLogData(state),
    range: getLoadedLogRange(state),
    status: getLoadedLogStatus(state)
});

const mapDispatchToProps = (dispatch) => ({
    fetchData: (fileName, fetchRange, fresh) => dispatch(actions.fetchLogData(fileName, fetchRange, fresh))
});

export default connect(mapStateToProps, mapDispatchToProps)(VisibleLogViewer);