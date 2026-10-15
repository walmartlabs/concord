import _includes from 'lodash-es/includes'
import _compact from 'lodash-es/compact'
import _map from 'lodash-es/map'
import _every from 'lodash-es/every'
import _without from 'lodash-es/without'
import _find from 'lodash-es/find'
import _dropRight from 'lodash-es/dropRight'
import _isEmpty from 'lodash-es/isEmpty'
import _size from 'lodash-es/size'
import _difference from 'lodash-es/difference'
import _union from 'lodash-es/union'
import _get from 'lodash-es/get'
import _noop from 'lodash-es/noop'
import _invoke from 'lodash-es/invoke'
import _has from 'lodash-es/has'
import _isEqual from 'lodash-es/isEqual'
import _pick from 'lodash-es/pick'
import _isNil from 'lodash-es/isNil'
import EventStack from '@/lib/event-stack'
import { Ref } from '@fluentui/react-component-ref'
import cx from 'clsx'
import keyboardKey from 'keyboard-key'

import React, { Children, cloneElement, useEffect, useRef } from 'react'
import shallowEqual from 'shallowequal'
import { SemanticShorthandContent, SemanticShorthandItem } from '../../generic'
import Icon, { IconProps } from '../../elements/Icon/Icon'
import Label, { LabelProps } from '../../elements/Label/Label'
import {
  childrenUtils,
  doesNodeContainClick,
  getElementType,
  getUnhandledProps,
  useAutoControlledState,
  useKeyOnly,
  useKeyOrValueAndKey,
} from '../../lib'
import useIsomorphicLayoutEffect from '../../lib/hooks/useIsomorphicLayoutEffect'
import DropdownDivider from './DropdownDivider'
import DropdownItem, { DropdownItemProps } from './DropdownItem'
import DropdownMenu from './DropdownMenu'
import DropdownSearchInput, { DropdownSearchInputProps } from './DropdownSearchInput'
import DropdownText from './DropdownText'
import getMenuOptions, { DropdownMenuItemProps } from './utils/getMenuOptions'
import getSelectedIndex from './utils/getSelectedIndex'

export interface DropdownProps
  extends Omit<
    React.HTMLAttributes<HTMLDivElement>,
    | 'children'
    | 'content'
    | 'defaultValue'
    | 'id'
    | 'tabIndex'
    | 'onChange'
    | 'onBlur'
    | 'onFocus'
    | 'onClick'
    | 'onMouseDown'
  > {
  as?: React.ElementType
  id?: string | number
  allowAdditions?: boolean
  basic?: boolean
  button?: boolean
  children?: React.ReactNode
  clearable?: boolean
  compact?: boolean
  defaultValue?: string | number | boolean | (number | string | boolean)[]
  disabled?: boolean
  error?: boolean
  fluid?: boolean
  icon?: SemanticShorthandItem<IconProps>
  labeled?: boolean
  loading?: boolean
  multiple?: boolean
  name?: string
  onAddItem?: (event: React.SyntheticEvent<HTMLElement>, data: DropdownProps) => void
  onBlur?: (event: React.FocusEvent<HTMLElement>, data: DropdownProps) => void
  onChange?: (event: React.SyntheticEvent<HTMLElement>, data: DropdownProps) => void
  onClick?: (event: React.MouseEvent<HTMLElement>, data: DropdownProps) => void
  onFocus?: (event: React.FocusEvent<HTMLElement>, data: DropdownProps) => void
  onMouseDown?: (event: React.MouseEvent<HTMLElement>, data: DropdownProps) => void
  options?: DropdownItemProps[]
  placeholder?: string
  pointing?:
    | boolean
    | 'left'
    | 'right'
    | 'top'
    | 'top left'
    | 'top right'
    | 'bottom'
    | 'bottom left'
    | 'bottom right'
  search?: boolean | ((options: DropdownItemProps[], value: string) => DropdownItemProps[])
  selectOnBlur?: boolean
  selection?: boolean
  tabIndex?: number | string
  text?: string
  value?: boolean | number | string | (boolean | number | string)[]
}

export interface StrictDropdownProps extends DropdownProps {}

interface DropdownState {
  focus?: boolean
  open?: boolean
  searchQuery?: string
  selectedIndex?: number
  selectedLabel?: number | string
  upward?: boolean
  value?: DropdownProps['value']
  // These values are stored only for a comparison on next getAutoControlledStateFromProps()
  __options?: DropdownItemProps[]
  __value?: DropdownProps['value']
}

