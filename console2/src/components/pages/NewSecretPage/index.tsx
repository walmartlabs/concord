import * as React from 'react';
import { RouteComponentProps, withRouter } from 'react-router';
import { Link } from 'react-router-dom';
import { Breadcrumb, Container, Header, Segment } from 'semantic-ui-react';

import { ConcordKey } from '../../../api/common';
import { BreadcrumbSegment } from '../../molecules';
import { NewSecretActivity } from '../../organisms';

interface RouteProps {
    orgName: ConcordKey;
}

class NewSecretPage extends React.PureComponent<RouteComponentProps<RouteProps>> {
    render() {
        const { orgName } = this.props.match.params;

        return (
            <>
                <BreadcrumbSegment>
                    <Breadcrumb.Section>
                        <Link to={`/org/${orgName}/secret`}>{orgName}</Link>
                    </Breadcrumb.Section>
                    <Breadcrumb.Divider />
                    <Breadcrumb.Section active={true}>New Secret</Breadcrumb.Section>
                </BreadcrumbSegment>

                <Segment basic={true}>
                    <Container text={true}>
                        <Header>Create a New Secret</Header>
                        <NewSecretActivity orgName={orgName} />
                    </Container>
                </Segment>
            </>
        );
    }
}

export default withRouter(NewSecretPage);
