import React, {Component} from "react";
import PropTypes from "prop-types";
import {connect} from "react-redux";
import {Link} from "react-router";
import {Button, Divider, Header, Label, Loader, Table} from "semantic-ui-react";
import {push as pushHistory} from "react-router-redux";
import moment from "moment";
import ErrorMessage from "../shared/ErrorMessage";
import RefreshButton from "../shared/RefreshButton";
import KillProcessPopup from "./KillProcessPopup";
import {actions as modal} from "../shared/Modal";
import * as constants from "./constants";
import * as actions from "./actions";
import reducers, * as selectors from "./reducers";
import sagas from "./sagas";

const enableFormsStatuses = [constants.status.suspendedStatus];

const formatTimestamp = (t) => t ? moment(t).format("YYYY-MM-DD HH:mm:ss") : t;

class ProcessStatusPage extends Component {

    componentDidMount() {
        this.load();
    }

    componentDidUpdate(prevProps) {
        const {instanceId} = this.props;
        if (prevProps.instanceId !== instanceId) {
            this.load();
        }
    }

    load() {
        const {instanceId, loadData} = this.props;
        loadData(instanceId);
    }

    openLog() {
        const {instanceId, openLog} = this.props;
        openLog(instanceId);
    }

    openWizard() {
        const {instanceId, openWizard} = this.props;
        openWizard(instanceId);
    }

    render() {
        const {instanceId, data, loading, error, openKillPopup} = this.props;

        if (loading || !data) {
            return <Loader active/>;
        }

        if (error) {
            return <ErrorMessage message={error} retryFn={() => this.load()}/>;
        }

        const showRefresh = constants.activeStatuses.includes(data.status);
        const enableLogButton = constants.hasLogStatuses.includes(data.status) && data.logFileName;
        const showKillButton = constants.canBeKilledStatuses.includes(data.status);
        const showStateDownload = constants.hasProcessState.includes(data.status);
        const showForms = data.forms && data.forms.length > 0 && enableFormsStatuses.includes(data.status);
        const showWizard = showForms;

        return <div>
            <Header as="h3">
                Process {instanceId}
                {showRefresh && <RefreshButton loading={loading} onClick={() => this.load()}/>}
            </Header>

            <Table definition collapsing>
                <Table.Body>
                    <Table.Row>
                        <Table.Cell>Parent process</Table.Cell>
                        <Table.Cell>
                            {data.parentInstanceId ?
                                <Link to={`/process/${data.parentInstanceId}`}>{data.parentInstanceId}</Link> : "-"}
                        </Table.Cell>
                    </Table.Row>
                    <Table.Row>
                        <Table.Cell>Kind</Table.Cell>
                        <Table.Cell>{data.kind ? data.kind : "-"}</Table.Cell>
                    </Table.Row>
                    <Table.Row>
                        <Table.Cell>Project</Table.Cell>
                        <Table.Cell>{data.projectName ? data.projectName : "-"}</Table.Cell>
                    </Table.Row>
                    <Table.Row>
                        <Table.Cell>Status</Table.Cell>
                        <Table.Cell>
                            <Label color={constants.statusColors[data.status]}
                                   icon={constants.statusIcons[data.status]}
                                   content={data.status}/>
                        </Table.Cell>
                    </Table.Row>
                    <Table.Row>
                        <Table.Cell>Initiator</Table.Cell>
                        <Table.Cell>{data.initiator}</Table.Cell>
                    </Table.Row>
                    <Table.Row>
                        <Table.Cell>Started at</Table.Cell>
                        <Table.Cell>{formatTimestamp(data.createdAt)}</Table.Cell>
                    </Table.Row>
                    <Table.Row>
                        <Table.Cell>Last update</Table.Cell>
                        <Table.Cell>{formatTimestamp(data.lastUpdatedAt)}</Table.Cell>
                    </Table.Row>
                    <Table.Row>
                        <Table.Cell>Tags</Table.Cell>
                        <Table.Cell>{data.tags ? data.tags.sort().join(", ") : "-"}</Table.Cell>
                    </Table.Row>
                </Table.Body>
            </Table>

            <Button disabled={!enableLogButton} onClick={() => this.openLog()}>View Log</Button>

            { showStateDownload && 
                <a href={`/api/v1/process/${instanceId}/state/snapshot`} download={`Concord_${data.status}_${instanceId}.zip`}>
                    <Button icon="download" color="blue" content="State" />
                </a>
            }

            {showKillButton &&
                <Button icon="delete" color="red" content="Kill" onClick={() => openKillPopup(instanceId)}/>}

            {showWizard && <Button onClick={() => this.openWizard()}>Wizard</Button>}

            {showForms && <div>
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
                        {data.forms.map(({formInstanceId, name}) => <Table.Row key={formInstanceId}>
                            <Table.Cell>
                                <Link to={`/process/${instanceId}/form/${formInstanceId}`}>{name}</Link>
                            </Table.Cell>
                            <Table.Cell>Form</Table.Cell>
                        </Table.Row>)}
                    </Table.Body>
                </Table>
            </div>
            }
        </div>;
    }
}

ProcessStatusPage.propTypes = {
    instanceId: PropTypes.string.isRequired,
    data: PropTypes.object,
    loading: PropTypes.bool,
    error: PropTypes.string,
    loadData: PropTypes.func.isRequired,
    openLog: PropTypes.func.isRequired,
    openKillPopup: PropTypes.func.isRequired,
    openWizard: PropTypes.func.isRequired
};

const mapStateToProps = ({process}, {params: {instanceId}}) => ({
    instanceId,
    data: selectors.getData(process),
    loading: selectors.isLoading(process),
    error: selectors.getError(process)
});

const mapDispatchToProps = (dispatch) => ({
    loadData: (instanceId) => dispatch(actions.load(instanceId)),
    openLog: (instanceId) => dispatch(pushHistory(`/process/${instanceId}/log`)),
    openKillPopup: (instanceId) => {
        // reload the data when a process is killed
        const onSuccess = [actions.load(instanceId)];
        dispatch(modal.open(KillProcessPopup.MODAL_TYPE, {instanceId, onSuccess}))
    },
    openWizard: (instanceId) => dispatch(pushHistory(`/process/${instanceId}/wizard`))
});

export default connect(mapStateToProps, mapDispatchToProps)(ProcessStatusPage);

export {actions, reducers, sagas};