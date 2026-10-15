import _get from 'lodash-es/get'
import _invoke from 'lodash-es/invoke'
import _isEmpty from 'lodash-es/isEmpty'
import _isPlainObject from 'lodash-es/isPlainObject'
import _isUndefined from 'lodash-es/isUndefined'
import _map from 'lodash-es/map'
import cx from 'clsx'
import keyboardKey from 'keyboard-key'

import React, { useCallback, useEffect, useRef } from 'react'
import shallowEqual from 'shallowequal'
import Input, { InputProps } from '../../elements/Input'
import { SemanticShorthandItem } from '../../generic'
import {
  eventStack,
  getElementType,
  getUnhandledProps,
  htmlInputAttrs,
  isBrowser,
  partitionHTMLProps,
  useAutoControlledState,
  useKeyOnly,
} from '../../lib'
import SearchResult, { SearchResultProps } from './SearchResult'
import SearchResults from './SearchResults'

export interface SearchProps
  extends StrictSearchProps,
    Omit<React.HTMLAttributes<HTMLElement>, keyof StrictSearchProps> {}

export interface StrictSearchProps {
  as?: React.ElementType
  defaultOpen?: boolean
  fluid?: boolean
  input?: SemanticShorthandItem<InputProps>
  loading?: boolean
  onBlur?: (event: React.FocusEvent<HTMLElement>, data: SearchProps) => void
  onResultSelect?: (event: React.MouseEvent<HTMLDivElement>, data: SearchResultData) => void
  onSearchChange?: (event: React.MouseEvent<HTMLElement>, data: SearchProps) => void
  open?: boolean
  results?: SearchResultProps[]
  showNoResults?: boolean
  value?: string
}

export interface SearchResultData extends SearchProps {
  result: SearchResultProps
}

interface SearchState {
  /** Whether the input is focused. */
  focus: boolean

  /** Whether the results menu is open, auto controlled by `open`. */
  open: boolean

  /** Read by `render()`, never set by this component. */
  searchClasses?: string

  /** Index of the result selected with the keyboard. */
  selectedIndex: number

  /** Current query, auto controlled by `value`. */
  value: string

  /** Previous query value, used to reset the selection. */
  prevValue?: string
}

/**
 * Props partitioned for the rendered `<input />`. Only native input attributes
 * (see `htmlInputAttrs`) are partitioned there, while `onBlur` and `results`
 * are module props that can not be forwarded to `Input`.
 */
type SearchInputRestProps = Partial<Omit<SearchProps, 'onBlur' | 'results'>>

/**
 * The native `<input />` shorthand is a private `Input` prop used to apply the
 * prompt class, it is intentionally absent from the public `InputProps`.
 */
type SearchInputShorthandProps = Partial<InputProps> & {
  input?: SemanticShorthandItem<React.InputHTMLAttributes<HTMLInputElement>>
}

const overrideSearchInputProps = (
  predefinedProps: SearchInputShorthandProps,
): SearchInputShorthandProps => {
  const { input } = predefinedProps

  if (_isUndefined(input)) {
    return { ...predefinedProps, input: { className: 'prompt' } }
  }

  if (_isPlainObject(input)) {
    // `isPlainObject()` only narrows to `object`, while the props object form is
    // the only shorthand form that can carry a `className`.
    const inputProps = input as Partial<React.InputHTMLAttributes<HTMLInputElement>>

    return {
      ...predefinedProps,
      input: { ...inputProps, className: cx(inputProps.className, 'prompt') },
    }
  }

  return predefinedProps
}

/**
 * Handlers registered in the `eventStack`. As `eventStack` stores handlers by
 * reference, they are dispatched through a stable identifier (see `handlersRef`).
 */
interface SearchHandlers {
  closeOnDocumentClick: () => void
  closeOnEscape: (e: KeyboardEvent) => void
  handleDocumentMouseUp: () => void
  moveSelectionOnKeyDown: (e: KeyboardEvent) => void
  selectItemOnEnter: (e: KeyboardEvent) => void
}

const getAutoControlledStateFromProps = (
  _props: SearchProps,
  state: SearchState,
): Partial<SearchState> | null => {
  // We need to store a `prevValue` to compare as in `getDerivedStateFromProps` we don't have
  // prevState
  if (typeof state.prevValue !== 'undefined' && shallowEqual(state.prevValue, state.value)) {
    return { prevValue: state.value }
  }

  const selectedIndex = -1

  return { prevValue: state.value, selectedIndex }
}

/**
 * A search module allows a user to query for results from a selection of data
 */
