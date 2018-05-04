import * as React from 'react';
import { RouteComponentProps, withRouter } from 'react-router';
import { Link } from 'react-router-dom';
import { Breadcrumb, Segment } from 'semantic-ui-react';

import { ConcordId } from '../../../api/common';
import { ProcessFormActivity } from '../../organisms';

interface Props {
    processInstanceId: ConcordId;
    formInstanceId: string;
    mode?: 'step' | 'wizard';
}

class ProcessFormPage extends React.PureComponent<RouteComponentProps<Props>> {
    render() {
        const { processInstanceId, formInstanceId, mode } = this.props.match.params;

        return (
            <>
                <Segment basic={true}>
                    <Breadcrumb size="big">
                        <Breadcrumb.Section>
                            <Link to={`/process`}>Processes</Link>
                        </Breadcrumb.Section>
                        <Breadcrumb.Divider />
                        <Breadcrumb.Section>
                            <Link to={`/process/${processInstanceId}`}>{processInstanceId}</Link>
                        </Breadcrumb.Section>
                        <Breadcrumb.Divider />
                        <Breadcrumb.Section active={true}>Form</Breadcrumb.Section>
                    </Breadcrumb>
                </Segment>

                <ProcessFormActivity
                    processInstanceId={processInstanceId}
                    formInstanceId={formInstanceId}
                    wizard={mode === 'wizard'}
                />
            </>
        );
    }
}

export default withRouter(ProcessFormPage);