/**
 * Props that are part of the runtime implementation, but are intentionally absent
 * from the public API.
 */
interface DropdownPrivateProps {
  additionLabel?: React.ReactNode
  additionPosition?: 'top' | 'bottom'
  deburr?: boolean
}

interface DropdownAriaOptions extends React.AriaAttributes {
  role?: React.AriaRole
}

/** Dropdown handlers are invoked both from React and from native (EventStack) events. */
type DropdownEvent = React.SyntheticEvent<HTMLElement> | Event

// `value` is a list in every `multiple` branch below
type MultipleValue = (boolean | number | string)[]

const getKeyOrValue = (key: React.Key | undefined, value: unknown): React.Key | undefined =>
  // an item `value` may be a boolean, which is still used as-is as the key
  _isNil(key) ? (value as React.Key | undefined) : key

const getKeyAndValues = (
  options?: DropdownItemProps[],
): Array<Pick<DropdownItemProps, 'key' | 'value'>> | undefined =>
  options ? options.map((option) => _pick(option, ['key', 'value'])) : options

const renderItemContent = (item: DropdownItemProps): { content: SemanticShorthandContent } => ({
  content: item.text,
})

/**
 * A dropdown allows a user to select a value from a series of options.
 * @see Form
 * @see Select
 * @see Menu
 */
