import React, {Component} from "react";
import PropTypes from "prop-types";
import {connect} from "react-redux";
import {push as pushHistory} from "react-router-redux";
import {Button, Dropdown, Form, Header, Input, Label, Loader} from "semantic-ui-react";
import ErrorMessage from "../../shared/ErrorMessage";
import * as actions from "./actions";
import * as selectors from "./reducers";
import reducers from "./reducers";
import sagas from "./sagas";

export const cardinalityTypes = {
    ONE_OR_NONE: "ONE_OR_NONE",
    ONE_AND_ONLY_ONE: "ONE_ANY_ONLY_ONE",
    AT_LEAST_ONE: "AT_LEAST_ONE",
    ANY: "ANY"
};

class ProcessForm extends Component {

    componentDidMount() {
        this.load();
    }

    load() {
        const {instanceId, formInstanceId, loadData} = this.props;
        loadData(instanceId, formInstanceId);
    }

    handleSubmit(ev) {
        ev.preventDefault();

        let values = this.state;
        if (!values) {
            values = {};
        }

        const {instanceId, formInstanceId, onSubmitFn, data: {fields}, wizard} = this.props;

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

        onSubmitFn(instanceId, formInstanceId, values, wizard);
    }

    renderField(f) {
        let value = this.state ? this.state[f.name] : undefined;
        if (value === undefined) {
            value = f.value;
        }

        switch (f.type) {
            case "string":
                return this.renderStringField(f, value);
            case "int":
            case "decimal":
                return this.renderIntField(f, value);
            default:
                return <p key={f.name}>Unknown field type: {f.type}</p>
        }
    }

    // TODO extract the common stuff
    renderStringField({name, label, type, cardinality, allowedValue}, value) {
        const {data: {errors}} = this.props;
        const error = errors ? errors[name] : undefined;

        // TODO check cardinality
        const dropdown = allowedValue instanceof Array;

        return <Form.Field key={name} error={error && true}>
            <label>{label}</label>

            { dropdown ? this.renderDropdown(name, cardinality, value, allowedValue) : this.renderInput(name, type, value) }

            { error && <Label basic color="red" pointing>{error}</Label> }
        </Form.Field>
    }

    renderIntField({name, label, type}, value) {
        const {data: {errors}} = this.props;
        const error = errors ? errors[name] : undefined;

        return <Form.Field key={name} error={error && true}>
            <label>{label}</label>

            { this.renderInput(name, type, value, "number") }

            { error && <Label basic color="red" pointing>{error}</Label> }
        </Form.Field>
    }

    renderInput(name, type, value, inputType) {
        const {submitting, completed} = this.props;
        return <Input name={name}
                      disabled={submitting || completed}
                      defaultValue={value}
                      type={inputType}
                      onChange={this.handleInput(name, type)}/>;
    }

    renderDropdown(name, cardinality, value, allowedValue) {
        const {submitting, completed} = this.props;

        const options = allowedValue.map(v => ({text: v, value: v}));
        const multiple = cardinality === cardinalityTypes.AT_LEAST_ONE ||
            cardinality === cardinalityTypes.ANY;

        if (value === null) {
            value = undefined;
        }

        return <Dropdown selection
                         multiple={multiple}
                         name={name}
                         disabled={submitting || completed}
                         value={value}
                         options={options}
                         onChange={this.handleDropdown(name)}/>;
    }

    handleReturn(ev) {
        ev.preventDefault();
        const {instanceId, onReturnFn} = this.props;
        onReturnFn(instanceId);
    }

    handleInput(fieldName, type) {
        return ({target}) => {
            let v = target.value;
            if (type === "int") {
                v = target.valueAsNumber;
            }

            let o = {};
            o[fieldName] = v;
            this.setState(o);
        };
    }

    handleDropdown(fieldName) {
        return (ev, {value}) => {
            let o = {};
            o[fieldName] = value;
            this.setState(o);
        };
    }

    render() {
        let {fetchError, submitError, data, loading, submitting, completed} = this.props;

        if (fetchError) {
            return <ErrorMessage message={fetchError} retryFn={() => this.load()}/>;
        }

        if (submitError) {
            return <ErrorMessage message={submitError}/>;
        }

        loading = loading || !data;
        if (loading) {
            return <Loader active size="massive"/>
        }

        return <div>
            <Header as="h2">{data.name}</Header>
            <Form onSubmit={(ev) => this.handleSubmit(ev)}>
                { data.fields && data.fields.map(f => this.renderField(f)) }

                { completed ? <Button icon="check" primary content="Return to the process page"
                                      onClick={(ev) => this.handleReturn(ev)}/> :
                    <Button type="submit" primary disabled={submitting} content="Submit"/> }
            </Form>
        </div>;
    }
}

ProcessForm.propTypes = {
    instanceId: PropTypes.string.isRequired,
    formInstanceId: PropTypes.string.isRequired,
    wizard: PropTypes.bool,
    data: PropTypes.object,
    loading: PropTypes.bool,
    submitting: PropTypes.bool,
    completed: PropTypes.bool,
    fetchError: PropTypes.string,
    submitError: PropTypes.string,
    loadData: PropTypes.func.isRequired,
    onSubmitFn: PropTypes.func.isRequired,
    onReturnFn: PropTypes.func.isRequired
};

const mapStateToProps = ({form}, {params, location: {query}}) => ({
    instanceId: params.instanceId,
    formInstanceId: params.formInstanceId,
    wizard: query.wizard,
    data: selectors.getData(form),
    loading: selectors.getIsLoading(form),
    submitting: selectors.getIsSubmitting(form),
    completed: selectors.getIsCompleted(form),
    fetchError: selectors.getFetchError(form),
    submitError: selectors.getSubmitError(form)
});

const mapDispatchToProps = (dispatch) => ({
    loadData: (instanceId, formInstanceId) => dispatch(actions.loadData(instanceId, formInstanceId)),
    onSubmitFn: (instanceId, formInstanceId, data, wizard) => dispatch(actions.submit(instanceId, formInstanceId, data, wizard)),
    onReturnFn: (instanceId) => dispatch(pushHistory(`/process/${instanceId}`))
});

export default connect(mapStateToProps, mapDispatchToProps)(ProcessForm);

export {reducers, sagas};