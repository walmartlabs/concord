import React, {Component, PropTypes} from "react";
import {Header, Icon, Label} from "semantic-ui-react";
import {Link} from "react-router";
import DataTable from "./DataTable";
import RefreshButton from "./RefreshButton";
import KillConfirmation from "./KillConfirmation";
import moment from "moment";
import * as constants from "../constants";
import * as routes from "../routes";
import "./HistoryTable.css";

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
}

const cellFn = (onKillFn) => (row, key) => {
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
        return <Label color={failed ? "red" : undefined}>
            <Icon name={icon}/> {status}
        </Label>;
    }

    // column with buttons (actions)
    if (key === constants.history.buttonsKey) {
        const status = row[constants.history.statusKey];
        const canBeKilled = constants.history.canBeKilledStatuses.includes(status);
        const id = row[constants.history.idKey];
        return canBeKilled && onKillFn && <KillConfirmation onConfirmFn={() => onKillFn(id)}/>;
    }

    // link to a process' log file
    if (key === constants.history.logLinkKey) {
        const n = row[constants.history.logFileNameKey];
        if (n) {
            return <Link to={routes.getProcessLogPath(n)}>{row[key]}</Link>;
        }
    }

    return row[key];
};

class HistoryTable extends Component {

    render() {
        const {loading, onRefreshFn, onKillFn, sortBy, sortDir, cols, rows} = this.props;
        return <div>
            <Header as="h3">{ onRefreshFn && <RefreshButton loading={loading} onClick={onRefreshFn}/> }History</Header>
            <DataTable cols={cols} rows={rows}
                       headerFn={headerFn(sortBy, sortDir)}
                       cellFn={cellFn(onKillFn)}/>
        </div>;
    }
}

HistoryTable.propTypes = {
    loading: PropTypes.bool,
    onRefreshFn: PropTypes.func,
    onKillFn: PropTypes.func,
    sortBy: PropTypes.any,
    sortDir: PropTypes.string
};

export default HistoryTable;