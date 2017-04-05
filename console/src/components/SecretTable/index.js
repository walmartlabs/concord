import React, {Component, PropTypes} from "react";
import {Header, Icon} from "semantic-ui-react";
import {Link} from "react-router";
import DataTable from "../DataTable";
import RefreshButton from "../RefreshButton";
import ConfirmationPopup from "../ConfirmationPopup";
import {sort, reverseSort} from "../../constants";
import * as constants from "./constants";
import * as routes from "../../routes";

const headerFn = (currentKey, currentDir) => (key, label) => {
    if (!constants.sortableKeys.includes(key)) {
        return label;
    }

    const path = {
        pathname: routes.getSecretListPath(),
        query: {sortBy: key, sortDir: reverseSort(currentDir)}
    };

    return <Link to={path}>{ label }
        { currentKey === key &&
        <Icon name={currentDir === sort.ASC ? "sort ascending" : "sort descending"}/> }
    </Link>;
};

const cellFn = (inFlightFn, onDeleteFn) => (row, key) => {

    if (key === constants.nameKey) {
        return <span>{row[key]}</span>;
    }

    // column with buttons (actions)
    if (key === constants.actionsKey) {
        const name = row[constants.nameKey];
        return onDeleteFn && <ConfirmationPopup icon="trash" message="Delete the selected secret?"
                                                onConfirmFn={() => onDeleteFn(name)}
                                                disabled={inFlightFn(name)}/>;
    }

    if (key === constants.templatesKey) {
        const arr = row[key];
        if (arr) {
            arr.sort();
            return arr.join(", ");
        }
    }

    return row[key];
};

class SecretTable extends Component {

    render() {
        const {loading, onRefreshFn, inFlightFn, onDeleteFn, sortBy, sortDir, cols, rows} = this.props;
        return <div>
            <Header as="h3">{ onRefreshFn && <RefreshButton loading={loading} onClick={onRefreshFn}/> }Secrets</Header>
            <DataTable cols={cols} rows={rows}
                       headerFn={headerFn(sortBy, sortDir)}
                       cellFn={cellFn(inFlightFn, onDeleteFn)}/>
        </div>;
    }
}

SecretTable.propTypes = {
    loading: PropTypes.bool,
    onRefreshFn: PropTypes.func,
    inFlightFn: PropTypes.func,
    onDeleteFn: PropTypes.func,
    sortBy: PropTypes.any,
    sortDir: PropTypes.string
};

export default SecretTable;