import React, {Component} from "react";
import PropTypes from "prop-types";
import {connect} from "react-redux";
import {Link} from "react-router";
import moment from "moment";
import {Button, Header, Icon} from "semantic-ui-react";
import KillProcessPopup from "../KillProcessPopup";
import RefreshButton from "../../shared/RefreshButton";
import ErrorMessage from "../../shared/ErrorMessage";
import DataTable from "../../shared/DataTable";
import {actions as modal} from "../../shared/Modal";
import * as actions from "./actions";
import reducers from "./reducers";
import * as selectors from "./reducers";
import sagas from "./sagas";
import * as constants from "../constants";

const columns = [
    {key: "instanceId", label: "Instance ID", collapsing: true},
    {key: "projectName", label: "Project", collapsing: true},
    {key: "status", label: "Status"},
    {key: "initiator", label: "Initiator"},
    {key: "lastUpdateDt", label: "Updated"},
    {key: "createdDt", label: "Created"},
    {key: "actions", label: "Actions", collapsing: true}
];

const actionsKey = "actions";

const cellFn = (killPopupFn) => (row, key) => {
    // columns with dates
    if (constants.dateKeys.includes(key)) {
        const raw = moment(row[key]);
        return raw.format("YYYY-MM-DD HH:mm:ss");
    }

    // status column
    if (key === constants.statusKey) {
        const status = row[key];
        const failed = constants.failedStatuses.includes(status);
        const icon = constants.statusIcons[status];
        return <div><Icon name={icon} color={failed ? "red" : undefined}/> {status}</div>;
    }

    // column with buttons (actions)
    if (key === actionsKey) {
        const status = row[constants.statusKey];
        const canBeKilled = constants.canBeKilledStatuses.includes(status);
        const id = row[constants.idKey];
        return canBeKilled && <Button icon="delete"
                                      color="red"
                                      onClick={() => killPopupFn(id)}/>;
    }

    // link to a process' page
    if (key === constants.idKey) {
        const instanceId = row[constants.idKey];
        return <Link to={`/process/${instanceId}`}>{instanceId}</Link>;
    }

    return row[key];
};

class HistoryPage extends Component {

    componentDidMount() {
        this.load();
    }

    load() {
        const {loadData} = this.props;
        loadData();
    }

    render() {
        const {loading, error, data, killPopupFn} = this.props;

        if (error) {
            return <ErrorMessage message={error} retryFn={() => this.load()}/>;
        }

        return <div>
            <Header as="h3"><RefreshButton loading={loading} onClick={() => this.load()}/> History</Header>
            <DataTable cols={columns} rows={data} cellFn={cellFn(killPopupFn)}/>
        </div>;
    }
}

HistoryPage.propTypes = {
    loading: PropTypes.bool,
    error: PropTypes.any,
    data: PropTypes.array,
    killPopupFn: PropTypes.func
};

const mapStateToProps = ({history}) => ({
    loading: selectors.isLoading(history),
    error: selectors.getError(history),
    data: selectors.getData(history)
});

const mapDispatchToProps = (dispatch) => ({
    loadData: () => dispatch(actions.loadData()),
    killPopupFn: (instanceId) => {
        // reload the table when a process is killed
        const onSuccess = [actions.loadData()];
        dispatch(modal.open(KillProcessPopup.MODAL_TYPE, {instanceId, onSuccess}))
    }
});

export default connect(mapStateToProps, mapDispatchToProps)(HistoryPage);

export {reducers, sagas};