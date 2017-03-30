import React, {Component, PropTypes} from "react";
import {Button, Dropdown, Form, Header, Input, Label, Loader} from "semantic-ui-react";
import * as constants from "./constants";

class ProcessForm extends Component {

    renderField(f) {
        let value = this.state ? this.state[f.name] : undefined;
        if (value === undefined) {
            value = f.value;
        }

        switch (f.type) {
            case "string":
                return this.renderStringField(f, value);
            case "int":
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

        const options = allowedValue.map(v => ({ text: v, value: v }));
        const multiple = cardinality === constants.cardinality.AT_LEAST_ONE ||
                cardinality === constants.cardinality.ANY;

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

    handleSubmit(ev) {
        ev.preventDefault();
        this.props.onSubmitFn(this.state);
    }

    handleReturn(ev) {
        ev.preventDefault();
        this.props.onReturnFn();
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
        const {data, loading, submitting, completed} = this.props;

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
    data: PropTypes.object,
    loading: PropTypes.bool,
    submitting: PropTypes.bool,
    completed: PropTypes.bool,
    onSubmitFn: PropTypes.func.isRequired,
    onReturnFn: PropTypes.func.isRequired
};

export default ProcessForm;