
import React from 'react'
import Select, { StrictSelectProps } from '../../addons/Select'
import { DropdownItemProps } from '../../modules/Dropdown/DropdownItem'
import { getElementType, getUnhandledProps } from '../../lib'
import FormField, { StrictFormFieldProps } from './FormField'

export interface FormSelectProps extends StrictFormSelectProps {}

export interface StrictFormSelectProps
  extends StrictFormFieldProps,
    Omit<StrictSelectProps, 'error'> {
  /** An element type to render as (string or function). */
  as?: React.ElementType

  /** A FormField control prop. */
  control?: React.ElementType

  /** Array of Dropdown.Item props e.g. `{ text: '', value: '' }` */
  options: DropdownItemProps[]
}

/**
 * Sugar for <Form.Field control={Select} />.
 * @see Form
 * @see Select
 */
function FormSelect(props: FormSelectProps): React.ReactElement {
  const { control, options } = props
  const rest = getUnhandledProps(FormSelect, props)
  const ElementType = getElementType(FormSelect, props)

  return React.createElement(ElementType, { ...rest, control, options })
}

FormSelect.handledProps = ['as', 'control', 'options']



FormSelect.defaultProps = { as: FormField, control: Select } satisfies Partial<FormSelectProps>

export default FormSelect
