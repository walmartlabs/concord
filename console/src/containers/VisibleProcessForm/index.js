import React, {Component} from "react";
import {connect} from "react-redux";
import {push as pushHistory} from "react-router-redux";
import ErrorMessage from "../../components/ErrorMessage";
import ProcessForm from "../../components/ProcessForm";
import {getProcessFormState} from "../../reducers";
import * as formActions from "./actions";
import * as formSelectors from "./reducers";
import {getProcessPath} from "../../routes";

class VisibleProcessForm extends Component {

    componentDidMount() {
        this.load();
    }

    load() {
        const {processInstanceId, formInstanceId, fetchData} = this.props;
        fetchData(processInstanceId, formInstanceId);
    }

    handleSubmit(values) {
        if (!values) {
            values = {};
        }

        const {processInstanceId, formInstanceId, onSubmitFn, data: {fields}, wizard} = this.props;

        for (let i = 0; i < fields.length; i++) {
            const f = fields[i];
            const k = f.name;
            const v = values[k];

            if (v === null || v === undefined) {
                values[k] = f.value;
            } else if (v === "") {
                values[k] = null;
            }
        }

        onSubmitFn(processInstanceId, formInstanceId, values, wizard);
    }

    render() {
        const {processInstanceId, fetchError, submitError, data, loading, submitting, completed, onReturnFn} = this.props;

        if (fetchError) {
            return <ErrorMessage message={fetchError} retryFn={() => this.load()}/>;
        }

        if (submitError) {
            return <ErrorMessage message={submitError}/>;
        }

        return <ProcessForm data={data}
                            loading={loading || !data}
                            submitting={submitting}
                            completed={completed}
                            onSubmitFn={(values) => this.handleSubmit(values)}
                            onReturnFn={() => onReturnFn(processInstanceId)}/>
    }
}

const mapStateToProps = (state, {params, location: {query}}) => ({
    processInstanceId: params.processId,
    formInstanceId: params.formId,
    wizard: query.wizard,
    data: formSelectors.getData(getProcessFormState(state)),
    loading: formSelectors.getIsLoading(getProcessFormState(state)),
    submitting: formSelectors.getIsSubmitting(getProcessFormState(state)),
    fetchError: formSelectors.getFetchError(getProcessFormState(state)),
    submitError: formSelectors.getSubmitError(getProcessFormState(state)),
    completed: formSelectors.getIsCompleted(getProcessFormState(state))
});

const mapDispatchToProps = (dispatch) => ({
    fetchData: (processInstanceId, formInstanceId) => dispatch(formActions.fetchData(processInstanceId, formInstanceId)),
    onSubmitFn: (processInstanceId, formInstanceId, data, wizard) => dispatch(formActions.submit(processInstanceId, formInstanceId, data, wizard)),
    onReturnFn: (processInstanceId) => dispatch(pushHistory(getProcessPath(processInstanceId)))
});

export default connect(mapStateToProps, mapDispatchToProps)(VisibleProcessForm);