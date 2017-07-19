import React, {Component} from "react";
import PropTypes from "prop-types";
import {connect} from "react-redux";
import {Button, Header} from "semantic-ui-react";
import DataTable from "../../shared/DataTable";
import RefreshButton from "../../shared/RefreshButton";
import ErrorMessage from "../../shared/ErrorMessage";
import {actions as modal} from "../../shared/Modal";
import DeleteSecretPopup from "./DeleteSecretPopup";
import * as actions from "./actions";
import * as selectors from "./reducers";
import reducers from "./reducers";
import sagas from "./sagas";

const columns = [
    {key: "name", label: "Name", collapsing: true},
    {key: "type", label: "Type"},
    {key: "actions", label: "Actions", collapsing: true}
];

const nameKey = "name";
const actionsKey = "actions";

const cellFn = (deletePopupFn) => (row, key) => {
    if (key === nameKey) {
        return <span>{row[key]}</span>;
    }

    // column with buttons (actions)
    if (key === actionsKey) {
        const name = row[nameKey];
        return <Button icon="delete" color="red" onClick={() => deletePopupFn(name)}/>;
    }

    return row[key];
};

class SecretTable extends Component {

    componentDidMount() {
        this.update();
    }

    update() {
        const {fetchData} = this.props;
        fetchData();
    }

    render() {
        const {error, loading, data, deletePopupFn} = this.props;

        if (error) {
            return <ErrorMessage message={error} retryFn={() => this.update()}/>;
        }

        return <div>
            <Header as="h3"><RefreshButton loading={loading} onClick={() => this.update()}/>Secrets</Header>
            <DataTable cols={columns} rows={data} cellFn={cellFn(deletePopupFn)}/>
        </div>;
    }
}

SecretTable.propTypes = {
    error: PropTypes.string,
    loading: PropTypes.bool,
    data: PropTypes.array,
    deletePopupFn: PropTypes.func,
};

const mapStateToProps = ({secret}) => ({
    error: selectors.getError(secret),
    loading: selectors.getIsLoading(secret),
    data: selectors.getRows(secret)
});

const mapDispatchToProps = (dispatch) => ({
    fetchData: () => dispatch(actions.fetchSecretList()),
    deletePopupFn: (name) => {
        const onSuccess = [actions.fetchSecretList()];
        dispatch(modal.open(DeleteSecretPopup.MODAL_TYPE, {name, onSuccess}));
    }
});

export default connect(mapStateToProps, mapDispatchToProps)(SecretTable);

export {reducers, sagas};