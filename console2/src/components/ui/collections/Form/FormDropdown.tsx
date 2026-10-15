
import React from 'react'
import Dropdown, { StrictDropdownProps } from '../../modules/Dropdown'
import { getElementType, getUnhandledProps } from '../../lib'
import FormField, { StrictFormFieldProps } from './FormField'

export interface FormDropdownProps extends StrictFormDropdownProps {}

export interface StrictFormDropdownProps
  extends StrictFormFieldProps,
    Omit<StrictDropdownProps, 'error'> {
  /** An element type to render as (string or function). */
  as?: React.ElementType

  /** A FormField control prop. */
  control?: React.ElementType
}

/**
 * Sugar for <Form.Field control={Dropdown} />.
 * @see Dropdown
 * @see Form
 */
function FormDropdown(props: FormDropdownProps): React.ReactElement {
  const { control } = props
  const rest = getUnhandledProps(FormDropdown, props)
  const ElementType = getElementType(FormDropdown, props)

  return React.createElement(ElementType, { ...rest, control })
}

FormDropdown.handledProps = ['as', 'control']



FormDropdown.defaultProps = { as: FormField, control: Dropdown } satisfies Partial<FormDropdownProps>

export default FormDropdown
