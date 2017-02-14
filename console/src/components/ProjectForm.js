import React, {Component, PropTypes} from "react";
import {Form, Header, Message, Dimmer, Loader} from "semantic-ui-react";

const CustomInput = ({input, meta: {error, touched}, label, ...rest}) => (
    <Form.Input {...input} label={label} error={error && touched} {...rest}/>
);

const CustomSelect = ({input: {name, value, onChange}, type, meta: {error, touched}, label, ...rest}) => {
    const onChangeFn = (ev, data) => {
        ev.target.value = data.value;
        return onChange(ev);
    };
    return <Form.Select name={name} value={value} onChange={onChangeFn} label={label}
                        error={error && touched} {...rest}/>;
};

const notEmpty = (v) => {
    return v !== undefined && v !== null;
};

class ProjectForm extends Component {

    render() {
        const {Field, onSubmitFn, isNew, loading, templates, invalid, error, submitting, submitSucceeded} = this.props;

        return (<div>
            <Header as="h3">Project</Header>
            <Form error={invalid} onSubmit={onSubmitFn}>
                <Dimmer active={loading} inverted><Loader/></Dimmer>

                <Message error content={error}/>
                {submitSucceeded && <Message>Project saved successfully</Message>}

                <Field component={CustomInput} name="name" label="Name" disabled={!isNew} required autoComplete="off"/>

                <Field component={CustomSelect}
                       name="templates"
                       label="Templates"
                       type="select-multiple"
                       options={templates.options}
                       loading={templates.loading}
                       disabled={templates.loading}
                       error={notEmpty(templates.error)}
                       multiple/>

                <Form.Button primary type="submit" loading={submitting} disabled={loading || submitting}>Save</Form.Button>
            </Form>
        </div>);
    }
}

ProjectForm.propTypes = {
    Field: PropTypes.any.isRequired,
    onSubmitFn: PropTypes.func.isRequired,
    isNew: PropTypes.bool,
    loading: PropTypes.bool,
    templates: PropTypes.object.isRequired
};

export default ProjectForm;