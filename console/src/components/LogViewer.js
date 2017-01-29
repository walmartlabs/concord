import React, {Component, PropTypes} from "react";
import {Header} from "semantic-ui-react";
import RefreshButton from "../components/RefreshButton";
import "./LogViewer.css";

class LogViewer extends Component {

    render() {
        const {onRefreshFn, loading, fileName, data} = this.props;

        return <div>
            <Header as="h3">{onRefreshFn && <RefreshButton loading={loading} onClick={onRefreshFn}/>}{fileName}</Header>
            <div className="logViewer">{data}</div>
        </div>
    }
}

LogViewer.propTypes = {
    data: PropTypes.any,
    loading: PropTypes.bool,
    fileName: PropTypes.string,
    onRefreshFn: PropTypes.func
};

export default LogViewer;