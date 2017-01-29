import React, {Component, PropTypes} from "react";
import {Header, Icon} from "semantic-ui-react";
import {Link} from "react-router";
import DataTable from "./DataTable";
import RefreshButton from "./RefreshButton";
import moment from "moment";
import * as constants from "../constants";
import * as routes from "../routes";
import "./HistoryTable.css";

const linkFn = (currentKey, currentDir) => (key, label) => {
    const path = {
        pathname: routes.getProcessHistoryPath(),
        query: {sortBy: key, sortDir: constants.reverseSort(currentDir)}
    };

    return <Link to={path}>{ label }
        { currentKey === key && <Icon name={currentDir === constants.sort.ASC ? "sort ascending" : "sort descending"}/> }
    </Link>;
}

const cellFn = (row, key) => {
    if (constants.history.dateKeys.includes(key)) {
        const raw = moment(row[key]);
        return raw.format("YYYY-MM-DD HH:mm:ss");
    }

    if (key === constants.history.statusKey) {
        const status = row[key];
        switch (status) {
            case constants.history.failedStatus:
                return <div className="historyFailedStatus">{status}</div>;
            default:
                return status;
        }
    }

    const n = row[constants.history.logFileNameKey];
    if (key !== constants.history.logLinkKey || !n) {
        return row[key];
    }

    return <Link to={routes.getProcessLogPath(n)}>{row[key]}</Link>;
};

class HistoryTable extends Component {

    render() {
        const {loading, onRefreshFn, sortBy, sortDir, cols, rows} = this.props;
        return <div>
            <Header as="h3">{ onRefreshFn && <RefreshButton loading={loading} onClick={onRefreshFn}/> }History</Header>
            <DataTable cols={cols} rows={rows} headerFn={linkFn(sortBy, sortDir)} cellFn={cellFn}/>
        </div>;
    }
}

HistoryTable.propTypes = {
    loading: PropTypes.bool,
    onRefreshFn: PropTypes.func,
    sortBy: PropTypes.any,
    sortDir: PropTypes.string
};

export default HistoryTable;