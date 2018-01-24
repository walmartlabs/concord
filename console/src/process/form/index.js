/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 Wal-Mart Store, Inc.
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
import React, { Component } from 'react';
import PropTypes from 'prop-types';
import { connect } from 'react-redux';
import { push as pushHistory } from 'react-router-redux';
import { Button, Checkbox, Dropdown, Form, Header, Input, Label, Loader } from 'semantic-ui-react';
import ErrorMessage from '../../shared/ErrorMessage';
import * as actions from './actions';
import * as selectors from './reducers';
import reducers from './reducers';
import sagas from './sagas';

export const cardinalityTypes = {
  ONE_OR_NONE: 'ONE_OR_NONE',
  ONE_AND_ONLY_ONE: 'ONE_ANY_ONLY_ONE',
  AT_LEAST_ONE: 'AT_LEAST_ONE',
  ANY: 'ANY'
};

class ProcessForm extends Component {
  componentDidMount() {
    this.load();
  }

  load() {
    const { instanceId, formInstanceId, loadData } = this.props;
    loadData(instanceId, formInstanceId);
  }

  handleSubmit(ev) {
    ev.preventDefault();

    let values = this.state;
    if (!values) {
      values = {};
    }

    const {
      instanceId,
      formInstanceId,
      onSubmitFn,
      data: { fields },
      wizard,
      yieldFlow
    } = this.props;

    for (let i = 0; i < fields.length; i++) {
      const f = fields[i];
      const k = f.name;
      const v = values[k];

      if (v === null || v === undefined) {
        values[k] = f.value;
      } else if (v === '') {
        values[k] = null;
      }
    }

    onSubmitFn(instanceId, formInstanceId, values, wizard, yieldFlow);
  }

  renderField(f) {
    let value = this.state ? this.state[f.name] : undefined;
    if (value === undefined) {
      value = f.value;
    }

    switch (f.type) {
      case 'string':
        return this.renderStringField(f, value);
      case 'int':
      case 'decimal':
        return this.renderNumberField(f, value);
      case 'boolean':
        return this.renderBooleanField(f, value);
      case 'file':
        return this.renderFileField(f, value);
      default:
        return <p key={f.name}>Unknown field type: {f.type}</p>;
    }
  }

  // TODO extract the common stuff
  renderStringField({ name, label, type, cardinality, allowedValue, options }, value) {
    const { data: { errors } } = this.props;
    const error = errors ? errors[name] : undefined;
    const inputType = options ? options.inputType : undefined;

    // TODO check cardinality
    const dropdown = allowedValue instanceof Array;

    return (
      <Form.Field key={name} error={error && true}>
        <label>{label}</label>

        {dropdown
          ? this.renderDropdown(name, cardinality, value, allowedValue)
          : this.renderInput(name, type, value, inputType)}

        {error && (
          <Label basic color="red" pointing>
            {error}
          </Label>
        )}
      </Form.Field>
    );
  }

  renderNumberField({ name, label, type }, value) {
    const { data: { errors } } = this.props;
    const error = errors ? errors[name] : undefined;

    return (
      <Form.Field key={name} error={error && true}>
        <label>{label}</label>

        {this.renderInput(name, type, value, 'number', { step: type === 'decimal' ? 'any' : '1' })}

        {error && (
          <Label basic color="red" pointing>
            {error}
          </Label>
        )}
      </Form.Field>
    );
  }

  renderBooleanField({ name, label, type }, value) {
    const { data: { errors }, submitting, completed } = this.props;
    const error = errors ? errors[name] : undefined;

    return (
      <Form.Field key={name} error={error && true}>
        <label>{label}</label>

        <Checkbox
          name={name}
          disabled={submitting || completed}
          defaultChecked={value}
          onChange={this.handleCheckboxInput(name)}
        />

        {error && (
          <Label basic color="red" pointing>
            {error}
          </Label>
        )}
      </Form.Field>
    );
  }

