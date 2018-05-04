import * as React from 'react';
import { RouteComponentProps, withRouter } from 'react-router';

import { ConcordId } from '../../../api/common';
import { ProcessWizard } from '../../organisms';

interface Props {
    instanceId: ConcordId;
}

class ProcessWizardPage extends React.PureComponent<RouteComponentProps<Props>> {
    render() {
        const { instanceId } = this.props.match.params;
        return <ProcessWizard processInstanceId={instanceId} />;
    }
}

export default withRouter(ProcessWizardPage);