function Dropdown(props: DropdownProps): React.ReactElement {
  const ref = useRef<HTMLDivElement | null>(null)
  const searchRef = useRef<HTMLInputElement | null>(null)
  const sizerRef = useRef<HTMLSpanElement | null>(null)

  const isMouseDownRef = useRef(false)

  // Callbacks passed to `close()` are flushed once the closed state is committed, they
  // replace the second argument of `this.setState(partial, callback)`.
  const closeCallbacksRef = useRef<Array<() => void>>([])

  // Previous props & state of the last committed render, they replace the arguments of
  // `componentDidUpdate()`.
  const prevPropsRef = useRef<DropdownProps | null>(null)
  const prevStateRef = useRef<DropdownState | null>(null)

  const getAutoControlledStateFromProps = (
    nextProps: DropdownProps,
    computedState: DropdownState,
    prevState: DropdownState,
  ): Partial<DropdownState> => {
    // These values are stored only for a comparison on next getAutoControlledStateFromProps()
    const derivedState: Partial<DropdownState> = {
      __options: nextProps.options,
      __value: computedState.value,
    }

    const { additionLabel, additionPosition, deburr } =
      nextProps as DropdownProps & DropdownPrivateProps

    // The selected index is only dependent:
    const shouldComputeSelectedIndex =
      // On value change
      !shallowEqual(prevState.__value, computedState.value) ||
      // On option keys/values, we only check those properties to avoid recursive performance impacts.
      !_isEqual(getKeyAndValues(nextProps.options), getKeyAndValues(prevState.__options))

    if (shouldComputeSelectedIndex) {
      derivedState.selectedIndex = getSelectedIndex({
        additionLabel,
        additionPosition,
        allowAdditions: nextProps.allowAdditions,
        deburr,
        multiple: nextProps.multiple,
        search: nextProps.search,
        selectedIndex: computedState.selectedIndex,
        value: computedState.value,
        options: nextProps.options,
        searchQuery: computedState.searchQuery,
      })
    }

    return derivedState
  }

  const [state, setState] = useAutoControlledState<DropdownProps, DropdownState>(props, {
    autoControlledProps: ['value'],
    initialState: { focus: false, searchQuery: '' },
    getAutoControlledStateFromProps,
  })

  const handleChange = (e: DropdownEvent, value: DropdownProps['value']): void => {
    _invoke(props, 'onChange', e, { ...props, value })
  }

  const closeOnChange = (e: React.SyntheticEvent<HTMLElement>): void => {
    if (!props.multiple) {
      close(e, _noop)
    }
  }

  const closeOnEscape = (e: KeyboardEvent): void => {
    if (keyboardKey.getCode(e) !== keyboardKey.Escape) return

    e.preventDefault()
    close(e)
  }

  const moveSelectionOnKeyDown = (e: React.KeyboardEvent<HTMLElement>): void => {
    const { multiple } = props
    const { open } = state

    if (!open) {
      return
    }

    const moves: Record<number, number> = {
      [keyboardKey.ArrowDown]: 1,
      [keyboardKey.ArrowUp]: -1,
    }
    const move = moves[keyboardKey.getCode(e)]

    if (move === undefined) {
      return
    }

    e.preventDefault()

    const nextIndex = getSelectedIndexAfterMove(move)

    if (!multiple) {
      makeSelectedItemActive(e, nextIndex)
    }

    setState({ selectedIndex: nextIndex })
  }

  const openOnSpace = (e: React.KeyboardEvent<HTMLElement>): void => {
    const shouldHandleEvent =
      state.focus && !state.open && keyboardKey.getCode(e) === keyboardKey.Spacebar
    const target = e.target as HTMLElement | null
    const shouldPreventDefault =
      target?.tagName !== 'INPUT' &&
      target?.tagName !== 'TEXTAREA' &&
      target?.isContentEditable !== true

    if (shouldHandleEvent) {
      if (shouldPreventDefault) {
        e.preventDefault()
      }

      open(e)
    }
  }

  const openOnArrow = (e: React.KeyboardEvent<HTMLElement>): void => {
    const { focus, open: isOpen } = state

    if (focus && !isOpen) {
      const code = keyboardKey.getCode(e)

      if (code === keyboardKey.ArrowDown || code === keyboardKey.ArrowUp) {
        e.preventDefault()

        open(e)
      }
    }
  }

  const makeSelectedItemActive = (
    e: React.SyntheticEvent<HTMLElement>,
    selectedIndex: number,
  ): DropdownProps['value'] => {
    const { open, value } = state
    const { multiple } = props

    const item = getSelectedItem(selectedIndex)
    const selectedValue = _get(item, 'value')

    // prevent selecting null if there was no selected item value
    // prevent selecting duplicate items when the dropdown is closed
    if (_isNil(selectedValue) || !open) {
      return value
    }

    // state value may be undefined
    const newValue = multiple ? _union(value as MultipleValue, [selectedValue]) : selectedValue
    const valueHasChanged = multiple
      ? !!_difference(newValue as MultipleValue, value as MultipleValue).length
      : newValue !== value

    if (valueHasChanged) {
      // notify the onChange prop that the user is trying to change value
      setState({ value: newValue })

      handleChange(e, newValue)

      // Heads up! This event handler should be called after `onChange`
      // Notify the onAddItem prop if this is a new value
      if (item['data-additional']) {
        _invoke(props, 'onAddItem', e, { ...props, value: selectedValue })
      }
    }

    return value
  }

  const selectItemOnEnter = (e: React.KeyboardEvent<HTMLElement>): void => {
    const { search } = props
    const { open, selectedIndex } = state

    if (!open) {
      return
    }

    const shouldSelect =
      keyboardKey.getCode(e) === keyboardKey.Enter ||
      // https://github.com/Semantic-Org/Semantic-UI-React/pull/3766
      (!search && keyboardKey.getCode(e) === keyboardKey.Spacebar)

    if (!shouldSelect) {
      return
    }

    e.preventDefault()

    const { additionLabel, additionPosition, deburr } =
      props as DropdownProps & DropdownPrivateProps

    const optionSize = _size(
      getMenuOptions({
        value: state.value,
        options: props.options,
        searchQuery: state.searchQuery,
        additionLabel,
        additionPosition,
        allowAdditions: props.allowAdditions,
        deburr,
        multiple: props.multiple,
        search: props.search,
      }),
    )

    if (search && optionSize === 0) {
      return
    }

    const nextValue = makeSelectedItemActive(e, selectedIndex)

    // This is required as selected value may be the same
    setState({
      selectedIndex: getSelectedIndex({
        additionLabel,
        additionPosition,
        allowAdditions: props.allowAdditions,
        deburr,
        multiple: props.multiple,
        search: props.search,
        selectedIndex,
        value: nextValue,
        options: props.options,
        searchQuery: '',
      }),
    })

    closeOnChange(e)

    clearSearchQuery()

    if (search) {
      _invoke(searchRef.current, 'focus')
    }
  }

  const removeItemOnBackspace = (e: KeyboardEvent): void => {
    const { multiple, search } = props
    const { searchQuery, value } = state

    if (keyboardKey.getCode(e) !== keyboardKey.Backspace) return
    if (searchQuery || !search || !multiple || _isEmpty(value)) return

    e.preventDefault()

    // remove most recent value
    const newValue = _dropRight(value as MultipleValue)

    setState({ value: newValue })

    handleChange(e, newValue)
  }

  const closeOnDocumentClick = (e: MouseEvent): void => {
    if (ref.current && doesNodeContainClick(ref.current, e)) return

    close()
  }

  const handleMouseDown = (e: React.MouseEvent<HTMLElement>): void => {
    isMouseDownRef.current = true

    _invoke(props, 'onMouseDown', e, props)

    document.addEventListener('mouseup', handleDocumentMouseUp)
  }

  const handleDocumentMouseUp = (): void => {
    isMouseDownRef.current = false
    document.removeEventListener('mouseup', handleDocumentMouseUp)
  }

  const handleClick = (e: React.MouseEvent<HTMLElement>): void => {
    const { search } = props
    const { open: isOpen } = state

    _invoke(props, 'onClick', e, props)

    e.stopPropagation()

    if (!search) return toggle(e)

    if (isOpen) {
      _invoke(searchRef.current, 'focus')
      return
    }

    open(e)
  }

  const handleIconClick = (e: React.MouseEvent<HTMLElement>): void => {
    const { clearable } = props

    const hasValue = hasAnyValue()

    _invoke(props, 'onClick', e, props)
    // prevent handleClick()
    e.stopPropagation()

    if (clearable && hasValue) {
      clearValue(e)
    } else {
      toggle(e)
    }
  }

  const handleItemClick = (e: React.MouseEvent<HTMLElement>, item: DropdownMenuItemProps): void => {
    const { multiple, search } = props
    const currentValue = state.value
    const { value } = item

    // prevent toggle() in handleClick()
    e.stopPropagation()

    // prevent closeOnDocumentClick() if multiple or item is disabled
    if (multiple || item.disabled) {
      e.nativeEvent.stopImmediatePropagation()
    }

    if (item.disabled) {
      return
    }

    const isAdditionItem = item['data-additional']
    const newValue = multiple ? _union(state.value as MultipleValue, [value]) : value
    const valueHasChanged = multiple
      ? !!_difference(newValue as MultipleValue, currentValue as MultipleValue).length
      : newValue !== currentValue

    // notify the onChange prop that the user is trying to change value
    if (valueHasChanged) {
      setState({ value: newValue })

      handleChange(e, newValue)
    }

    clearSearchQuery()

    if (search) {
      _invoke(searchRef.current, 'focus')
    } else {
      _invoke(ref.current, 'focus')
    }

    closeOnChange(e)

    // Heads up! This event handler should be called after `onChange`
    // Notify the onAddItem prop if this is a new value
    if (isAdditionItem) {
      _invoke(props, 'onAddItem', e, { ...props, value })
    }
  }

  const handleFocus = (e: React.FocusEvent<HTMLElement>): void => {
    const { focus } = state

    if (focus) return

    _invoke(props, 'onFocus', e, props)

    setState({ focus: true })
  }

  const handleBlur = (e: React.FocusEvent<HTMLElement>): void => {
    // Heads up! Don't remove this.
    // https://github.com/Semantic-Org/Semantic-UI-React/issues/1315
    const currentTarget = _get(e, 'currentTarget')

    if (currentTarget && currentTarget.contains(document.activeElement)) return

    const { multiple, selectOnBlur } = props

    if (isMouseDownRef.current) return

    _invoke(props, 'onBlur', e, props)

    if (selectOnBlur && !multiple) {
      makeSelectedItemActive(e, state.selectedIndex)
    }

    setState({ focus: false })

    clearSearchQuery()
  }

  const handleSearchChange = (
    e: React.ChangeEvent<HTMLInputElement>,
    { value }: { value?: string },
  ): void => {
    e.stopPropagation()

    const { open: isOpen } = state

    setState({ searchQuery: value, selectedIndex: 0 })

    if (!isOpen && value.length > 0) {
      open()
    }
  }

  const handleKeyDown = (e: React.KeyboardEvent<HTMLElement>): void => {
    moveSelectionOnKeyDown(e)

    openOnArrow(e)

    openOnSpace(e)

    selectItemOnEnter(e)

    _invoke(props, 'onKeyDown', e)
  }

  const getSelectedItem = (selectedIndex: number): DropdownMenuItemProps | undefined => {
    const { additionLabel, additionPosition, deburr } =
      props as DropdownProps & DropdownPrivateProps

    const options = getMenuOptions({
      value: state.value,
      options: props.options,
      searchQuery: state.searchQuery,
      additionLabel,
      additionPosition,
      allowAdditions: props.allowAdditions,
      deburr,
      multiple: props.multiple,
      search: props.search,
    })

    return _get(options, `[${selectedIndex}]`)
  }

  const getItemByValue = (value: DropdownItemProps['value']): DropdownItemProps | undefined => {
    const { options } = props

    return _find(options, { value })
  }

  const getDropdownAriaOptions = (): DropdownAriaOptions => {
    const { loading, disabled, search, multiple } = props
    const { open } = state

    const ariaOptions: DropdownAriaOptions = {
      role: search ? 'combobox' : 'listbox',
      'aria-busy': loading,
      'aria-disabled': disabled,
      'aria-expanded': !!open,
    }

    if (ariaOptions.role === 'listbox') {
      ariaOptions['aria-multiselectable'] = multiple
    }

    return ariaOptions
  }

  const clearSearchQuery = (): void => {
    const { searchQuery } = state

    if (searchQuery === undefined || searchQuery === '') return

    setState({ searchQuery: '' })
  }

  const handleLabelClick = (e: React.MouseEvent<HTMLElement>, labelProps: LabelProps): void => {
    // prevent focusing search input on click
    e.stopPropagation()

    setState({ selectedLabel: labelProps.value })
  }

  const handleLabelRemove = (e: React.MouseEvent<HTMLElement>, labelProps: LabelProps): void => {
    // prevent focusing search input on click
    e.stopPropagation()

    const { value } = state
    const newValue = _without(value as MultipleValue, labelProps.value)

    setState({ value: newValue })

    handleChange(e, newValue)
  }

  const getSelectedIndexAfterMove = (
    offset: number,
    startIndex: number = state.selectedIndex,
  ): number | undefined => {
    const { additionLabel, additionPosition, deburr } =
      props as DropdownProps & DropdownPrivateProps

    const options = getMenuOptions({
      value: state.value,
      options: props.options,
      searchQuery: state.searchQuery,
      additionLabel,
      additionPosition,
      allowAdditions: props.allowAdditions,
      deburr,
      multiple: props.multiple,
      search: props.search,
    })

    // Prevent infinite loop
    // TODO: remove left part of condition after children API will be removed
    if (options === undefined || _every(options, 'disabled')) return

    const lastIndex = options.length - 1
    let nextIndex = startIndex + offset

    if (nextIndex > lastIndex) {
      nextIndex = 0
    } else if (nextIndex < 0) {
      nextIndex = lastIndex
    }

    if (options[nextIndex].disabled) {
      return getSelectedIndexAfterMove(offset, nextIndex)
    }

    return nextIndex
  }

  const handleIconOverrides = (predefinedProps: Partial<IconProps>) => {
    const { clearable } = props

    const classes = cx(clearable && hasAnyValue() && 'clear', predefinedProps.className)

    return {
      className: classes,
      onClick: (e: React.MouseEvent<HTMLElement>) => {
        _invoke(predefinedProps, 'onClick', e, predefinedProps)

        handleIconClick(e)
      },
    }
  }

  const clearValue = (e: React.MouseEvent<HTMLElement>): void => {
    const { multiple } = props

    const newValue = multiple ? [] : ''

    setState({ value: newValue })

    handleChange(e, newValue)
  }

  const computeSearchInputTabIndex = (): number | string => {
    const { disabled, tabIndex } = props

    if (!_isNil(tabIndex)) return tabIndex
    return disabled ? -1 : 0
  }

  const computeSearchInputWidth = (): number | undefined => {
    const { searchQuery } = state

    if (sizerRef.current && searchQuery) {
      // resize the search input, temporarily show the sizer so we can measure it
      sizerRef.current.style.display = 'inline'
      sizerRef.current.textContent = searchQuery

      const searchWidth = Math.ceil(sizerRef.current.getBoundingClientRect().width)

      sizerRef.current.style.removeProperty('display')

      return searchWidth
    }
  }

  const computeTabIndex = (): number | string | undefined => {
    const { disabled, search, tabIndex } = props

    // don't set a root node tabIndex as the search input has its own tabIndex
    if (search) return undefined
    if (disabled) return -1
    return _isNil(tabIndex) ? 0 : tabIndex
  }

  const handleSearchInputOverrides = (predefinedProps: Partial<DropdownSearchInputProps>) => ({
    onChange: (e: React.ChangeEvent<HTMLInputElement>, inputProps?: { value?: string }) => {
      _invoke(predefinedProps, 'onChange', e, inputProps)

      handleSearchChange(e, inputProps)
    },
  })

  const hasAnyValue = (): boolean => {
    const { multiple } = props
    const { value } = state

    return multiple ? !_isEmpty(value) : !_isNil(value) && value !== ''
  }

  const scrollSelectedItemIntoView = (): void => {
    if (!ref.current) return

    const menu = ref.current.querySelector<HTMLElement>('.menu.visible')

    if (!menu) return

    const item = menu.querySelector<HTMLElement>('.item.selected')
    if (!item) return

    const isOutOfUpperView = item.offsetTop < menu.scrollTop
    const isOutOfLowerView =
      item.offsetTop + item.clientHeight > menu.scrollTop + menu.clientHeight

    if (isOutOfUpperView) {
      menu.scrollTop = item.offsetTop
    } else if (isOutOfLowerView) {
      menu.scrollTop = item.offsetTop + item.clientHeight - menu.clientHeight
    }
  }

  const setOpenDirection = (): void => {
    if (!ref.current) return

    const menu = ref.current.querySelector<HTMLElement>('.menu.visible')

    if (!menu) return

    const dropdownRect = ref.current.getBoundingClientRect()

    const menuHeight = menu.clientHeight
    const spaceAtTheBottom =
      document.documentElement.clientHeight - dropdownRect.top - dropdownRect.height - menuHeight
    const spaceAtTheTop = dropdownRect.top - menuHeight
    const upward = spaceAtTheBottom < 0 && spaceAtTheTop > spaceAtTheBottom

    // set state only if there's a relevant difference
    if (!upward !== !state.upward) {
      setState({ upward })
    }
  }

  const open = (
    e: React.SyntheticEvent<HTMLElement> | null = null,
    triggerSetState: boolean = true,
  ): void => {
    const { disabled, search } = props

    if (disabled) return
    if (search) _invoke(searchRef.current, 'focus')

    if (triggerSetState) {
      setState({ open: true })
    }

    scrollSelectedItemIntoView()
  }

  const handleClose = (): void => {
    const hasSearchFocus = document.activeElement === searchRef.current

    // https://github.com/Semantic-Org/Semantic-UI-React/issues/627
    // Blur the Dropdown on close so it is blurred after selecting an item.
    // This is to prevent it from re-opening when switching tabs after selecting an item.
    if (!hasSearchFocus && ref.current) {
      ref.current.blur()
    }

    const hasDropdownFocus = document.activeElement === ref.current
    const hasFocus = hasSearchFocus || hasDropdownFocus
    // We need to keep the virtual model in sync with the browser focus change
    // https://github.com/Semantic-Org/Semantic-UI-React/issues/692
    setState({ focus: hasFocus })
  }

  const close = (e: DropdownEvent | null = null, callback: () => void = handleClose): void => {
    if (state.open) {
      closeCallbacksRef.current.push(callback)

      setState({ open: false })
    }
  }

  const toggle = (e: React.SyntheticEvent<HTMLElement>): void =>
    state.open ? close(e) : open(e)

  const renderText = (): React.ReactNode => {
    const { multiple, placeholder, search, text } = props
    const { searchQuery, selectedIndex, value, open } = state

    const hasValue = hasAnyValue()
    const classes = cx(placeholder && !hasValue && 'default', 'text', search && searchQuery && 'filtered')

    let _text: string | undefined = placeholder
    let selectedItem: DropdownItemProps | undefined

    if (text) {
      _text = text
    } else if (open && !multiple) {
      selectedItem = getSelectedItem(selectedIndex)
    } else if (hasValue) {
      selectedItem = getItemByValue(value as DropdownItemProps['value'])
    }

    return DropdownText.create(selectedItem ? renderItemContent(selectedItem) : _text, {
      defaultProps: { className: classes },
    })
  }

  const renderSearchInput = (): React.ReactNode => {
    const { search } = props
    const { searchQuery } = state

    // the shorthand factory returns a node, the search input is always an element here
    const searchInput = DropdownSearchInput.create('text', {
      defaultProps: {
        style: { width: computeSearchInputWidth() },
        // the dropdown `tabIndex` prop allows strings, the input only accepts numbers
        tabIndex: computeSearchInputTabIndex() as number,
        value: searchQuery,
      },
      overrideProps: handleSearchInputOverrides,
    }) as React.ReactElement

    // `DropdownSearchInput` has to stay a plain function component (its shorthand
    // factory rejects exotic components), so it cannot forward a ref to the rendered
    // `<input />`. The fluentui `Ref` resolves that DOM node for us here.
    return search && <Ref innerRef={searchRef}>{searchInput}</Ref>
  }

  const renderSearchSizer = (): React.ReactNode => {
    const { search, multiple } = props

    return search && multiple && <span className="sizer" ref={sizerRef} />
  }

  const renderLabels = (): React.ReactNode => {
    const { multiple } = props
    const { selectedLabel, value } = state

    if (!multiple || _isEmpty(value)) return

    const selectedItems = _map(value as MultipleValue, getItemByValue)

    return _map(_compact(selectedItems), (item) => {
      return Label.create(renderItemContent(item), {
        defaultProps: {
          active: item.value === selectedLabel,
          as: 'a',
          key: getKeyOrValue(item.key, item.value),
          onClick: handleLabelClick,
          onRemove: handleLabelRemove,
          // dropdown item values may be booleans, `Label` only accepts strings & numbers
          value: item.value as LabelProps['value'],
        },
      })
    })
  }

  const renderOptions = (): React.ReactNode => {
    const { multiple, search } = props
    const { selectedIndex, value } = state

    const { additionLabel, additionPosition, deburr } =
      props as DropdownProps & DropdownPrivateProps
    const options = getMenuOptions({
      value,
      options: props.options,
      searchQuery: state.searchQuery,
      additionLabel,
      additionPosition,
      allowAdditions: props.allowAdditions,
      deburr,
      multiple,
      search,
    })

    if (search && _isEmpty(options)) {
      return React.createElement('div', { className: 'message' }, 'No results found.')
    }

    const isActive = multiple
      ? (optValue: DropdownItemProps['value']) => _includes(value as MultipleValue, optValue)
      : (optValue: DropdownItemProps['value']) => optValue === value

    return _map(options, (opt, i) => {
      const itemProps: DropdownMenuItemProps = {
        active: isActive(opt.value),
        onClick: handleItemClick,
        selected: selectedIndex === i,
        ...opt,
        key: getKeyOrValue(opt.key, opt.value),
        style: { ...opt.style, pointerEvents: 'all' },
      }

      return DropdownItem.create(itemProps)
    })
  }

  const renderMenu = (): React.ReactNode => {
    const { open } = state
    const ariaOptions = getDropdownMenuAriaOptions()
    const { children } = props

    if (!childrenUtils.isNil(children)) {
      const menuChild = Children.only(children) as React.ReactElement
      const className = cx(useKeyOnly(open, 'visible'), menuChild.props.className)

      return cloneElement(menuChild, { className, ...ariaOptions })
    }

    return (
      <DropdownMenu {...ariaOptions} open={open}>
        {renderOptions()}
      </DropdownMenu>
    )
  }

  // ----------------------------------------
  // Document Event Handlers
  // ----------------------------------------

  const getDropdownMenuAriaOptions = (): DropdownAriaOptions => {
    const { search, multiple } = props
    const ariaOptions: DropdownAriaOptions = {}

    if (search) {
      ariaOptions['aria-multiselectable'] = multiple
      ariaOptions.role = 'listbox'
    }

    return ariaOptions
  }

  // Replaces `componentDidMount()`
  useEffect(() => {
    if (state.open) {
      open(null, false)
    }
  }, [])

  // Replaces `componentDidUpdate()`
  useIsomorphicLayoutEffect(() => {
    const prevProps = prevPropsRef.current
    const prevState = prevStateRef.current

    prevPropsRef.current = props
    prevStateRef.current = state

    // `componentDidUpdate()` is not called on mount
    if (!prevProps || !prevState) return

    if (process.env.NODE_ENV !== 'production') {
      // in development, validate value type matches dropdown type
      const isNextValueArray = Array.isArray(props.value)

      const hasValue = _has(props, 'value')

      if (hasValue && props.multiple && !isNextValueArray) {
        console.error(
          'Dropdown `value` must be an array when `multiple` is set.' +
            ` Received type: \`${Object.prototype.toString.call(props.value)}\`.`,
        )
      } else if (hasValue && !props.multiple && isNextValueArray) {
        console.error(
          'Dropdown `value` must not be an array when `multiple` is not set.' +
            ' Either set `multiple={true}` or use a string or number value.',
        )
      }
    }

    // focused / blurred
    if (!prevState.focus && state.focus) {
      if (!isMouseDownRef.current && !state.open) open()
    } else if (prevState.focus && !state.focus) {
      if (!isMouseDownRef.current) close()
    }

    // opened / closed
    if (!prevState.open && state.open) {
      setOpenDirection()
      scrollSelectedItemIntoView()
    }

    if (prevState.selectedIndex !== state.selectedIndex) {
      scrollSelectedItemIntoView()
    }
  })

  // Flushes the callbacks passed to `close()`, they replace the callback of
  // `this.setState(partial, callback)` that is invoked once the update is committed.
  useEffect(() => {
    if (closeCallbacksRef.current.length === 0) return

    const callbacks = closeCallbacksRef.current
    closeCallbacksRef.current = []
    callbacks.forEach((callback) => callback())
  })

  const {
    basic,
    button,
    className,
    compact,
    disabled,
    error,
    fluid,
    icon,
    labeled,
    loading,
    multiple,
    pointing,
    search,
    selection,
  } = props
  const { focus, upward } = state

  const classes = cx(
    'ui',
    useKeyOnly(state.open, 'active visible'),
    useKeyOnly(disabled, 'disabled'),
    useKeyOnly(error, 'error'),
    useKeyOnly(loading, 'loading'),
    useKeyOnly(basic, 'basic'),
    useKeyOnly(button, 'button'),
    useKeyOnly(compact, 'compact'),
    useKeyOnly(fluid, 'fluid'),
    useKeyOnly(labeled, 'labeled'),
    useKeyOnly(multiple, 'multiple'),
    useKeyOnly(!!search, 'search'),
    useKeyOnly(selection, 'selection'),
    useKeyOnly(upward, 'upward'),
    useKeyOrValueAndKey(pointing, 'pointing'),
    'dropdown',
    className,
  )

  const rest = getUnhandledProps(Dropdown, props)
  const ElementType = getElementType(Dropdown, props)
  const ariaOptions = getDropdownAriaOptions()

  return (
    <Ref innerRef={ref}>
      {React.createElement(
        ElementType,
        {
          ...rest,
          ...ariaOptions,
          className: classes,
          onBlur: handleBlur,
          onClick: handleClick,
          onKeyDown: handleKeyDown,
          onMouseDown: handleMouseDown,
          onFocus: handleFocus,
          onChange: handleChange,
          tabIndex: computeTabIndex(),
        },
        renderLabels(),
        renderSearchInput(),
        renderSearchSizer(),
        renderText(),
        Icon.create(icon, { overrideProps: handleIconOverrides, autoGenerateKey: false }),
        renderMenu(),
        state.open && <EventStack name="keydown" on={closeOnEscape} />,
        state.open && <EventStack name="click" on={closeOnDocumentClick} />,
        focus && <EventStack name="keydown" on={removeItemOnBackspace} />,
      )}
    </Ref>
  )
}
Dropdown.Divider = DropdownDivider
Dropdown.Item = DropdownItem
Dropdown.Menu = DropdownMenu

Dropdown.handledProps = [
  'additionLabel',
  'additionPosition',
  'allowAdditions',
  'deburr',
  'as',
  'basic',
  'button',
  'children',
  'className',
  'clearable',
  'compact',
  'defaultValue',
  'disabled',
  'error',
  'fluid',
  'icon',
  'labeled',
  'loading',
  'multiple',
  'onAddItem',
  'onBlur',
  'onChange',
  'onClick',
  'onFocus',
  'onMouseDown',
  'options',
  'placeholder',
  'pointing',
  'search',
  'selectOnBlur',
  'selection',
  'tabIndex',
  'text',
  'value',
]



Dropdown.defaultProps = { icon: 'dropdown', selectOnBlur: true } satisfies Partial<DropdownProps>

export default Dropdown
