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

import {ConcordKey, GenericOperationResult} from '../../../api/common';
import { ProjectVisibility } from '../../../api/org/project';
import {NewProjectForm, NewProjectFormValues} from '../../molecules';
import {RequestErrorActivity} from "../index";
import {useCallback, useState} from "react";
import {createOrUpdate as apiCreate} from "../../../api/org/project";
import {useApi} from "../../../hooks/useApi";
import {LoadingDispatch} from "../../../App";
import {Redirect} from "react-router";

interface ExternalProps {
    orgName: ConcordKey;
}

const INIT_VALUES : NewProjectFormValues = {
    name: '',
    visibility: ProjectVisibility.PRIVATE,
    description: ''
}

const NewProjectActivity = (props: ExternalProps) => {
    const {orgName} = props;

    const dispatch = React.useContext(LoadingDispatch);
    const [values, setValues] = useState(INIT_VALUES);

    const postQuery = useCallback(() => {
        return apiCreate(orgName, values);
    }, [orgName, values]);

    const { error, isLoading, data, fetch } = useApi<GenericOperationResult>(postQuery, {
        fetchOnMount: false,
        requestByFetch: true,
        dispatch: dispatch
    });

    const handleSubmit = useCallback(
        (values: NewProjectFormValues) => {
            setValues(values);
            fetch();
        },
        [fetch]
    );

    if (data) {
        return <Redirect to={`/org/${orgName}/project/${values.name}`} />;
    }

    return (
        <>
            {error && <RequestErrorActivity error={error} />}
            <NewProjectForm
                orgName={orgName}
                submitting={isLoading}
                onSubmit={handleSubmit}
                initial={INIT_VALUES}
            />
        </>
    );
};

export default NewProjectActivity;

/*

interface StateProps {
    submitting: boolean;
    error: RequestError;
}

interface DispatchProps {
    submit: (values: NewProjectFormValues) => void;
}

type Props = ExternalProps & StateProps & DispatchProps;

class NewProjectActivity extends React.PureComponent<Props> {
    render() {
        const { error, submitting, submit, orgName } = this.props;

        return (
            <>
                {error && <RequestErrorMessage error={error} />}
                <NewProjectForm
                    orgName={orgName}
                    submitting={submitting}
                    onSubmit={submit}
                    initial={{
                        name: '',
                        visibility: ProjectVisibility.PRIVATE,
                        description: ''
                    }}
                />
            </>
        );
    }
}

const mapStateToProps = ({ projects }: { projects: State }): StateProps => ({
    submitting: projects.loading,
    error: projects.error
});

const mapDispatchToProps = (
    dispatch: Dispatch<AnyAction>,
    { orgName }: ExternalProps
): DispatchProps => ({
    submit: (values: NewProjectFormValues) => dispatch(actions.createProject(orgName, values))
});

export default connect(mapStateToProps, mapDispatchToProps)(NewProjectActivity);
*/
