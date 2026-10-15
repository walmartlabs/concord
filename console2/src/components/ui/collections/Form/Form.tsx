import _invoke from 'lodash-es/invoke'
import cx from 'clsx'

import React from 'react'
import { getElementType, getUnhandledProps, useKeyOnly } from '../../lib'
import FormButton from './FormButton'
import FormCheckbox from './FormCheckbox'
import FormDropdown from './FormDropdown'
import FormField from './FormField'
import FormGroup from './FormGroup'
import FormInput from './FormInput'
import FormSelect from './FormSelect'

export interface FormProps extends StrictFormProps {}

export interface StrictFormProps extends Omit<React.FormHTMLAttributes<HTMLFormElement>, 'onSubmit'> {
  /** An element type to render as (string or function). */
  as?: React.ElementType

  /** Automatically show any error Message children. */
  error?: boolean

  /** Automatically show a loading indicator. */
  loading?: boolean

  /** The HTML form submit handler. */
  onSubmit?: (event: React.FormEvent<HTMLFormElement>, data: FormProps) => void
}

export interface FormComponent extends React.FunctionComponent<FormProps> {
  Field: typeof FormField
  Button: typeof FormButton
  Checkbox: typeof FormCheckbox
  Dropdown: typeof FormDropdown
  Group: typeof FormGroup
  Input: typeof FormInput
  Select: typeof FormSelect
}

/**
 * A Form displays a set of related user input fields in a structured way.
 * @see Button
 * @see Checkbox
 * @see Dropdown
 * @see Input
 * @see Message
 * @see Select
 */
function Form(props: FormProps): React.ReactElement {
  const { action, children, className, error, loading } = props

  const handleSubmit = (e: React.FormEvent<HTMLFormElement>, ...args: unknown[]): void => {
    // Heads up! Third party libs can pass own data as first argument, we need to check that it has preventDefault()
    // method.
    if (typeof action !== 'string') _invoke(e, 'preventDefault')

    _invoke(props, 'onSubmit', e, props, ...args)
  }

  const classes = cx(
    'ui',
    useKeyOnly(error, 'error'),
    useKeyOnly(loading, 'loading'),
    'form',
    className,
  )
  const rest = getUnhandledProps(Form, props)
  const ElementType = getElementType(Form, props)

  return React.createElement(
    ElementType,
    { ...rest, action, className: classes, onSubmit: handleSubmit },
    children,
  )
}

Form.Field = FormField
Form.Button = FormButton
Form.Checkbox = FormCheckbox
Form.Dropdown = FormDropdown
Form.Group = FormGroup
Form.Input = FormInput
Form.Select = FormSelect

Form.handledProps = ['action', 'as', 'children', 'className', 'error', 'loading', 'onSubmit']



Form.defaultProps = { as: 'form' } satisfies Partial<FormProps>

export default Form
