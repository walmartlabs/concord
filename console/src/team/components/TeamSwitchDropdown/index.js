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

const teamsToOptions = (teams) =>
    teams.slice().sort(byName)
        .map(t => ({text: t.name, value: t.id}));

const TeamSwitchDropdown = ({currentTeam, teams, onChangeFn}) =>
    <Dropdown item
              text={`Team: ${currentTeam.name}`}
              value={currentTeam.id}
              options={teamsToOptions(teams)}
              onChange={(ev, v) => onChangeFn(v.value)}/>;

const mapStateToProps = ({session}) => ({
    currentTeam: selectors.getCurrentTeam(session),
    teams: selectors.getAvailableTeams(session)
});

const mapDispatchToProps = (dispatch) => ({
    onChangeFn: (teamId) => dispatch(actions.changeTeam(teamId))
});

export default connect(mapStateToProps, mapDispatchToProps)(TeamSwitchDropdown);