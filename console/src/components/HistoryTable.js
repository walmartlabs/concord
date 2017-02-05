import React, {Component, PropTypes} from "react";
import {Header, Icon} from "semantic-ui-react";
import {Link} from "react-router";
import DataTable from "./DataTable";
import RefreshButton from "./RefreshButton";
import ConfirmationPopup from "./ConfirmationPopup";
import moment from "moment";
import * as constants from "../constants";
import * as routes from "../routes";

const headerFn = (currentKey, currentDir) => (key, label) => {
    if (!constants.history.sortableKeys.includes(key)) {
        return label;
    }

    const path = {
        pathname: routes.getProcessHistoryPath(),
        query: {sortBy: key, sortDir: constants.reverseSort(currentDir)}
    };

    return <Link to={path}>{ label }
        { currentKey === key &&
        <Icon name={currentDir === constants.sort.ASC ? "sort ascending" : "sort descending"}/> }
    </Link>;
};

const cellFn = (inFlightFn, onKillFn) => (row, key) => {
    // columns with dates
    if (constants.history.dateKeys.includes(key)) {
        const raw = moment(row[key]);
        return raw.format("YYYY-MM-DD HH:mm:ss");
    }

    // status column
    if (key === constants.history.statusKey) {
        const status = row[key];
        const failed = constants.history.failedStatuses.includes(status);
        const icon = constants.history.statusToIcon[status];
        return <div><Icon name={icon} color={failed ? "red" : undefined}/> {status}</div>;
    }

    // column with buttons (actions)
    if (key === constants.history.actionsKey) {
        const status = row[constants.history.statusKey];
        const canBeKilled = constants.history.canBeKilledStatuses.includes(status);
        const id = row[constants.history.idKey];
        return canBeKilled && onKillFn && <ConfirmationPopup message="Kill the selected process?"
                                                             onConfirmFn={() => onKillFn(id)}
                                                             disabled={inFlightFn(id)}/>;
    }

    // link to a process' log file
    if (key === constants.history.logLinkKey) {
        const n = row[constants.history.logFileNameKey];
        const instanceId = row[constants.history.idKey];
        if (n) {
            return <Link to={routes.getProcessLogPath(instanceId)}>{row[key]}</Link>;
        }
    }

    return row[key];
};

class HistoryTable extends Component {

    render() {
        const {loading, onRefreshFn, inFlightFn, onKillFn, sortBy, sortDir, cols, rows} = this.props;
        return <div>
            <Header as="h3">{ onRefreshFn && <RefreshButton loading={loading} onClick={onRefreshFn}/> }History</Header>
            <DataTable cols={cols} rows={rows}
                       headerFn={headerFn(sortBy, sortDir)}
                       cellFn={cellFn(inFlightFn, onKillFn)}/>
        </div>;
    }
}

HistoryTable.propTypes = {
    loading: PropTypes.bool,
    onRefreshFn: PropTypes.func,
    inFlightFn: PropTypes.func,
    onKillFn: PropTypes.func,
    sortBy: PropTypes.any,
    sortDir: PropTypes.string
};

export default HistoryTable;