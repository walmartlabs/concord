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
import { connect, Dispatch } from 'react-redux';

import { ConcordKey, RequestError } from '../../../api/common';
import { actions, State } from '../../../state/data/projects';
import { SingleOperationPopup } from '../../molecules';

import './styles.css';

interface ExternalProps {
    orgName: ConcordKey;
    projectName: ConcordKey;
    repoName: ConcordKey;
    trigger: (onClick: () => void) => React.ReactNode;
}

interface DispatchProps {
    reset: () => void;
    onConfirm: () => void;
}

interface StateProps {
    validating: boolean;
    success: boolean;
    error: RequestError;
}

type Props = DispatchProps & ExternalProps & StateProps;

class ValidateRepositoryPopup extends React.Component<Props> {
    render() {
        const { trigger, validating, success, error, reset, onConfirm, repoName } = this.props;

        let title = 'Validate repository?';

        if (success) {
            title = 'Validation complete';
        }

        if (error) {
            title = 'Validation error';
        }

        return (
            <SingleOperationPopup
                trigger={trigger}
                title={title}
                introMsg={
                    <p>
                        This will run syntax validation on flows for <b>{repoName}</b> repository?
                    </p>
                }
                running={validating}
                success={success}
                successMsg={<p> Repository validated successfully.</p>}
                error={error}
                reset={reset}
                onConfirm={onConfirm}
            />
        );
    }
}

const mapStateToProps = ({ projects }: { projects: State }): StateProps => ({
    validating: projects.validateRepository.running,
    success: !!projects.validateRepository.response && projects.validateRepository.response.ok,
    error: projects.validateRepository.error
});

const mapDispatchToProps = (
    dispatch: Dispatch<{}>,
    { orgName, projectName, repoName }: ExternalProps
): DispatchProps => ({
    reset: () => dispatch(actions.resetRepository()),
    onConfirm: () => dispatch(actions.validateRepository(orgName, projectName, repoName))
});

export default connect(
    mapStateToProps,
    mapDispatchToProps
)(ValidateRepositoryPopup);
