
import React from 'react'
import Dropdown, { StrictDropdownProps } from '../../modules/Dropdown'
import { DropdownItemProps } from '../../modules/Dropdown/DropdownItem'

export interface StrictSelectProps
  extends Omit<StrictDropdownProps, 'children' | 'selection' | 'options'> {
  /** Array of Dropdown.Item props e.g. `{ text: '', value: '' }` */
  options: DropdownItemProps[]
}

export interface SelectProps extends StrictSelectProps {}

/**
 * A Select is sugar for <Dropdown selection />.
 * @see Dropdown
 * @see Form
 */
function Select(props: SelectProps): React.ReactElement {
  return React.createElement(Dropdown, { ...props, selection: true })
}

Select.handledProps = ['options']



export default Select
