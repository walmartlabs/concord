import * as React from 'react';
import { connect, Dispatch } from 'react-redux';

import { ConcordKey, RequestError } from '../../../api/common';
import { ProjectEntry } from '../../../api/org/project';
import { EditRepositoryEntry } from '../../../api/org/project/repository';
import { testRepository } from '../../../api/service/console';
import { actions, selectors, State } from '../../../state/data/projects';
import { RepositoryForm, RepositoryFormValues, RequestErrorMessage } from '../../molecules';
import { RepositorySourceType } from '../../molecules/RepositoryForm';

interface ExternalProps {
    orgName: ConcordKey;
    projectName: ConcordKey;

    /** defined for edit, undefined for new repos */
    repoName?: ConcordKey;
}

interface StateProps {
    submitting: boolean;
    error: RequestError;
    initial?: RepositoryFormValues;
}

interface DispatchProps {
    load: () => void;
    submit: (values: RepositoryFormValues) => void;
}

type Props = ExternalProps & StateProps & DispatchProps;

class EditRepositoryActivity extends React.PureComponent<Props> {
    componentDidMount() {
        const { repoName, load } = this.props;
        if (repoName) {
            load();
        }
    }

    render() {
        const { error, submitting, submit, orgName, projectName, initial } = this.props;

        return (
            <>
                {error && <RequestErrorMessage error={error} />}
                <RepositoryForm
                    orgName={orgName}
                    projectName={projectName}
                    onSubmit={submit}
                    submitting={submitting}
                    editMode={true}
                    initial={
                        initial || {
                            name: '',
                            url: '',
                            sourceType: RepositorySourceType.BRANCH_OR_TAG
                        }
                    }
                    testRepository={({ name, sourceType, id, ...rest }) =>
                        testRepository({ orgName, ...rest })
                    }
                />
            </>
        );
    }
}

const notEmpty = (s: string | undefined): string | undefined => {
    if (!s) {
        return;
    }

    if (s === '') {
        return;
    }

    return s;
};

const findRepo = (p?: ProjectEntry, repoName?: ConcordKey) => {
    if (!p || !p.repositories || !repoName) {
        return;
    }

    return p.repositories[repoName];
};

const toFormValues = (
    p?: ProjectEntry,
    repoName?: ConcordKey
): RepositoryFormValues | undefined => {
    const r = findRepo(p, repoName);
    if (!r) {
        return;
    }

    const sourceType = r.commitId
        ? RepositorySourceType.COMMIT_ID
        : RepositorySourceType.BRANCH_OR_TAG;

    return {
        id: r.id,
        name: r.name,
        url: r.url,
        sourceType,
        branch: r.branch,
        commitId: r.commitId,
        path: r.path,
        secretName: r.secretName
    };
};

const mapStateToProps = (
    { projects }: { projects: State },
    { orgName, projectName, repoName }: ExternalProps
): StateProps => ({
    submitting: repoName ? projects.updateRepository.running : projects.createRepository.running,
    error: repoName ? projects.updateRepository.error : projects.createRepository.error,
    initial: toFormValues(selectors.projectByName(projects, orgName, projectName), repoName)
});

const mapDispatchToProps = (
    dispatch: Dispatch<{}>,
    { orgName, projectName, repoName }: ExternalProps
): DispatchProps => ({
    load: () => {
        dispatch(actions.getProject(orgName, projectName));
    },

    submit: (values: RepositoryFormValues) => {
        let branch = notEmpty(values.branch);
        if (values.sourceType !== RepositorySourceType.BRANCH_OR_TAG) {
            branch = undefined;
        }

        let commitId = notEmpty(values.commitId);
        if (values.sourceType !== RepositorySourceType.COMMIT_ID) {
            commitId = undefined;
        }

        const entry: EditRepositoryEntry = {
            id: values.id,
            name: values.name,
            url: values.url,
            branch,
            commitId,
            path: values.path,
            secretName: values.secretName!
        };

        if (repoName) {
            dispatch(actions.updateRepository(orgName, projectName, entry));
        } else {
            dispatch(actions.addRepository(orgName, projectName, entry));
        }
    }
});

export default connect(mapStateToProps, mapDispatchToProps)(EditRepositoryActivity);
