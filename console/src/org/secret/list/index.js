import React, {Component} from "react";
import PropTypes from "prop-types";
import {connect} from "react-redux";
import {Button, Header, Popup, Icon} from "semantic-ui-react";

import DataTable from "../../../shared/DataTable";
import RefreshButton from "../../../shared/RefreshButton";
import ErrorMessage from "../../../shared/ErrorMessage";
import {actions as modal} from "../../../shared/Modal";
import DeleteSecretPopup from "./DeleteSecretPopup";
import ShowSecretPublicKey from "./ShowSecretPublicKey";
import {getCurrentOrg} from "../../../session/reducers";

import {actionTypes, actions, selectors, reducers, sagas} from "./effects";

const columns = [
    {key: "visibility", label: "Access", collapsing: true},
    {key: "name", label: "Name"},
    {key: "type", label: "Type"},
    {key: "storeType", label: "Store type"},
    {key: "owner", label: "Owner"},
    {key: "actions", label: "Actions", collapsing: true}
];

const nameKey = "name";
const ownerKey = "owner";
const visibilityKey = "visibility";
const actionsKey = "actions";

const cellFn = (orgName, deletePopupFn, getPublicKey) => (row, key) => {

    if (key === nameKey) {
        return <span>{row[key]}</span>;
    }

    if (key === ownerKey) {
        return row.owner && row.owner.username;
    }

    if (key === visibilityKey) {
        const n = row[key];
        return <Icon name={n === "PUBLIC" ? "unlock" : "lock"} color={n === "PRIVATE" ? "red" : "grey"}/>;
    }

    // column with buttons (actions)
    if (key === actionsKey) {
        const name = row[nameKey];
        return (
            <div style={{textAlign: "right"}}>
                {row.type === "KEY_PAIR" && row.storeType === "SERVER_KEY" &&
                <Popup
                    trigger={<Button color="blue" icon='key' onClick={() => getPublicKey(orgName, name)}/>}
                    content="Get Public Key"
                    inverted
                />
                }

                <Popup
                    trigger={<Button icon="delete" color="red" onClick={() => deletePopupFn(orgName, name)}/>}
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
        const {org: currentOrg} = this.props;
        const {org: prevOrg} = prevProps;
        if (currentOrg !== prevOrg) {
            this.update();
        }
    }

    update() {
        const {fetchData, org} = this.props;
        fetchData(org.name);
    }

    render() {
        const {error, loading, data, org, deletePopupFn, getPublicKey} = this.props;

        if (error) {
            return <ErrorMessage message={error} retryFn={() => this.update()}/>;
        }

        return <div>
            <Header as="h3"><RefreshButton loading={loading} onClick={() => this.update()}/>Secrets</Header>
            <DataTable cols={columns} rows={data} cellFn={cellFn(org.name, deletePopupFn, getPublicKey)}/>
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
    org: getCurrentOrg(session),
    error: selectors.getError(secretList),
    loading: selectors.getIsLoading(secretList),
    data: selectors.getRows(secretList)
});

const mapDispatchToProps = (dispatch) => ({
    fetchData: (orgName) => dispatch(actions.fetchSecretList(orgName)),

    deletePopupFn: (orgName, name) => {
        const onSuccess = [actions.fetchSecretList(orgName)];
        dispatch(modal.open(DeleteSecretPopup.MODAL_TYPE, {orgName, name, onSuccess}));
    },

    getPublicKey: (orgName, name) => {
        dispatch(modal.open(ShowSecretPublicKey.MODAL_TYPE, {orgName, name}));
        dispatch({
            type: actionTypes.USER_SECRET_PUBLICKEY_REQUEST,
            orgName,
            name
        });
    }
});

export default connect(mapStateToProps, mapDispatchToProps)(SecretTable);

export {reducers, sagas};