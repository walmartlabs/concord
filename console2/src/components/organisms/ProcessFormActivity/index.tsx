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
import { push as pushHistory } from 'connected-react-router';
import { Loader, Segment } from 'semantic-ui-react';
import { ConcordId, RequestError } from '../../../api/common';
import { FormInstanceEntry, FormSubmitErrors } from '../../../api/process/form';
import { actions } from '../../../state/data/forms';
import { FormDataType, State } from '../../../state/data/forms';
import { ProcessForm, RequestErrorMessage } from '../../molecules';

interface ExternalProps {
    processInstanceId: ConcordId;
    formName: string;
    wizard: boolean;
}

interface StateProps {
    form?: FormInstanceEntry;
    loading: boolean;
    loadError?: RequestError;
    submitting: boolean;
    submitError?: RequestError;
    validationErrors?: FormSubmitErrors;
    completed: boolean;
}

interface DispatchProps {
    load: (processInstanceId: ConcordId, formName: string) => void;
    onSubmit: (
        processInstanceId: ConcordId,
        formName: string,
        data: FormDataType,
        yieldFlow: boolean
    ) => void;
    onReturn: (processInstanceId: ConcordId) => void;
    onStartForm: (processInstanceId: ConcordId, formName: string) => void;
}

type Props = ExternalProps & StateProps & DispatchProps;

class ProcessFormActivity extends React.PureComponent<Props> {
    componentDidMount() {
        this.init();
    }

    componentDidUpdate(prevProps: Props) {
        if (
            this.props.processInstanceId !== prevProps.processInstanceId ||
            this.props.formName !== prevProps.formName
        ) {
            this.init();
        }
    }

    init() {
        const { load, processInstanceId, formName } = this.props;
        load(processInstanceId, formName);
    }

    render() {
        const {
            form,
            loading,
            loadError,
            onSubmit,
            onReturn,
            processInstanceId,
            formName,
            validationErrors,
            submitting,
            submitError,
            completed,
            onStartForm
        } = this.props;

        if (loading) {
            return <Loader active={true} />;
        }

        if (loadError) {
            return <RequestErrorMessage error={loadError} />;
        }

        if (!form || !form.fields) {
            return <h3>Form not found.</h3>;
        }

        return (
            <>
                {form.custom && (
                    <Segment>
                        This form has a {/* eslint-disable-next-line jsx-a11y/anchor-is-valid */}
                        <a href="#" onClick={() => onStartForm(processInstanceId, formName)}>
                            custom view
                        </a>
                        .
                    </Segment>
                )}

                <ProcessForm
                    form={form}
                    submitting={submitting}
                    submitError={submitError}
                    completed={completed}
                    errors={validationErrors}
                    onSubmit={(values) => onSubmit(processInstanceId, formName, values, form.yield)}
                    onReturn={() => onReturn(processInstanceId)}
                />
            </>
        );
    }
}

const mapStateToProps = ({ forms }: { forms: State }): StateProps => ({
    form: forms.get.response ? forms.get.response : undefined,
    loading: forms.get.running,
    loadError: forms.get.error,
    submitting: forms.submit.running,
    submitError: forms.submit.error,
    validationErrors: forms.submit.response ? forms.submit.response.errors : undefined,
    completed: forms.submit.response ? forms.submit.response.ok : false
});

const mapDispatchToProps = (
    dispatch: Dispatch<AnyAction>,
    { wizard }: ExternalProps
): DispatchProps => ({
    load: (processInstanceId, formName) => {
        dispatch(actions.reset());
        dispatch(actions.getProcessForm(processInstanceId, formName));
    },

    onSubmit: (processInstanceId, formName, data, yieldFlow) =>
        dispatch(actions.submitProcessForm(processInstanceId, formName, wizard, yieldFlow, data)),

    onReturn: (processInstanceId) => dispatch(pushHistory(`/process/${processInstanceId}`)),

    onStartForm: (processInstanceId, formName) =>
        dispatch(actions.startForm(processInstanceId, formName))
});

export default connect(
    mapStateToProps,
    mapDispatchToProps
)(ProcessFormActivity);
