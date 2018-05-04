import * as React from 'react';
import { RouteComponentProps, withRouter } from 'react-router';
import { Link } from 'react-router-dom';
import { Breadcrumb } from 'semantic-ui-react';

import { ConcordId } from '../../../api/common';
import { BreadcrumbSegment } from '../../molecules';
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
                <BreadcrumbSegment>
                    <Breadcrumb.Section>
                        <Link to={`/process`}>Processes</Link>
                    </Breadcrumb.Section>
                    <Breadcrumb.Divider />
                    <Breadcrumb.Section>
                        <Link to={`/process/${processInstanceId}`}>{processInstanceId}</Link>
                    </Breadcrumb.Section>
                    <Breadcrumb.Divider />
                    <Breadcrumb.Section active={true}>Form</Breadcrumb.Section>
                </BreadcrumbSegment>

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
