import React, {Component} from "react";
import {connect} from "react-redux";
import {Dropdown} from "semantic-ui-react";

import {getCurrentTeam} from "../../../session/reducers";

import {actions, selectors} from "./effects";

const LOCKED_SECRET_TYPE = "PASSWORD";

class SecretsListDropdown extends Component {

    componentDidMount() {
        const {loadFn, team} = this.props;
        loadFn(team.name);
    }

    render() {
        const {data, isLoading, loadFn, team, ...rest} = this.props;

        let options = [];
        if (data) {
            options = data.map(({name, type, storageType}) => {
                const icon = storageType === LOCKED_SECRET_TYPE ? "lock" : undefined;
                return {text: `${name} (${type})`, value: name, icon: icon};
            });
        }

        return <Dropdown loading={isLoading} options={options} {...rest} search/>;
    }
}

const mapStateToProps = ({session, secretList}) => ({
    team: getCurrentTeam(session),
    data: selectors.getRows(secretList),
    isLoading: selectors.getIsLoading(secretList)
});

const mapDispatchToProps = (dispatch) => ({
    loadFn: (teamName) => dispatch(actions.fetchSecretList(teamName))
});

export default connect(mapStateToProps, mapDispatchToProps)(SecretsListDropdown);
