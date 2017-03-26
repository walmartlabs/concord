import React, {Component, PropTypes} from "react";
import {Button, Form, Header, Input, Label, Loader} from "semantic-ui-react";

class ProcessForm extends Component {

    renderField(f) {
        switch (f.type) {
            case "string":
                return this.renderStringField(f);
            case "int":
                return this.renderIntField(f);
            default:
                return <p key={f.name}>Unknown field type: {f.type}</p>
        }
    }

    renderStringField({name, label, type, value}) {
        const {data: {errors}, submitting, completed} = this.props;
        const error = errors ? errors[name] : undefined;

        return <Form.Field key={name} error={error && true}>
            <label>{label}</label>

            <Input name={name}
                   disabled={submitting || completed}
                   defaultValue={value}
                   onChange={this.handleValue(name, type)}/>

            { error && <Label basic color="red" pointing>{error}</Label> }
        </Form.Field>
    }

    renderIntField({name, label, type, value}) {
        const {data: {errors}, submitting, completed} = this.props;
        const error = errors ? errors[name] : undefined;

        return <Form.Field key={name} error={error && true}>
            <label>{label}</label>

            <Input name={name}
                   disabled={submitting || completed}
                   defaultValue={value}
                   type="number"
                   onChange={this.handleValue(name, type)}/>

            { error && <Label basic color="red" pointing>{error}</Label> }
        </Form.Field>
    }

    handleSubmit(ev) {
        ev.preventDefault();
        this.props.onSubmitFn(this.state);
    }

    handleReturn(ev) {
        ev.preventDefault();
        this.props.onReturnFn();
    }

    handleValue(fieldName, type) {
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

    render() {
        const {data, loading, submitting, completed} = this.props;

        if (loading) {
            return <Loader active size="massive"/>
        }

        return <div>
            <Header as="h2">{data.name}</Header>
            <Form onSubmit={(ev) => this.handleSubmit(ev)}>
                { data.fields && data.fields.map(f => this.renderField(f)) }

                { completed ? <Button icon="check" primary content="Return to the process page" onClick={(ev) => this.handleReturn(ev)}/> :
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