import React from "react";
import {connect} from "react-redux";
import {Dropdown} from "semantic-ui-react"
import {selectors} from "../../../session";

const byName = ({name: a}, {name: b}) => {
    if (a < b) {
        return -1;
    } else if (a > b) {
        return 1;
    }
    return 0;
};

const teamsToOptions = (teams) =>
    teams.slice().sort(byName)
        .map(t => ({text: t.name, value: t.name}));

const TeamSwitchDropdown = ({currentTeamName, teams, onChangeFn}) =>
    <Dropdown item
              text={`Team: ${currentTeamName}`}
              value={currentTeamName}
              options={teamsToOptions(teams)}
              onChange={(ev, v) => onChangeFn(v.value)}/>;

const mapStateToProps = ({session}) => ({
    currentTeamName: selectors.getCurrentTeamName(session),
    teams: selectors.getAvailableTeams(session)
});

const mapDispatchToProps = (dispatch) => ({
    onChangeFn: (teamName) => {
        console.log("!!!!", teamName);
    }
});

export default connect(mapStateToProps, mapDispatchToProps)(TeamSwitchDropdown);