function Search(props: SearchProps): React.ReactElement {
  const isMouseDown = useRef(false)

  const [state, setState] = useAutoControlledState<SearchProps, SearchState>(props, {
    autoControlledProps: ['open', 'value'],
    getAutoControlledStateFromProps,
  })

  const { focus, open: isOpen, searchClasses, selectedIndex, value } = state

  // `eventStack` keeps handlers by reference, subscriptions therefore dispatch
  // through this ref to always call the latest render's handlers.
  const handlersRef = useRef<SearchHandlers | null>(null)

  const onDocumentClick = useCallback(
    (): void => handlersRef.current?.closeOnDocumentClick(),
    [],
  )
  const onDocumentEscape = useCallback(
    (e: KeyboardEvent): void => handlersRef.current?.closeOnEscape(e),
    [],
  )
  const onDocumentMouseUp = useCallback(
    (): void => handlersRef.current?.handleDocumentMouseUp(),
    [],
  )
  const onKeyDownMoveSelection = useCallback(
    (e: KeyboardEvent): void => handlersRef.current?.moveSelectionOnKeyDown(e),
    [],
  )
  const onKeyDownSelectItem = useCallback(
    (e: KeyboardEvent): void => handlersRef.current?.selectItemOnEnter(e),
    [],
  )

  const prevFocusRef = useRef(focus)
  const prevOpenRef = useRef(isOpen)

  // componentDidUpdate()
  useEffect(() => {
    const prevFocus = prevFocusRef.current
    const prevOpen = prevOpenRef.current

    prevFocusRef.current = focus
    prevOpenRef.current = isOpen

    // focused / blurred
    if (!prevFocus && focus) {
      if (!isMouseDown.current) {
        tryOpen()
      }

      if (isOpen) {
        eventStack.sub('keydown', [onKeyDownMoveSelection, onKeyDownSelectItem])
      }
    } else if (prevFocus && !focus) {
      if (!isMouseDown.current) {
        close()
      }

      eventStack.unsub('keydown', [onKeyDownMoveSelection, onKeyDownSelectItem])
    }

    // opened / closed
    if (!prevOpen && isOpen) {
      open()
      eventStack.sub('click', onDocumentClick)
      eventStack.sub('keydown', [onDocumentEscape, onKeyDownMoveSelection, onKeyDownSelectItem])
    } else if (prevOpen && !isOpen) {
      close()
      eventStack.unsub('click', onDocumentClick)
      eventStack.unsub('keydown', [onDocumentEscape, onKeyDownMoveSelection, onKeyDownSelectItem])
    }
  }, [focus, isOpen]) // eslint-disable-line react-hooks/exhaustive-deps

  // componentWillUnmount()
  useEffect(() => {
    return (): void => {
      eventStack.unsub('click', onDocumentClick)
      eventStack.unsub('keydown', [onDocumentEscape, onKeyDownMoveSelection, onKeyDownSelectItem])
    }
  }, [onDocumentClick, onDocumentEscape, onKeyDownMoveSelection, onKeyDownSelectItem])

  const handleResultSelect = (
    e: React.MouseEvent<HTMLDivElement> | KeyboardEvent,
    result: SearchResultProps,
  ): void => {
    _invoke(props, 'onResultSelect', e, { ...props, result })
  }

  const closeOnEscape = (e: KeyboardEvent): void => {
    if (keyboardKey.getCode(e) !== keyboardKey.Escape) return

    e.preventDefault()
    close()
  }

  const moveSelectionOnKeyDown = (e: KeyboardEvent): void => {
    switch (keyboardKey.getCode(e)) {
      case keyboardKey.ArrowDown:
        e.preventDefault()
        moveSelectionBy(1)
        break

      case keyboardKey.ArrowUp:
        e.preventDefault()
        moveSelectionBy(-1)
        break

      default:
        break
    }
  }

  const selectItemOnEnter = (e: KeyboardEvent): void => {
    if (keyboardKey.getCode(e) !== keyboardKey.Enter) return

    const result = getSelectedResult()
    // prevent selecting null if there was no selected item value
    if (!result) return

    e.preventDefault()

    // notify the onResultSelect prop that the user is trying to change value
    setValue(result.title)
    handleResultSelect(e, result)
    close()
  }

  const closeOnDocumentClick = (): void => {
    close()
  }

  const handleMouseDown = (): void => {
    isMouseDown.current = true

    eventStack.sub('mouseup', onDocumentMouseUp)
  }

  const handleDocumentMouseUp = (): void => {
    isMouseDown.current = false

    eventStack.unsub('mouseup', onDocumentMouseUp)
  }

  handlersRef.current = {
    closeOnDocumentClick,
    closeOnEscape,
    handleDocumentMouseUp,
    moveSelectionOnKeyDown,
    selectItemOnEnter,
  }

  const handleInputClick = (e: React.MouseEvent<HTMLElement>): void => {
    // prevent closeOnDocumentClick()
    e.nativeEvent.stopImmediatePropagation()

    tryOpen()
  }

  const handleItemClick = (
    e: React.MouseEvent<HTMLDivElement>,
    { id }: SearchResultProps,
  ): void => {
    const result = getSelectedResult(id)
    // prevent closeOnDocumentClick()
    e.nativeEvent.stopImmediatePropagation()

    // notify the onResultSelect prop that the user is trying to change value
    setValue(result.title)
    handleResultSelect(e, result)
    close()
  }

  const handleItemMouseDown = (e: React.MouseEvent<HTMLDivElement>): void => {
    // Heads up! We should prevent default to prevent blur events.
    // https://github.com/Semantic-Org/Semantic-UI-React/issues/3298
    e.preventDefault()
  }

  const handleFocus = (): void => {
    setState({ focus: true })
  }

  const handleBlur = (e: React.FocusEvent<HTMLElement>): void => {
    _invoke(props, 'onBlur', e, props)

    setState({ focus: false })
  }

  const handleSearchChange = (e: React.ChangeEvent<HTMLInputElement>): void => {
    // prevent propagating to this.props.onChange()
    e.stopPropagation()

    const newQuery = e.target.value

    _invoke(props, 'onSearchChange', e, { ...props, value: newQuery })

    // open search dropdown on search query
    if (newQuery.length < 1) {
      close()
    } else if (!isOpen) {
      tryOpen(newQuery)
    }

    setValue(newQuery)
  }

  const getFlattenedResults = (): SearchResultProps[] => props.results

  const getSelectedResult = (index: number | string = selectedIndex): SearchResultProps => {
    const results = getFlattenedResults()

    return _get(results, index)
  }

  const setValue = (value: string): void => {
    setState({ value, selectedIndex: -1 })
  }

  const moveSelectionBy = (offset: number): void => {
    const results = getFlattenedResults()
    const lastIndex = results.length - 1

    // next is after last, wrap to beginning
    // next is before first, wrap to end
    let nextIndex = selectedIndex + offset

    if (nextIndex > lastIndex) nextIndex = 0
    else if (nextIndex < 0) nextIndex = lastIndex

    setState({ selectedIndex: nextIndex })
    scrollSelectedItemIntoView()
  }

  const scrollSelectedItemIntoView = (): void => {
    // Do not access document when server side rendering
    if (!isBrowser()) return

    const menu = document.querySelector<HTMLElement>('.ui.search.active.visible .results.visible')
    if (!menu) return

    const item = menu.querySelector<HTMLElement>('.result.active')
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

  const tryOpen = (currentValue: string = value): void => {
    if (currentValue.length < 1) return

    open()
  }

  const open = (): void => {
    setState({ open: true })
  }

  const close = (): void => {
    setState({ open: false })
  }

  const renderSearchInput = (rest: SearchInputRestProps): React.ReactNode => {
    const { input } = props

    return Input.create(input, {
      autoGenerateKey: false,
      defaultProps: {
        ...rest,
        autoComplete: 'off',
        icon: 'search',
        onChange: handleSearchChange,
        onClick: handleInputClick,
        tabIndex: '0',
        value,
      },
      // Nested shorthand props need special treatment to survive the shallow merge
      overrideProps: overrideSearchInputProps,
    })
  }

  const renderNoResults = (): React.ReactElement =>
    React.createElement(
      'div',
      { className: 'message empty' },
      React.createElement('div', { className: 'header' }, 'No results found.'),
    )

  const renderResult = (result: SearchResultProps, index: number): React.ReactElement => {
    return React.createElement(SearchResult, {
      // React stringifies element keys, while `SearchResultProps['key']` is a string
      key: String(result.id || result.title),
      active: selectedIndex === index,
      onClick: handleItemClick,
      onMouseDown: handleItemMouseDown,
      ...result,
      id: index,
    })
  }

  const renderResults = (): React.ReactNode => {
    const { results } = props

    return _map(results, renderResult)
  }

  const renderMenuContent = (): React.ReactNode => {
    const { showNoResults, results } = props

    if (_isEmpty(results)) {
      return showNoResults ? renderNoResults() : null
    }

    return renderResults()
  }

  const renderResultsMenu = (): React.ReactElement | undefined => {
    const resultsClasses = isOpen ? 'visible' : ''
    const menuContent = renderMenuContent()

    if (!menuContent) return

    return React.createElement(SearchResults, { className: resultsClasses }, menuContent)
  }

  const { className, fluid, loading } = props

  const classes = cx(
    'ui',
    isOpen && 'active visible',
    searchClasses,
    useKeyOnly(focus, 'focus'),
    useKeyOnly(fluid, 'fluid'),
    useKeyOnly(loading, 'loading'),
    'search',
    className,
  )
  const unhandled = getUnhandledProps(Search, props)
  const ElementType = getElementType(Search, props)

  const [htmlInputProps, rest] = partitionHTMLProps(unhandled, {
    htmlProps: htmlInputAttrs,
  })

  return React.createElement(
    ElementType,
    {
      ...rest,
      className: classes,
      onBlur: handleBlur,
      onFocus: handleFocus,
      onMouseDown: handleMouseDown,
    },
    renderSearchInput(htmlInputProps),
    renderResultsMenu(),
  )
}

Search.handledProps = [
  'as',
  'className',
  'defaultOpen',
  'fluid',
  'input',
  'loading',
  'onBlur',
  'onResultSelect',
  'onSearchChange',
  'open',
  'results',
  'showNoResults',
  'value',
]



Search.defaultProps = { input: 'text', showNoResults: true } satisfies Partial<SearchProps>

export default Search
