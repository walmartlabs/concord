import _some from 'lodash-es/some'
import _isFunction from 'lodash-es/isFunction'
import _escapeRegExp from 'lodash-es/escapeRegExp'
import _deburr from 'lodash-es/deburr'
import _includes from 'lodash-es/includes'
import _filter from 'lodash-es/filter'
import React from 'react'
import { DropdownProps } from '../Dropdown'
import { DropdownItemProps } from '../DropdownItem'

/**
 * An option rendered by the dropdown menu. Options gained from `allowAdditions`
 * carry the runtime-only `data-additional` flag.
 */
export interface DropdownMenuItemProps extends DropdownItemProps {
  'data-additional'?: boolean
}

export interface GetMenuOptionsConfig {
  additionLabel?: React.ReactNode
  additionPosition?: 'top' | 'bottom'
  allowAdditions?: boolean
  deburr?: boolean
  multiple?: boolean
  options?: DropdownItemProps[]
  search?: DropdownProps['search']
  searchQuery?: string
  value?: DropdownProps['value']
}

// Only the `multiple` branches below read `value` as a list
type MultipleValue = (boolean | number | string)[]

// Filter and augment flat option objects for the dropdown's search menu.
export default function getMenuOptions(config: GetMenuOptionsConfig): DropdownMenuItemProps[] {
  const {
    additionLabel,
    additionPosition,
    allowAdditions,
    deburr,
    multiple,
    options,
    search,
    searchQuery,
    value,
  } = config

  let filteredOptions = options as DropdownMenuItemProps[]

  if (multiple) {
    filteredOptions = _filter(filteredOptions, (opt) => !_includes(value as MultipleValue, opt.value))
  }

  if (search && searchQuery) {
    if (_isFunction(search)) {
      // lodash's type guard only narrows to `Function`, the callback shape comes from the prop
      const searchOptions = search as unknown as (
        options: DropdownItemProps[],
        searchQuery: string,
      ) => DropdownItemProps[]

      filteredOptions = searchOptions(filteredOptions, searchQuery)
    } else {
      const strippedQuery = deburr ? _deburr(searchQuery) : searchQuery
      const re = new RegExp(_escapeRegExp(strippedQuery), 'i')

      // `text` is the rendered label, hence a string for searchable options
      filteredOptions = _filter(filteredOptions, (opt) =>
        re.test(deburr ? _deburr(opt.text as string) : (opt.text as string)),
      )
    }
  }

  if (allowAdditions && search && searchQuery && !_some(filteredOptions, { text: searchQuery })) {
    const additionLabelElement = React.isValidElement(additionLabel)
      ? React.cloneElement(additionLabel, { key: 'addition-label' })
      : additionLabel === undefined
        ? 'Add '
        : additionLabel
    const addItem: DropdownMenuItemProps = {
      key: 'addition',
      text: [
        additionLabelElement,
        React.createElement('b', { key: 'addition-query' }, searchQuery),
      ],
      value: searchQuery,
      className: 'addition',
      'data-additional': true,
    }
    filteredOptions = filteredOptions.slice()
    if (additionPosition === 'bottom') filteredOptions.push(addItem)
    else filteredOptions.unshift(addItem)
  }

  return filteredOptions
}

getMenuOptions.handledProps = []
