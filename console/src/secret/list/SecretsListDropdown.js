import React, {Component} from "react";
import {connect} from "react-redux";
import {Dropdown} from "semantic-ui-react";
import * as selectors from "./reducers";
import * as actions from "./actions";

const LOCKED_SECRET_TYPE = "PASSWORD";

class SecretsListDropdown extends Component {

    componentDidMount() {
        const {loadFn} = this.props;
        loadFn();
    }

    render() {
        const {data, isLoading, ...rest} = this.props;

        let options = [];
        if (data) {
            options = data.map(({name, type, storageType}) => {
                const icon = storageType == LOCKED_SECRET_TYPE ? "lock" : undefined;
                return {text: `${name} (${type})`, value: name, icon: icon};
            });
        }

        return <Dropdown loading={isLoading} options={options} {...rest} search/>;
    }
}

const mapStateToProps = ({secret}) => ({
    data: selectors.getRows(secret),
    isLoading: selectors.getIsLoading(secret)
});

const mapDispatchToProps = (dispatch) => ({
    loadFn: () => dispatch(actions.fetchSecretList())
});

export default connect(mapStateToProps, mapDispatchToProps)(SecretsListDropdown);
