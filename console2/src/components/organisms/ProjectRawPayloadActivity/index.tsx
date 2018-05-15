import * as React from 'react';
import { connect, Dispatch } from 'react-redux';
import { Form } from 'semantic-ui-react';
import { ConcordId, ConcordKey, RequestError } from '../../../api/common';
import { actions, State } from '../../../state/data/projects';
import { RequestErrorMessage } from '../../molecules';

interface ExternalProps {
    orgName: ConcordKey;
    projectId: ConcordId;
    acceptsRawPayload: boolean;
}

interface StateProps {
    updating: boolean;
    error: RequestError;
}

interface DispatchProps {
    update: (orgName: ConcordKey, projectId: ConcordId, acceptsRawPayload: boolean) => void;
}

type Props = ExternalProps & StateProps & DispatchProps;

class ProjectRenameActivity extends React.PureComponent<Props> {
    render() {
        const { error, updating, acceptsRawPayload, update, orgName, projectId } = this.props;

        return (
            <>
                {error && <RequestErrorMessage error={error} />}
                <Form loading={updating}>
                    <Form.Group>
                        <Form.Checkbox
                            toggle={true}
                            defaultChecked={acceptsRawPayload}
                            onChange={(ev, data) => update(orgName, projectId, !!data.checked)}
                        />

                        <p>
                            Allows users to start new processes using payload archives. When
                            disabled, only the configured repositories can be used to start a new
                            process.
                        </p>
                    </Form.Group>
                </Form>
            </>
        );
    }
}

const mapStateToProps = ({ projects }: { projects: State }): StateProps => ({
    updating: projects.acceptRawPayload.running,
    error: projects.acceptRawPayload.error
});

const mapDispatchToProps = (dispatch: Dispatch<{}>): DispatchProps => ({
    update: (orgName, projectId, acceptsRawPayload) =>
        dispatch(actions.setAcceptsRawPayload(orgName, projectId, acceptsRawPayload))
});

export default connect(mapStateToProps, mapDispatchToProps)(ProjectRenameActivity);
