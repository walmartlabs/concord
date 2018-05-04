import * as React from 'react';
import { connect, Dispatch } from 'react-redux';
import { Link } from 'react-router-dom';
import { Icon, List, Loader } from 'semantic-ui-react';

import { RequestError } from '../../../api/common';
import { ProjectEntry, ProjectVisibility } from '../../../api/org/project';
import { actions, State } from '../../../state/data/projects';
import { comparators } from '../../../utils';

import { RequestErrorMessage } from '../../molecules';

interface ExternalProps {
    orgName: string;
}

interface StateProps {
    projects: ProjectEntry[];
    loading: boolean;
    error: RequestError;
}

interface DispatchProps {
    load: () => void;
}

type Props = ExternalProps & StateProps & DispatchProps;

const ProjectVisibilityIcon = ({ project }: { project: ProjectEntry }) => {
    if (project.visibility === ProjectVisibility.PUBLIC) {
        return <Icon name="unlock" size="large" />;
    } else {
        return <Icon name="lock" color="red" size="large" />;
    }
};

class ProjectList extends React.PureComponent<Props> {
    componentDidMount() {
        this.props.load();
    }

    componentDidUpdate(prevProps: Props) {
        const { orgName: newOrgName } = this.props;
        const { orgName: oldOrgName } = prevProps;

        if (oldOrgName !== newOrgName) {
            this.props.load();
        }
    }

    render() {
        const { orgName, loading, projects, error } = this.props;

        if (error) {
            return <RequestErrorMessage error={error} />;
        }

        // TODO use dimmer

        if (loading) {
            return <Loader active={true} />;
        }

        if (projects.length === 0) {
            return <h3>No projects found</h3>;
        }

        return (
            <List divided={true} relaxed={true} size="large">
                {projects.map((project, index) => (
                    <List.Item key={index}>
                        <ProjectVisibilityIcon project={project} />
                        <List.Content>
                            <List.Header>
                                <Link to={`/org/${orgName}/project/${project.name}`}>
                                    {project.name}
                                </Link>
                            </List.Header>
                            <List.Description>
                                {project.description ? project.description : 'No description'}
                            </List.Description>
                        </List.Content>
                    </List.Item>
                ))}
            </List>
        );
    }
}

// TODO refactor as a selector?
const makeProjectList = (data: { [id: string]: ProjectEntry }): ProjectEntry[] =>
    Object.keys(data)
        .map((k) => data[k])
        .sort(comparators.byName);

const mapStateToProps = ({ projects }: { projects: State }): StateProps => ({
    projects: makeProjectList(projects.projectById),
    loading: projects.loading,
    error: projects.error
});

const mapDispatchToProps = (dispatch: Dispatch<{}>, { orgName }: ExternalProps): DispatchProps => ({
    load: () => dispatch(actions.listProjects(orgName))
});

export default connect(mapStateToProps, mapDispatchToProps)(ProjectList);
