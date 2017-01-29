import React, {Component} from "react";
import {connect} from "react-redux";
import LogViewer from "../components/LogViewer";
import ErrorMessage from "../components/ErrorMessage";
import {getLogData, getIsLogLoading, getLogLoadingError} from "../reducers";
import * as actions from "../actions";

class VisibleLogViewer extends Component {

    componentDidMount() {
        this.update();
    }

    componentDidUpdate(prevProps) {
        const {fileName} = this.props;
        if (fileName !== prevProps.fileName) {
            this.update();
        }
    }

    update() {
        const {fileName, fetchData} = this.props;
        fetchData(fileName);
    }

    render() {
        const {error, ...rest} = this.props;
        if (error) {
            return <ErrorMessage message={error} retryFn={() => this.update()}/>;
        }
        return <LogViewer {...rest} onRefreshFn={() => this.update()}/>
    }
}

const mapStateToProps = (state, {params}) => ({
    loading: getIsLogLoading(state),
    error: getLogLoadingError(state),
    fileName: params.n,
    data: getLogData(state)
});

const mapDispatchToProps = (dispatch) => ({
    fetchData: (fileName) => dispatch(actions.fetchLogData(fileName))
});

export default connect(mapStateToProps, mapDispatchToProps)(VisibleLogViewer);