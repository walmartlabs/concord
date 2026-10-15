import _includes from 'lodash-es/includes'
import _find from 'lodash-es/find'
import _findIndex from 'lodash-es/findIndex'
import _reduce from 'lodash-es/reduce'
import getMenuOptions, { DropdownMenuItemProps, GetMenuOptionsConfig } from './getMenuOptions'

export interface GetSelectedIndexConfig extends GetMenuOptionsConfig {
  selectedIndex?: number
}

export default function getSelectedIndex(config: GetSelectedIndexConfig): number | undefined {
  const { multiple, options, search, searchQuery, selectedIndex, value } = config

  const menuOptions = getMenuOptions({
    value,
    options,
    searchQuery,
    allowAdditions: config.allowAdditions,
    multiple,
    search,
  })

  const enabledIndexes = _reduce<DropdownMenuItemProps, number[]>(
    menuOptions,
    (memo, item, index) => {
      if (!item.disabled) memo.push(index)

      return memo
    },
    [],
  )

  let newSelectedIndex: number | undefined

  if (!selectedIndex || selectedIndex < 0) {
    const firstIndex = enabledIndexes[0]

    newSelectedIndex = multiple
      ? firstIndex
      : _findIndex(menuOptions, ['value', value]) || enabledIndexes[0]
  } else if (multiple) {
    newSelectedIndex = _find(enabledIndexes, (index) => index >= selectedIndex)

    if (selectedIndex >= menuOptions.length - 1) {
      newSelectedIndex = enabledIndexes[enabledIndexes.length - 1]
    }
  } else {
    const activeIndex = _findIndex(menuOptions, ['value', value])

    newSelectedIndex = _includes(enabledIndexes, activeIndex) ? activeIndex : undefined
  }

  if (!newSelectedIndex || newSelectedIndex < 0) {
    newSelectedIndex = enabledIndexes[0]
  }

  return newSelectedIndex
}
