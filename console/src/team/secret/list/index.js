import React, {Component} from "react";
import PropTypes from "prop-types";
import {connect} from "react-redux";
import {Button, Header, Popup} from "semantic-ui-react";

import DataTable from "../../../shared/DataTable";
import RefreshButton from "../../../shared/RefreshButton";
import ErrorMessage from "../../../shared/ErrorMessage";
import {actions as modal} from "../../../shared/Modal";
import DeleteSecretPopup from "./DeleteSecretPopup";
import ShowSecretPublicKey from "./ShowSecretPublicKey";
import {getCurrentTeam} from "../../../session/reducers";

import {actionTypes, actions, selectors, reducers, sagas} from "./effects";

const columns = [
    {key: "name", label: "Name", collapsing: true},
    {key: "type", label: "Type"},
    {key: "storeType", label: "Store type"},
    {key: "actions", label: "Actions", collapsing: true}
];

const nameKey = "name";
const actionsKey = "actions";

const cellFn = (teamName, deletePopupFn, getPublicKey) => (row, key) => {

    if (key === nameKey) {
        return <span>{row[key]}</span>;
    }

    console.log(row, key);

    // column with buttons (actions)
    if (key === actionsKey) {
        const name = row[nameKey];
        return (
            <div style={{textAlign: "right"}}>
                {row.type === "KEY_PAIR" && row.storeType === "SERVER_KEY" &&
                <Popup
                    trigger={<Button color="blue" icon='key' onClick={() => getPublicKey(teamName, name)}/>}
                    content="Get Public Key"
                    inverted
                />
                }

                <Popup
                    trigger={<Button icon="delete" color="red" onClick={() => deletePopupFn(teamName, name)}/>}
                    content="Delete"
                    inverted
                />
            </div>
        );
    }

    return row[key];
};

class SecretTable extends Component {

    componentDidMount() {
        this.update();
    }

    componentDidUpdate(prevProps) {
        const {team: currentTeam} = this.props;
        const {team: prevTeam} = prevProps;
        if (currentTeam !== prevTeam) {
            this.update();
        }
    }

    update() {
        const {fetchData, team} = this.props;
        fetchData(team.name);
    }

    render() {
        const {error, loading, data, team, deletePopupFn, getPublicKey} = this.props;

        if (error) {
            return <ErrorMessage message={error} retryFn={() => this.update()}/>;
        }

        return <div>
            <Header as="h3"><RefreshButton loading={loading} onClick={() => this.update()}/>Secrets</Header>
            <DataTable cols={columns} rows={data} cellFn={cellFn(team.name, deletePopupFn, getPublicKey)}/>
        </div>;
    }
}

SecretTable.propTypes = {
    error: PropTypes.string,
    loading: PropTypes.bool,
    data: PropTypes.array,
    deletePopupFn: PropTypes.func,
};

const mapStateToProps = ({session, secretList}) => ({
    team: getCurrentTeam(session),
    error: selectors.getError(secretList),
    loading: selectors.getIsLoading(secretList),
    data: selectors.getRows(secretList)
});

const mapDispatchToProps = (dispatch) => ({
    fetchData: (teamName) => dispatch(actions.fetchSecretList(teamName)),

    deletePopupFn: (teamName, name) => {
        const onSuccess = [actions.fetchSecretList(teamName)];
        dispatch(modal.open(DeleteSecretPopup.MODAL_TYPE, {teamName, name, onSuccess}));
    },

    getPublicKey: (teamName, name) => {
        dispatch(modal.open(ShowSecretPublicKey.MODAL_TYPE, {teamName, name}));
        dispatch({
            type: actionTypes.USER_SECRET_PUBLICKEY_REQUEST,
            teamName,
            name
        });
    }
});

export default connect(mapStateToProps, mapDispatchToProps)(SecretTable);

export {reducers, sagas};