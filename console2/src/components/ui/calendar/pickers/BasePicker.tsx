import keyboardKey from 'keyboard-key'
import _filter from 'lodash-es/filter'
import _includes from 'lodash-es/includes'
import _isNil from 'lodash-es/isNil'
import _isNumber from 'lodash-es/isNumber'
import _range from 'lodash-es/range'
import { Moment } from 'moment'
import React, { useCallback, useEffect, useRef, useState } from 'react'

interface HandleChangeParams {
  value?: string
  itemPosition?: number
}

export interface BasePickerOnChangeData {
  value: {
    /** Year. */
    year?: number
    /** Month (0 - 11). */
    month?: number
    /** Date (1 - 31). */
    date?: number
    /** Hour (0 - 23). */
    hour?: number
    /** Minute (0 - 59). */
    minute?: number
  }
}

export interface BasePickerProps {
  /** Called after day is selected. */
  onChange: (e: React.SyntheticEvent<HTMLElement>, data: BasePickerOnChangeData) => void

  /** Currently selected date. */
  value?: Moment

  /** A value for initializing day picker's state. */
  initializeWith: Moment

  /** Forse popup to close. */
  closePopup: () => void

  /** WHether picker in focus. */
  isPickerInFocus: () => boolean

  /** Whether popup-trigger in focus. */
  isTriggerInFocus: () => boolean

  /** Used to pass underlying picker's html element to parent component. */
  onCalendarViewMount: (e: HTMLElement) => void

  /** Called on calendar's header click. */
  onHeaderClick: () => void

  /** Moment date localization */
  localization?: string

  tabIndex?: number | string
}

export interface OptionalHeaderProps {
  /** Whether to display calendar's header. */
  hasHeader: boolean
}

export interface BasePickerState {
  /** Position of a cell that is currently hovered on. */
  hoveredCellPosition: number | undefined

  /** Inner picker's currently selected date. */
  date: Moment
}

/** A value (date, year or anything like that) that is displayed in a calendar header. */
export type HeadingValue = string

export interface ProvideHeadingValue {
  /** Return currently selected value to display in calendar header. */
  getCurrentDate(): HeadingValue
}

/** Values the concrete pickers share for building their cells. */
export interface PickerBehavior {
  /** Number of values that fill a calendar page. */
  cellCount: number

  /** Number of cells in a row (the `PAGE_WIDTH` of the pickers). */
  pageWidth: number

  /** Builds the values that fill a calendar page. */
  buildCalendarValues: (date: Moment, props: BasePickerProps) => string[]

  /** Positions of the cells that are displayed as disabled. */
  getDisabledPositions: (date: Moment) => number[]

  /** Position of a value with which a calendar was initialized. */
  getInitialDatePosition: (date: Moment, values: string[], selectable: number[]) => number

  /** Position of the cell that is displayed as active. */
  getActiveCellPosition: (date: Moment, props: BasePickerProps) => number | undefined

  /** Value payload of a selection, the surrounding `onChange` data is added by the hook. */
  getChangeValue: (
    date: Moment,
    value: string,
    values: string[],
  ) => BasePickerOnChangeData['value']

  /** Date to display after switching a page. */
  switchPage: (date: Moment, direction: 'next' | 'prev') => Moment

  /** Currently selected value to display in a calendar header, see `ProvideHeadingValue`. */
  getHeadingValue?: (date: Moment, props: BasePickerProps) => HeadingValue
}

/** State and handlers the shared picker behavior provides to a concrete picker. */
export interface PickerController {
  /** Hovered cell position and the date a picker displays. */
  state: BasePickerState

  /** Values that fill the current page. */
  buildCalendarValues: () => string[]

  /** Positions of the disabled cells of the current page. */
  getDisabledPositions: () => number[]

  /** Position of the cell that is displayed as active. */
  getActiveCellPosition: () => number | undefined

  /** Value that is displayed in a calendar header. */
  getHeadingValue: () => HeadingValue

  /** Whether a previous page can be displayed. */
  isPrevPageAvailable: () => boolean

  /** Whether a next page can be displayed. */
  isNextPageAvailable: () => boolean

  /** Changes the displayed page to the previous one. */
  switchToPrevPage: (
    e?: React.SyntheticEvent<HTMLElement>,
    data?: unknown,
    callback?: () => void,
  ) => void

  /** Changes the displayed page to the next one. */
  switchToNextPage: (
    e?: React.SyntheticEvent<HTMLElement>,
    data?: unknown,
    callback?: () => void,
  ) => void

  /** Stores the position of a hovered cell. */
  onHoveredCellPositionChange: (
    e: React.SyntheticEvent<HTMLElement, Event> | null,
    data: { itemPosition: number },
  ) => void

