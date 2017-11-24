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
import DataItem from "../../shared/DataItem";
import * as api from "./api";
import * as constants from "../constants";

const { actions, reducers, selectors, sagas } = DataItem("process/queue", [], api.loadData);

const columns = [
    { key: "instanceId", label: "Instance ID", collapsing: true },
    { key: "projectName", label: "Project", collapsing: true },
    { key: "status", label: "Status" },
    { key: "initiator", label: "Initiator" },
    { key: "lastUpdatedAt", label: "Updated" },
    { key: "createdAt", label: "Created" },
    { key: "actions", label: "Actions", collapsing: true }
];

const actionsKey = "actions";

const cellFn = (killPopupFn) => (row, key) => {
    // columns with dates
    if (constants.dateKeys.includes(key)) {
        const v = row[key];
        if (!v) {
            return v;
        }

        const raw = moment(row[key]);
        return raw.format("YYYY-MM-DD HH:mm:ss");
    }

    // status column
    if (key === constants.statusKey) {
        const status = row[key];
        const failed = constants.failedStatuses.includes(status);
        const icon = constants.statusIcons[status];
        return <div><Icon name={icon} color={failed ? "red" : undefined} /> {status}</div>;
    }

    // column with buttons (actions)
    if (key === actionsKey) {
        const status = row[constants.statusKey];
        const canBeKilled = constants.canBeKilledStatuses.includes(status);
        const id = row[constants.idKey];
        return canBeKilled && <Button icon="delete"
            color="red"
            onClick={() => killPopupFn(id)} />;
    }

    // link to a process' page
    if (key === constants.idKey) {
        const instanceId = row[constants.idKey];
        return <Link to={`/process/${instanceId}`}>{instanceId}</Link>;
    }

    // link to a project' page
    if (key === constants.projectName) {
        const projectName = row[constants.projectName];
        return <Link to={`/project/${projectName}`}>{projectName}</Link>;
    }

    return row[key];
};

class QueuePage extends Component {

    componentDidMount() {
        this.load();
    }

    load() {
        const { loadData } = this.props;
        loadData();
    }

    render() {
        const { loading, error, data, killPopupFn } = this.props;

        if (error) {
            return <ErrorMessage message={error} retryFn={() => this.load()} />;
        }

        return <div>
            <Header as="h3"><RefreshButton loading={loading} onClick={() => this.load()}/> Queue (all teams)</Header>
            <DataTable cols={columns} rows={data} cellFn={cellFn(killPopupFn)} />
        </div>;
    }
}

QueuePage.propTypes = {
    loading: PropTypes.bool,
    error: PropTypes.any,
    data: PropTypes.array,
    killPopupFn: PropTypes.func
};

const mapStateToProps = ({ queue }) => ({
    loading: selectors.isLoading(queue),
    error: selectors.getError(queue),
    data: selectors.getData(queue)
});

const mapDispatchToProps = (dispatch) => ({
    loadData: () => dispatch(actions.loadData()),
    killPopupFn: (instanceId) => {
        // reload the table when a process is killed
        const onSuccess = [actions.loadData()];
        dispatch(modal.open(KillProcessPopup.MODAL_TYPE, { instanceId, onSuccess }))
    }
});

export default connect(mapStateToProps, mapDispatchToProps)(QueuePage);

export { reducers, sagas };