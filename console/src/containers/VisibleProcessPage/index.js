import React, {Component} from "react";
import {connect} from "react-redux";
import {Link} from "react-router";
import {push as pushHistory} from "react-router-redux";
import {Button, Divider, Header, Label, Loader, Table} from "semantic-ui-react";
import ErrorMessage from "../../components/ErrorMessage";
import {getProcessState} from "../../reducers";
import * as processActions from "./actions";
import * as processSelectors from "./reducers";
import ConfirmationPopup from "../../components/ConfirmationPopup";
import RefreshButton from "../../components/RefreshButton";
import {getProcessFormPath, getProcessLogPath, getProcessWizardPath} from "../../routes";
import * as constants from "./constants";
import * as global from "../../constants";

class VisibleProcessPage extends Component {

    componentDidMount() {
        this.load();
    }

    load() {
        const {processInstanceId, fetchData} = this.props;
        fetchData(processInstanceId);
    }

    openLog(ev) {
        ev.preventDefault();
        const {processInstanceId, openLogFn} = this.props;
        openLogFn(processInstanceId);
    }

    startWizard(ev) {
        ev.preventDefault();
        const {processInstanceId, wizardFn} = this.props;
        wizardFn(processInstanceId);
    }

    render() {
        let {processInstanceId, data, loading, error, killFn} = this.props;

        if (loading || !data) {
            return <Loader active/>;
        }

        if (error) {
            return <ErrorMessage message={error} retryFn={() => this.load()}/>;
        }

        const showRefresh = constants.activeStatuses.includes(data.status);
        const showWizard = constants.enableFormsStatuses.includes(data.status);
        const showForms = data.forms && data.forms.length > 0 && constants.enableFormsStatuses.includes(data.status);

        return <div>
            <Header as="h3">
                Process {processInstanceId}
                { showRefresh && <RefreshButton loading={loading} onClick={() => this.load()}/>}
            </Header>

            <Table definition collapsing>
                <Table.Body>
                    <Table.Row>
                        <Table.Cell>Status</Table.Cell>
                        <Table.Cell>
                            <Label color={global.process.statusColors[data.status]}
                                   icon={global.process.statusIcons[data.status]}
                                   content={data.status}/>
                        </Table.Cell>
                    </Table.Row>
                    <Table.Row>
                        <Table.Cell>Initiator</Table.Cell>
                        <Table.Cell>{data.initiator}</Table.Cell>
                    </Table.Row>
                    <Table.Row>
                        <Table.Cell>Started at</Table.Cell>
                        <Table.Cell>{data.createdDt}</Table.Cell>
                    </Table.Row>
                    <Table.Row>
                        <Table.Cell>Last update</Table.Cell>
                        <Table.Cell>{data.lastUpdateDt}</Table.Cell>
                    </Table.Row>
                </Table.Body>
            </Table>

            <Button onClick={(ev) => this.openLog(ev)}>View Log</Button>

            { constants.canBeKilledStatuses.includes(data.status) &&
            <ConfirmationPopup buttonLabel={"Kill"}
                               message="Kill the process?"
                               onConfirmFn={() => killFn(processInstanceId)}/> }

            { showWizard && <Button onClick={(ev) => this.startWizard(ev)}>Wizard</Button> }

            { showForms && <div>
                <Divider/>

                <Header as="h4">Required actions</Header>

                <Table>
                    <Table.Header>
                        <Table.Row>
                            <Table.HeaderCell>Action</Table.HeaderCell>
                            <Table.HeaderCell>Description</Table.HeaderCell>
                        </Table.Row>
                    </Table.Header>
                    <Table.Body>
                        { data.forms.map(({formInstanceId, name}) => <Table.Row key={formInstanceId}>
                            <Table.Cell>
                                <Link to={getProcessFormPath(processInstanceId, formInstanceId)}>{name}</Link>
                            </Table.Cell>
                            <Table.Cell>Form</Table.Cell>
                        </Table.Row>) }
                    </Table.Body>
                </Table>
            </div>
            }
        </div>;
    }
}

const mapStateToProps = (state, {params}) => ({
    processInstanceId: params.id,
    data: processSelectors.getData(getProcessState(state)),
    loading: processSelectors.getIsLoading(getProcessState(state)),
    error: processSelectors.getError(getProcessState(state))
});

const mapDispatchToProps = (dispatch) => ({
    fetchData: (processInstanceId) => dispatch(processActions.fetchData(processInstanceId)),
    openLogFn: (processInstanceId) => dispatch(pushHistory(getProcessLogPath(processInstanceId))),
    killFn: (processInstanceId) => dispatch(processActions.kill(processInstanceId)),
    wizardFn: (processInstanceId) => dispatch(pushHistory(getProcessWizardPath(processInstanceId)))
});

export default connect(mapStateToProps, mapDispatchToProps)(VisibleProcessPage);
