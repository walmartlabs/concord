/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2018 Walmart Inc.
 * -----
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * =====
 */

import * as React from 'react';
import { connect } from 'react-redux';
import { AnyAction, Dispatch } from 'redux';
import { Loader, Dimmer } from 'semantic-ui-react';
import { ConcordKey, RequestError } from '../../../api/common';
import { ProjectEntry } from '../../../api/org/project';
import { EditRepositoryEntry } from '../../../api/org/project/repository';
import { testRepository } from '../../../api/service/console';
import { actions, selectors, State } from '../../../state/data/projects';
import { RepositoryForm, RepositoryFormValues, RequestErrorMessage } from '../../molecules';
import { RepositorySourceType } from '../../molecules';

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
                <Dimmer active={submitting} inverted={true} page={true}>
                    <Loader active={submitting} size="massive" content={'Saving'} />
                </Dimmer>

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
                            enabled: true,
                            sourceType: RepositorySourceType.BRANCH_OR_TAG,
                            triggersEnabled: true
                        }
                    }
                    testRepository={({ name, sourceType, id, ...rest }) =>
                        testRepository({ orgName, projectName, ...rest })
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
        secretId: r.secretId,
        secretName: r.secretName,
        enabled: !r.disabled,
        triggersEnabled: !r.triggersDisabled
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
    dispatch: Dispatch<AnyAction>,
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
            secretId: values.secretId!,
            disabled: !values.enabled,
            triggersDisabled: !values.triggersEnabled
        };

        if (repoName) {
            dispatch(actions.updateRepository(orgName, projectName, entry));
        } else {
            dispatch(actions.addRepository(orgName, projectName, entry));
        }
    }
});

export default connect(mapStateToProps, mapDispatchToProps)(EditRepositoryActivity);
