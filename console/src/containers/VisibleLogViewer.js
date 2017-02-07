import React, {Component} from "react";
import {connect} from "react-redux";
import LogViewer from "../components/LogViewer";
import ErrorMessage from "../components/ErrorMessage";
import {getLogData, getIsLogLoading, getLogLoadingError, getLoadedLogRange, getLoadedLogStatus} from "../reducers";
import * as actions from "../actions";
import * as constants from "../constants";

const shiftRange = ({low, high, length}) => ({
    low: high,
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
        const {instanceId} = this.props;
        if (instanceId !== prevProps.instanceId) {
            this.update(true);
        }

        this.handleTimer();
    }

    update(reset) {
        this._update(reset);
        this.handleTimer();
    }

    _update(reset) {
        const {instanceId, fetchData, range} = this.props;

        let nextRange;
        if (!reset && range) {
            nextRange = shiftRange(range);
        }

        fetchData(instanceId, nextRange, reset);
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

        const {instanceId, fetchData} = this.props;
        fetchData(instanceId, {low: 0, high: undefined});
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
    instanceId: params.id,
    data: getLogData(state),
    range: getLoadedLogRange(state),
    status: getLoadedLogStatus(state)
});

const mapDispatchToProps = (dispatch) => ({
    fetchData: (instanceId, fetchRange, reset) => dispatch(actions.fetchLogData(instanceId, fetchRange, reset))
});

export default connect(mapStateToProps, mapDispatchToProps)(VisibleLogViewer);