  /** Handles a selection and forwards it through the picker's `onChange`. */
  handleChange: (
    e: React.SyntheticEvent<HTMLElement> | null,
    data: HandleChangeParams & { value: string },
  ) => void

  /** Handles a calendar blur by closing the popup. */
  handleBlur: () => void
}

/** Shared keyboard and selection behavior for the concrete picker components. */
export function usePicker(props: BasePickerProps, behavior: PickerBehavior): PickerController {
  const [state, setState] = useState<BasePickerState>(() => ({
    hoveredCellPosition: undefined,
    date: props.initializeWith.clone(),
  }))

  const propsRef = useRef(props)
  propsRef.current = props

  const behaviorRef = useRef(behavior)
  behaviorRef.current = behavior

  const stateRef = useRef(state)
  stateRef.current = state

  const getValues = useCallback(
    (date: Moment): string[] => behaviorRef.current.buildCalendarValues(date, propsRef.current),
    [],
  )

  const getDisabledPositions = useCallback(
    (date: Moment): number[] => behaviorRef.current.getDisabledPositions(date),
    [],
  )

  const getSelectableCellPositions = useCallback(
    (date: Moment): number[] =>
      _filter(
        _range(0, behaviorRef.current.cellCount),
        (position) => !_includes(behaviorRef.current.getDisabledPositions(date), position),
      ),
    [],
  )

  const getActiveCellPosition = useCallback(
    (date: Moment): number | undefined =>
      behaviorRef.current.getActiveCellPosition(date, propsRef.current),
    [],
  )

  const getInitialDatePosition = useCallback(
    (date: Moment): number =>
      behaviorRef.current.getInitialDatePosition(
        date,
        getValues(date),
        getSelectableCellPositions(date),
      ),
    [getSelectableCellPositions, getValues],
  )

  const setHoveredCellPosition = useCallback((itemPosition: number): void => {
    setState((prevState) => ({ ...prevState, hoveredCellPosition: itemPosition }))
  }, [])

  const onHoveredCellPositionChange = useCallback(
    (
      _e: React.SyntheticEvent<HTMLElement, Event> | null,
      { itemPosition }: { itemPosition: number },
    ): void => {
      setHoveredCellPosition(itemPosition)
    },
    [setHoveredCellPosition],
  )

  const canCalendarCatchKeyboardEvents = useCallback(
    (): boolean => propsRef.current.isTriggerInFocus(),
    [],
  )

  /** Called when the picker loses focus. */
  const handleBlur = useCallback((): void => {
    propsRef.current.closePopup()
  }, [])

  const handleChange = useCallback(
    (
      e: React.SyntheticEvent<HTMLElement> | null,
      { value }: HandleChangeParams & { value: string },
    ): void => {
      const { date } = stateRef.current

      propsRef.current.onChange(e, {
        ...propsRef.current,
        value: behaviorRef.current.getChangeValue(date, value, getValues(date)),
      })
    },
    [getValues],
  )

  /*
    Emulates the `setState(partialState, callback)` form of the legacy class:
    the callback is invoked after the state update it was scheduled with has
    been committed.
  */
  const pendingCallback = useRef<(() => void) | null>(null)

  useEffect(() => {
    const callback = pendingCallback.current

    if (callback) {
      pendingCallback.current = null
      callback()
    }
  })

  const switchPage = useCallback((direction: 'next' | 'prev', callback?: () => void): void => {
    setState((prevState) => ({
      ...prevState,
      date: behaviorRef.current.switchPage(prevState.date, direction),
    }))

    pendingCallback.current = callback ?? null
  }, [])

  const switchToPrevPage = useCallback(
    (_e?: React.SyntheticEvent<HTMLElement>, _data?: unknown, callback?: () => void): void => {
      switchPage('prev', callback)
    },
    [switchPage],
  )

  const switchToNextPage = useCallback(
    (_e?: React.SyntheticEvent<HTMLElement>, _data?: unknown, callback?: () => void): void => {
      switchPage('next', callback)
    },
    [switchPage],
  )

  const isPrevPageAvailable = useCallback((): boolean => true, [])

  const isNextPageAvailable = useCallback((): boolean => true, [])

  const handleEnterKeyPress = useCallback(
    (event: KeyboardEvent): void => {
      const key = keyboardKey.getKey(event)

      if (key === 'Enter' && canCalendarCatchKeyboardEvents()) {
        event.preventDefault()

        const { date, hoveredCellPosition } = stateRef.current

        handleChange(null, {
          value: getValues(date)[hoveredCellPosition],
          itemPosition: hoveredCellPosition,
        })
      }
    },
    [canCalendarCatchKeyboardEvents, getValues, handleChange],
  )

  const handleArrowKeyPress = useCallback(
    (event: KeyboardEvent): void => {
      if (!canCalendarCatchKeyboardEvents()) {
        return
      }

      const key = keyboardKey.getKey(event)
      const { date, hoveredCellPosition } = stateRef.current
      const selectableCells = getSelectableCellPositions(date)
      const nextSelectableCellPositionLeft = selectableCells
        .slice(0, selectableCells.indexOf(hoveredCellPosition))
        .pop()
      const nextSelectableCellPositionRight = selectableCells.slice(
        selectableCells.indexOf(hoveredCellPosition) + 1,
      )[0]

      switch (key) {
        case 'Left':
        case 'ArrowLeft':
          if (!_isNil(nextSelectableCellPositionLeft)) {
            onHoveredCellPositionChange(null, { itemPosition: nextSelectableCellPositionLeft })
          } else {
            if (isPrevPageAvailable()) {
              switchToPrevPage(null, null, () => {
                const selectableCellsPrevPage = getSelectableCellPositions(stateRef.current.date)

                onHoveredCellPositionChange(null, {
                  itemPosition: selectableCellsPrevPage[selectableCellsPrevPage.length - 1],
                })
              })
            }
          }
          break

        case 'Right':
        case 'ArrowRight':
          if (!_isNil(nextSelectableCellPositionRight)) {
            onHoveredCellPositionChange(null, { itemPosition: nextSelectableCellPositionRight })
          } else {
            if (isNextPageAvailable()) {
              switchToNextPage(null, null, () => {
                const selectableCellsNextPage = getSelectableCellPositions(stateRef.current.date)

                onHoveredCellPositionChange(null, {
                  itemPosition: selectableCellsNextPage[0],
                })
              })
            }
          }
          break

        case 'Up':
        case 'ArrowUp':
          event.preventDefault()

          if (_includes(selectableCells, hoveredCellPosition - behaviorRef.current.pageWidth)) {
            onHoveredCellPositionChange(null, {
              itemPosition: hoveredCellPosition - behaviorRef.current.pageWidth,
            })
          }
          break

        case 'Down':
        case 'ArrowDown':
          event.preventDefault()

          if (_includes(selectableCells, hoveredCellPosition + behaviorRef.current.pageWidth)) {
            onHoveredCellPositionChange(null, {
              itemPosition: hoveredCellPosition + behaviorRef.current.pageWidth,
            })
          }
          break

        default:
          break
      }
    },
    [
      canCalendarCatchKeyboardEvents,
      getSelectableCellPositions,
      isNextPageAvailable,
      isPrevPageAvailable,
      onHoveredCellPositionChange,
      switchToNextPage,
      switchToPrevPage,
    ],
  )

  const handleKeyPress = useCallback(
    (event: KeyboardEvent): void => {
      if (!canCalendarCatchKeyboardEvents()) {
        return
      }

      const key = keyboardKey.getKey(event)

      switch (key) {
        case 'Enter':
          handleEnterKeyPress(event)
          break

        case 'Esc':
        case 'Escape':
          propsRef.current.closePopup()
          break

        default:
          handleArrowKeyPress(event)
      }
    },
    [canCalendarCatchKeyboardEvents, handleArrowKeyPress, handleEnterKeyPress],
  )

  useEffect(() => {
    document.addEventListener('keydown', handleKeyPress)

    return () => document.removeEventListener('keydown', handleKeyPress)
  }, [handleKeyPress])

  // `componentDidMount()` of the legacy `SingleSelectionPicker`.
  useEffect(() => {
    const { date } = stateRef.current
    const active = getActiveCellPosition(date)

    setHoveredCellPosition(_isNumber(active) ? active : getInitialDatePosition(date))
  }, [getActiveCellPosition, getInitialDatePosition, setHoveredCellPosition])

  const getHeadingValue = useCallback((date: Moment): HeadingValue => {
    const { getHeadingValue: getBehaviorHeadingValue } = behaviorRef.current

    return getBehaviorHeadingValue ? getBehaviorHeadingValue(date, propsRef.current) : ''
  }, [])

  return {
    state,
    buildCalendarValues: () => getValues(state.date),
    getDisabledPositions: () => getDisabledPositions(state.date),
    getActiveCellPosition: () => getActiveCellPosition(state.date),
    getHeadingValue: () => getHeadingValue(state.date),
    isPrevPageAvailable,
    isNextPageAvailable,
    switchToPrevPage,
    switchToNextPage,
    onHoveredCellPositionChange,
    handleChange,
    handleBlur,
  }
}
