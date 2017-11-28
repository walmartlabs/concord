import React from "react";
import {connect} from "react-redux";
import {Dropdown} from "semantic-ui-react"
import {selectors, actions} from "../../../session";

const byName = ({name: a}, {name: b}) => {
    if (a < b) {
        return -1;
    } else if (a > b) {
        return 1;
    }
    return 0;
};

const orgsToOptions = (orgs) =>
    orgs.slice().sort(byName)
        .map(t => ({text: t.name, value: t.id}));

const OrgSwitchDropdown = ({currentOrg, orgs, onChangeFn}) =>
    <Dropdown item
              text={`Organization: ${currentOrg.name}`}
              value={currentOrg.id}
              options={orgsToOptions(orgs)}
              onChange={(ev, v) => onChangeFn(v.value)}/>;

const mapStateToProps = ({session}) => ({
    currentOrg: selectors.getCurrentOrg(session),
    orgs: selectors.getAvailableOrgs(session)
});

const mapDispatchToProps = (dispatch) => ({
    onChangeFn: (orgId) => dispatch(actions.changeOrg(orgId))
});

export default connect(mapStateToProps, mapDispatchToProps)(OrgSwitchDropdown);