  renderFileField({ name, label, type }, value) {
    const { data: { errors }, submitting, completed } = this.props;
    const error = errors ? errors[name] : undefined;

    return (
      <Form.Field key={name} error={error && true}>
        <label>{label}</label>

        <Input
          name={name}
          type="file"
          disabled={submitting || completed}
          onChange={this.handleInput(name, type)}
        />

        {error && (
          <Label basic color="red" pointing>
            {error}
          </Label>
        )}
      </Form.Field>
    );
  }

  renderInput(name, type, value, inputType, opts) {
    const { submitting, completed } = this.props;
    return (
      <Input
        name={name}
        disabled={submitting || completed}
        defaultValue={value}
        type={inputType}
        {...opts}
        onChange={this.handleInput(name, type)}
      />
    );
  }

  renderDropdown(name, cardinality, value, allowedValue) {
    const { submitting, completed } = this.props;

    const options = allowedValue.map((v) => ({ text: v, value: v }));
    const multiple =
      cardinality === cardinalityTypes.AT_LEAST_ONE || cardinality === cardinalityTypes.ANY;

    if (value === null) {
      value = undefined;
    }

    return (
      <Dropdown
        selection
        multiple={multiple}
        name={name}
        disabled={submitting || completed}
        value={value}
        options={options}
        onChange={this.handleDropdown(name)}
      />
    );
  }

  handleReturn(ev) {
    ev.preventDefault();
    const { instanceId, onReturnFn } = this.props;
    onReturnFn(instanceId);
  }

  handleCheckboxInput(fieldName) {
    return (ev, { checked }) => {
      let o = {};
      o[fieldName] = checked;
      this.setState(o);
    };
  }

  handleInput(fieldName, type) {
    return ({ target }) => {
      let v = target.value;
      if (type === 'int') {
        v = target.valueAsNumber;
      } else if (type === 'decimal') {
        v = target.valueAsNumber;
      } else if (type === 'boolean') {
        v = target.valueAsBoolean;
      } else if (type === 'file') {
        v = target.files[0];
      }

      let o = {};
      o[fieldName] = v;
      this.setState(o);
    };
  }

  handleDropdown(fieldName) {
    return (ev, { value }) => {
      let o = {};
      o[fieldName] = value;
      this.setState(o);
    };
  }

  render() {
    let { fetchError, submitError, data, loading, submitting, completed } = this.props;

    if (fetchError) {
      return <ErrorMessage message={fetchError} retryFn={() => this.load()} />;
    }

    if (submitError) {
      return <ErrorMessage message={submitError} />;
    }

    loading = loading || !data;
    if (loading) {
      return <Loader active size="massive" />;
    }

    return (
      <div>
        <Header as="h2">{data.name}</Header>
        <Form onSubmit={(ev) => this.handleSubmit(ev)}>
          {data.fields && data.fields.map((f) => this.renderField(f))}

          {completed ? (
            <Button
              icon="check"
              primary
              content="Return to the process page"
              onClick={(ev) => this.handleReturn(ev)}
            />
          ) : (
            <Button type="submit" primary disabled={submitting} content="Submit" />
          )}
        </Form>
      </div>
    );
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

const mapStateToProps = ({ processForm }, { params, location: { query } }) => ({
  instanceId: params.instanceId,
  formInstanceId: params.formInstanceId,
  wizard: query.wizard === 'true',
  yieldFlow: query.yieldFlow === 'true',
  data: selectors.getData(processForm),
  loading: selectors.getIsLoading(processForm),
  submitting: selectors.getIsSubmitting(processForm),
  completed: selectors.getIsCompleted(processForm),
  fetchError: selectors.getFetchError(processForm),
  submitError: selectors.getSubmitError(processForm)
});

const mapDispatchToProps = (dispatch) => ({
  loadData: (instanceId, formInstanceId) => dispatch(actions.loadData(instanceId, formInstanceId)),
  onSubmitFn: (instanceId, formInstanceId, data, wizard, yieldFlow) =>
    dispatch(actions.submit(instanceId, formInstanceId, data, wizard, yieldFlow)),
  onReturnFn: (instanceId) => dispatch(pushHistory(`/process/${instanceId}`))
});

export default connect(mapStateToProps, mapDispatchToProps)(ProcessForm);

export { reducers, sagas };
