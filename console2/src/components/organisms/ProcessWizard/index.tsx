import * as React from 'react';
import { connect, Dispatch } from 'react-redux';
import { Loader } from 'semantic-ui-react';

import { ConcordId, RequestError } from '../../../api/common';
import { actions, State } from '../../../state/data/forms';
import { RequestErrorMessage } from '../../molecules';

interface ExternalProps {
    processInstanceId: ConcordId;
}

interface StateProps {
    error?: RequestError;
}

interface DispatchProps {
    start: (processInstanceId: ConcordId) => void;
}

type Props = ExternalProps & StateProps & DispatchProps;

class ProcessWizard extends React.PureComponent<Props> {
    componentDidMount() {
        const { start, processInstanceId } = this.props;
        start(processInstanceId);
    }

    render() {
        const { error } = this.props;
        if (error) {
            return <RequestErrorMessage error={error} />;
        }
        return (
            <div>
                <Loader active={true} />
            </div>
        );
    }
}

const mapStateToProps = ({ forms }: { forms: State }): StateProps => ({
    error: forms.wizard.error
});

const mapDispatchToProps = (dispatch: Dispatch<{}>): DispatchProps => ({
    start: (processInstanceId: ConcordId) => dispatch(actions.startWizard(processInstanceId))
});

export default connect(mapStateToProps, mapDispatchToProps)(ProcessWizard);
