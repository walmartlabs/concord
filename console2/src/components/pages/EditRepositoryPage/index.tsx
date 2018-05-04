import * as React from 'react';
import { RouteComponentProps, withRouter } from 'react-router';
import { Link } from 'react-router-dom';
import { Breadcrumb, Container, Header, Segment } from 'semantic-ui-react';
import { ConcordKey } from '../../../api/common';

import { EditRepositoryActivity } from '../../organisms';

interface RouteProps {
    orgName: ConcordKey;
    projectName: ConcordKey;
    repoName: ConcordKey;
}

class AddRepositoryPage extends React.PureComponent<RouteComponentProps<RouteProps>> {
    render() {
        const { orgName, projectName, repoName } = this.props.match.params;

        return (
            <>
                <Segment basic={true}>
                    <Breadcrumb size="big">
                        <Breadcrumb.Section>
                            <Link to={`/org/${orgName}/project/${projectName}/repository`}>
                                {projectName}
                            </Link>
                        </Breadcrumb.Section>
                        <Breadcrumb.Divider />
                        <Breadcrumb.Section active={true}>Edit Repository</Breadcrumb.Section>
                    </Breadcrumb>
                </Segment>

                <Segment basic={true}>
                    <Container text={true}>
                        <Header>
                            <Header.Content>Edit a Repository</Header.Content>
                        </Header>
                        <EditRepositoryActivity
                            orgName={orgName}
                            projectName={projectName}
                            repoName={repoName}
                        />
                    </Container>
                </Segment>
            </>
        );
    }
}

export default withRouter(AddRepositoryPage);
