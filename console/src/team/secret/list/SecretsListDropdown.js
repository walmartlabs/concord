import React, {Component} from "react";
import {connect} from "react-redux";
import {Dropdown} from "semantic-ui-react";

import {getCurrentTeamName} from "../../../session/reducers";

import {actions, selectors} from "./effects";

const LOCKED_SECRET_TYPE = "PASSWORD";

class SecretsListDropdown extends Component {

    componentDidMount() {
        const {loadFn, teamName} = this.props;
        loadFn(teamName);
    }

    render() {
        const {data, isLoading, loadFn, teamName, ...rest} = this.props;

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
    teamName: getCurrentTeamName(session),
    data: selectors.getRows(secretList),
    isLoading: selectors.getIsLoading(secretList)
});

const mapDispatchToProps = (dispatch) => ({
    loadFn: (teamName) => dispatch(actions.fetchSecretList(teamName))
});

export default connect(mapStateToProps, mapDispatchToProps)(SecretsListDropdown);
