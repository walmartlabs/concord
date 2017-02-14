import React, {Component, PropTypes} from "react";
import {Header, Icon} from "semantic-ui-react";
import {Link} from "react-router";
import DataTable from "./DataTable";
import RefreshButton from "./RefreshButton";
import ConfirmationPopup from "./ConfirmationPopup";
import * as constants from "../constants";
import * as routes from "../routes";

const headerFn = (currentKey, currentDir) => (key, label) => {
    if (!constants.projectList.sortableKeys.includes(key)) {
        return label;
    }

    const path = {
        pathname: routes.getProjectListPath(),
        query: {sortBy: key, sortDir: constants.reverseSort(currentDir)}
    };

    return <Link to={path}>{ label }
        { currentKey === key &&
        <Icon name={currentDir === constants.sort.ASC ? "sort ascending" : "sort descending"}/> }
    </Link>;
};

const cellFn = (inFlightFn, onDeleteFn) => (row, key) => {
    if (key === constants.projectList.nameKey) {
        const name = row[constants.projectList.nameKey];
        return <Link to={routes.getProjectPath(name)}>{row[key]}</Link>;
    }

    // column with buttons (actions)
    if (key === constants.projectList.actionsKey) {
        const name = row[constants.projectList.nameKey];
        return onDeleteFn && <ConfirmationPopup icon="trash" message="Delete the selected project?"
                                                onConfirmFn={() => onDeleteFn(name)}
                                                disabled={inFlightFn(name)}/>;
    }

    return row[key];
};

class ProjectTable extends Component {

    render() {
        const {loading, onRefreshFn, inFlightFn, onDeleteFn, sortBy, sortDir, cols, rows} = this.props;
        return <div>
            <Header as="h3">{ onRefreshFn && <RefreshButton loading={loading} onClick={onRefreshFn}/> }Projects</Header>
            <DataTable cols={cols} rows={rows}
                       headerFn={headerFn(sortBy, sortDir)}
                       cellFn={cellFn(inFlightFn, onDeleteFn)}/>
        </div>;
    }
}

ProjectTable.propTypes = {
    loading: PropTypes.bool,
    onRefreshFn: PropTypes.func,
    inFlightFn: PropTypes.func,
    onDeleteFn: PropTypes.func,
    sortBy: PropTypes.any,
    sortDir: PropTypes.string
};

export default ProjectTable;