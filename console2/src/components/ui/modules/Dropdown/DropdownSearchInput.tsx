import _invoke from 'lodash-es/invoke'
import _get from 'lodash-es/get'
import cx from 'clsx'

import React from 'react'
import { createShorthandFactory, getUnhandledProps } from '../../lib'

export interface DropdownSearchInputProps extends React.InputHTMLAttributes<HTMLInputElement> {
  as?: React.ElementType
}

/**
 * A search item sub-component for Dropdown component.
 */
function DropdownSearchInput(props: DropdownSearchInputProps): React.ReactElement {
  const { autoComplete, className, tabIndex, type, value } = props

  const handleChange = (e: React.ChangeEvent<HTMLInputElement>): void => {
    const value: string = _get(e, 'target.value')

    _invoke(props, 'onChange', e, { ...props, value })
  }

  const classes = cx('search', className)
  const rest = getUnhandledProps(DropdownSearchInput, props)

  return React.createElement('input', {
    ...rest,
    'aria-autocomplete': 'list',
    autoComplete,
    className: classes,
    onChange: handleChange,
    tabIndex,
    type,
    value,
  })
}

DropdownSearchInput.handledProps = ['as', 'autoComplete', 'className', 'tabIndex', 'type', 'value']



DropdownSearchInput.defaultProps = { autoComplete: 'off', type: 'text' } satisfies Partial<DropdownSearchInputProps>

DropdownSearchInput.create = createShorthandFactory(DropdownSearchInput, (type: string) => ({ type }))

export default DropdownSearchInput
